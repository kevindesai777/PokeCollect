#!/usr/bin/env python3
"""Measures every catch effect on an Android phone over adb.

For each type it opens the benchmark build, searches the collection for that type's first card
(lowest dex number), ticks it and reads the frames back from `dumpsys gfxinfo`. It unticks the card
and repeats. Play 1 is kept apart: one-off costs land there (GPU pipelines built, code run for the
first time).

Setup, once per install (see docs/research/effects/notes.md, phase 6):
    ./gradlew :androidApp:assembleBenchmark
    adb install -r androidApp/build/outputs/apk/benchmark/androidApp-benchmark.apk
    scripts/measure_effects.py --serial S --compile speed-profile   # what a Play install starts from

Runs:
    scripts/measure_effects.py --serial S --fresh ever    # clears app data (and its shader cache) per type
    scripts/measure_effects.py --serial S --fresh launch  # new process per type, caches kept
    scripts/measure_effects.py --serial S --report OUT    # the table again, from saved files

Never screen-record while this runs: the recording's encoder costs frames.
"""
import argparse
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

PKG = "com.pixel.pokecollect.benchmark"
ACTIVITY = f"{PKG}/com.pixel.pokecollect.MainActivity"
ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "shared/src/commonMain/composeResources/files/pokemon.json"
TYPES = ("normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost "
         "dragon dark steel fairy").split()
EFFECT_WAIT_S = 2.2  # the longest effect ends by ~1.5 s; the rest is margin


def effect_type(types):
    """Mirrors effectTypeFor: Normal with a second type plays the second type's effect."""
    return types[1] if types[0] == "normal" and len(types) > 1 else types[0]


def cards():
    data = json.loads(DATASET.read_text())
    best = {}
    for p in data["pokemon"]:
        t = effect_type(p["types"])
        if t not in best or p["id"] < best[t]["id"]:
            best[t] = p
    return {t: best[t]["name"] for t in TYPES}


class Phone:
    def __init__(self, serial):
        self.serial = serial

    def sh(self, cmd, check=True):
        out = subprocess.run(["adb", "-s", self.serial, "shell", cmd], capture_output=True, text=True)
        if check and out.returncode != 0:
            raise RuntimeError(f"{cmd}: {out.stderr.strip()}")
        return out.stdout

    def nodes(self):
        self.sh("uiautomator dump /data/local/tmp/ui.xml >/dev/null")
        xml = self.sh("cat /data/local/tmp/ui.xml")
        found = []
        for m in re.finditer(r"<node [^>]*>", xml):
            n = m.group(0)
            b = [int(v) for v in re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1))]
            found.append({
                "text": re.search(r' text="([^"]*)"', n).group(1),
                "desc": re.search(r'content-desc="([^"]*)"', n).group(1),
                "cls": re.search(r'class="([^"]*)"', n).group(1),
                "click": 'clickable="true"' in n,
                "b": b,
            })
        return found

    def tap(self, x, y):
        self.sh(f"input tap {x} {y}")

    def ime_shown(self):
        return "mInputShown=true" in self.sh("dumpsys input_method")

    def state(self):
        battery = self.sh("dumpsys battery")
        temp = int(re.search(r"temperature: (\d+)", battery).group(1)) / 10
        thermal = re.search(r"Thermal Status: (\d+)", self.sh("dumpsys thermalservice", check=False) or "")
        return {"battery_c": temp, "thermal_status": int(thermal.group(1)) if thermal else None}


def center(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def inside(inner, outer):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and inner[2] <= outer[2] and inner[3] <= outer[3]


def open_list(phone, fresh):
    if fresh == "ever":
        phone.sh(f"pm clear {PKG}")
    phone.sh(f"am force-stop {PKG}")
    phone.sh("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard", check=False)
    phone.sh(f"am start -W -n {ACTIVITY}")
    for _ in range(20):
        time.sleep(0.5)
        nodes = phone.nodes()
        add = next((n for n in nodes if n["text"] == "Add a card by name"), None)
        if add:  # an empty collection opens on its welcome card
            phone.tap(*center(add["b"]))
            continue
        field = next((n for n in nodes if n["cls"].endswith("EditText")), None)
        if field:
            return field
    raise RuntimeError("the collection list never showed")


def find_card(phone, field, name):
    """Searches for the card and returns where to tap it and its row's bounds."""
    phone.tap(*center(field["b"]))
    phone.sh("input keyevent " + " ".join(["KEYCODE_DEL"] * 15))
    phone.sh(f"input text {name.replace(' ', '%s')}")
    # The keyboard's Search key clears the field's focus; a focused field's blinking cursor would
    # draw frames of its own, and the first tap on the card would only take the focus away.
    phone.sh("input keyevent KEYCODE_ENTER")
    time.sleep(0.8)
    if phone.ime_shown():
        phone.sh("input keyevent KEYCODE_BACK")
        time.sleep(0.5)
    nodes = phone.nodes()
    # The search field holds the name too; the card's label is the TextView.
    label = next(n for n in nodes if n["text"] == name and n["cls"].endswith("TextView"))
    row = min((n for n in nodes if n["click"] and inside(label["b"], n["b"])),
              key=lambda n: (n["b"][2] - n["b"][0]) * (n["b"][3] - n["b"][1]))
    return center(label["b"]), row["b"]


def is_caught(phone, row):
    return any(n["desc"] == "Caught" and inside(n["b"], row) for n in phone.nodes())


def measure(args):
    phone = Phone(args.serial)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    names = cards()
    types = args.types.split(",") if args.types else TYPES
    meta = {"fresh": args.fresh, "plays": args.plays, "started": time.strftime("%Y-%m-%d %H:%M:%S"),
            "model": phone.sh("getprop ro.product.model").strip(),
            "android": phone.sh("getprop ro.build.version.release").strip(),
            "compile": re.findall(r"status=([\w-]+)", phone.sh(f"dumpsys package dexopt | grep -A3 '\\[{PKG}\\]'"))[:1],
            "animator_scale": phone.sh("settings get global animator_duration_scale").strip(),
            "states": {}}
    for t in types:
        name = names[t]
        field = open_list(phone, args.fresh)
        spot, row = find_card(phone, field, name)
        if is_caught(phone, row):
            phone.tap(*spot)
            time.sleep(1.0)
        meta["states"][t] = phone.state()
        play, attempts = 1, 0
        while play <= args.plays and attempts < args.plays * 2:
            attempts += 1
            phone.sh(f"dumpsys gfxinfo {PKG} reset >/dev/null")
            phone.tap(*spot)
            time.sleep(EFFECT_WAIT_S)
            gfx = phone.sh(f"dumpsys gfxinfo {PKG}")
            stats_text = phone.sh(f"dumpsys gfxinfo {PKG} framestats")
            # Only a tap that ticked the card played its effect. A lost tap, or one that unticked a
            # card left caught, is thrown away; the card is unticked either way afterwards.
            if is_caught(phone, row):
                (out / f"{t}.{play}.gfx.txt").write_text(gfx)
                (out / f"{t}.{play}.frames.txt").write_text(stats_text)
                play += 1
                phone.tap(*spot)  # untick, unmeasured
            else:
                print(f"  {t}: a tap didn't tick the card; measuring again", flush=True)
            time.sleep(1.0)
        print(f"{t:9s} {name}: {args.plays} plays, {meta['states'][t]}", flush=True)
    (out / "meta.json").write_text(json.dumps(meta, indent=2))
    report(out)


# --- reading the files back ---------------------------------------------------------------------

def frames(path):
    """framestats rows as dicts of ms from IntendedVsync, for frames this run drew."""
    lines = path.read_text().splitlines()
    hdr = next(line for line in lines if line.startswith("Flags,")).split(",")
    rows = []
    for line in lines:
        if re.match(r"^\d+,\d+", line):
            v = dict(zip(hdr, map(int, line.split(",")[:-1])))
            if v["FrameCompleted"] > 0:  # a frame that was skipped has no timings
                rows.append(v)
    return rows


def summary(path):
    text = path.read_text()
    get = lambda key: int(re.search(rf"^{re.escape(key)}: (\d+)", text, re.M).group(1))
    histogram = lambda key: {int(ms): int(n) for ms, n in re.findall(r"(\d+)ms=(\d+)", re.search(rf"^{key}: (.*)$", text, re.M).group(1))}
    return {"total": get("Total frames rendered"), "janky": get("Janky frames"), "deadline_missed": get("Number Frame deadline missed"),
            "hist": histogram("HISTOGRAM"), "gpu_hist": histogram("GPU HISTOGRAM")}


def hist_pct(hists, p):
    """Percentile over gfxinfo histograms, which count every frame (framestats keeps only 120)."""
    total = {}
    for h in hists:
        for ms, n in h.items():
            total[ms] = total.get(ms, 0) + n
    count = sum(total.values())
    if not count:
        return float("nan")
    seen = 0
    for ms in sorted(total):
        seen += total[ms]
        if seen * 100 >= count * p:
            return ms


def stage_ms(r):
    span = lambda a, b: (r[b] - r[a]) / 1e6
    return {
        "total": span("IntendedVsync", "FrameCompleted"),
        "ui": span("IntendedVsync", "SyncQueued"),
        "rt": span("SyncStart", "FrameCompleted"),
        "gpu": span("IssueDrawCommandsStart", "GpuCompleted") if r["GpuCompleted"] > 0 else None,
    }


def pct(values, p):
    v = sorted(x for x in values if x is not None)
    if not v:
        return float("nan")
    return v[max(1, -(-len(v) * p // 100)) - 1]


def stats(plays):
    rows = [stage_ms(r) for play in plays for r in play["rows"]]
    deltas = []  # vsync to vsync within a play: the refresh period the effect actually got
    for play in plays:
        vsyncs = [r["IntendedVsync"] for r in play["rows"]]
        deltas += [(b - a) / 1e6 for a, b in zip(vsyncs, vsyncs[1:])]
    return {
        "frames": sum(p["sum"]["total"] for p in plays),
        "rows": len(rows),
        "janky": sum(p["sum"]["janky"] for p in plays),
        "p50": pct([r["total"] for r in rows], 50), "p90": pct([r["total"] for r in rows], 90),
        "max": max((r["total"] for r in rows), default=float("nan")),
        "ui_max": max((r["ui"] for r in rows), default=float("nan")),
        "rt_max": max((r["rt"] for r in rows), default=float("nan")),
        "gpu_p50": pct([r["gpu"] for r in rows], 50), "gpu_p90": pct([r["gpu"] for r in rows], 90),
        "ui_p50": pct([r["ui"] for r in rows], 50), "rt_p50": pct([r["rt"] for r in rows], 50),
        "hist_p50": hist_pct([p["sum"]["hist"] for p in plays], 50), "hist_p90": hist_pct([p["sum"]["hist"] for p in plays], 90),
        "hist_p99": hist_pct([p["sum"]["hist"] for p in plays], 99),
        "gpu_hist_p90": hist_pct([p["sum"]["gpu_hist"] for p in plays], 90),
        "period": statistics.median(deltas) if deltas else float("nan"),
    }


def report(out):
    out = Path(out)
    meta = json.loads((out / "meta.json").read_text())
    print(f"\n{meta['model']} · Android {meta['android']} · compile {meta['compile']} · fresh {meta['fresh']} · {meta['started']}")
    print("ms: frame time = vsync to frame handed over; janky = SurfaceFlinger's count (missed its deadline)")
    print(f"{'type':9s} | {'play 1: frames janky  max  ui-max rt-max':40s} | plays 2+: frames janky  p50  p90  max  gpu-p50 gpu-p90 | period")
    result = {}
    for t in TYPES:
        files = sorted(out.glob(f"{t}.*.gfx.txt"), key=lambda p: int(p.name.split(".")[1]))
        if not files:
            continue
        plays = [{"sum": summary(f), "rows": frames(f.with_name(f.name.replace(".gfx.", ".frames.")))} for f in files]
        first, rest = stats(plays[:1]), stats(plays[1:]) if len(plays) > 1 else None
        result[t] = {"first": first, "rest": rest}
        line = f"{t:9s} | {first['frames']:6d} {first['janky']:5d} {first['max']:6.1f} {first['ui_max']:6.1f} {first['rt_max']:6.1f}           |"
        if rest:
            line += (f" {rest['frames']:6d} {rest['janky']:5d} {rest['p50']:5.1f} {rest['p90']:5.1f} {rest['max']:5.1f}"
                     f" {rest['gpu_p50']:7.1f} {rest['gpu_p90']:7.1f} | {rest['period']:.1f}")
        print(line)
    print("\nplays 2+, by stage (p50 ms): ui = vsync to sync queued, rt = RenderThread, gpu = draw issue to GPU done;"
          "\nthen gfxinfo's histograms over every frame (1 ms buckets; the first bucket is 'up to 5 ms')")
    for t, r in result.items():
        if r["rest"]:
            q = r["rest"]
            cut = " (framestats cut: percentiles above use its last 120 frames)" if q["rows"] < q["frames"] else ""
            print(f"{t:9s}  ui {q['ui_p50']:5.1f}   rt {q['rt_p50']:5.1f}   gpu {q['gpu_p50']:5.1f}"
                  f"   | all frames: p50 {q['hist_p50']}  p90 {q['hist_p90']}  p99 {q['hist_p99']}  gpu p90 {q['gpu_hist_p90']}{cut}")
    (out / "summary.json").write_text(json.dumps(result, indent=2))


def compile_app(serial, mode):
    phone = Phone(serial)
    phone.sh(f"cmd package compile --reset {PKG}")
    if mode == "speed-profile":
        # What Play does at install: apply the baseline profiles the libraries ship, then compile.
        phone.sh(f"am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE {PKG}/androidx.profileinstaller.ProfileInstallReceiver")
        time.sleep(2)
        phone.sh(f"am force-stop {PKG}")
    if mode != "verify":
        phone.sh(f"cmd package compile -f -m {mode} {PKG}")
    print(re.findall(r"status=[\w-]+", phone.sh(f"dumpsys package dexopt | grep -A3 '\\[{PKG}\\]'")))


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial")
    ap.add_argument("--fresh", choices=["ever", "launch"], default="launch")
    ap.add_argument("--plays", type=int, default=10)
    ap.add_argument("--types", help="comma-separated; default all eighteen")
    ap.add_argument("--out", default=str(ROOT / "build/effect-measurements" / time.strftime("%Y%m%d-%H%M%S")))
    ap.add_argument("--compile", choices=["speed-profile", "speed", "verify"], help="recompile the app, then stop")
    ap.add_argument("--report", metavar="DIR", help="print the table for a finished run")
    a = ap.parse_args()
    if a.report:
        report(a.report)
    elif a.compile:
        compile_app(a.serial, a.compile)
    else:
        if not a.serial:
            sys.exit("--serial is required")
        measure(a)
