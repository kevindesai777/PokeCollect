#!/usr/bin/env python3
"""Measures every catch effect on an iPhone: Instruments records while a UI test ticks the cards.

The iOS counterpart of scripts/measure_effects.py. For each type it installs the Release app afresh,
starts `xctrace record` (Hitches, Time Profiler and Points of Interest, all processes), and runs the
EffectsDriver UI test (scripts/EffectsDriver), which searches the type's first card, ticks it and
unticks it, several times, then relaunches the app and plays it once more. Each play is a Points of
Interest signpost from the test, so the trace can be cut per play. The trace is exported and deleted
(it is ~2 MB a second and takes about as long to save as to record).

What the report reads:
- frames: PokeCollect's commits (the Hitches instrument's "hitches-updates" rows), one per frame
  drawn. Instruments' own hitch list stays empty when a Compose app simply skips frames, so gaps
  between commits are the measure.
- CPU: Time Profiler's 1 ms samples of PokeCollect's threads, per frame drawn, and of Apple's
  MTLCompilerService, which compiles Metal shaders for the app in its own process.

Setup, once (Team ID on the command line, never committed):
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \\
        -destination 'id=UDID' -derivedDataPath build/ios-device TEAM_ID=T DEVELOPMENT_TEAM=T \\
        -allowProvisioningUpdates build
    TEAM_ID=T scripts/measure_effects_ios.py --udid UDID --build-driver
    The iPhone: unlocked, Auto-Lock off, Settings > Developer > Enable UI Automation on.

Runs:
    scripts/measure_effects_ios.py --udid UDID --out DIR     # all types: fresh install, 4 plays, relaunch
    scripts/measure_effects_ios.py --udid UDID --out DIR --types fire,ice --plays 6
    scripts/measure_effects_ios.py --report DIR              # the table again, from saved files
"""
import argparse
import json
import os
import re
import signal
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "shared/src/commonMain/composeResources/files/pokemon.json"
APP = ROOT / "build/ios-device/Build/Products/Release-iphoneos/PokeCollect.app"
DRIVER = ROOT / "scripts/EffectsDriver/EffectsDriver.xcodeproj"
DRIVER_BUILD = ROOT / "build/effects-driver"
BUNDLE = "com.pixel.pokecollect"
SUBSYSTEM = "com.pixel.pokecollect.effectsdriver"
TYPES = ("normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost "
         "dragon dark steel fairy").split()
VSYNC_MS = 1000 / 60  # the iPhone 16e's display
LATE_MS = VSYNC_MS * 1.5
EFFECTS = ROOT / "shared/src/commonMain/kotlin/com/pixel/pokecollect/ui/catcheffects"
# A play's first frame comes about 120 ms before its effect starts (the tap's own redraw, the write,
# the recomposition), and the app can redraw for other reasons after the effect: frames later than
# this past the first one, plus the effect's end, aren't the effect's.
LEAD_MS = 250

# One export for every table the report reads: each export loads the whole trace. Several tables
# share the os-signpost schema; the test's signposts are in the Points of Interest one.
TABLES = ('/trace-toc/run[@number="1"]/data/table[@schema="hitches-updates" or @schema="time-sample" or '
          '(@schema="os-signpost" and @category="PointsOfInterest" and not(@dynamic-tracing-enabled-subsystems))]')
# A combined export names the columns of its first table only, and table numbers differ between
# traces, so the other tables are recognised by their first row.
COLUMNS = {
    "hitches-updates": ["start", "duration", "process", "display", "swap-id", "surface-id", "frame-color",
                        "containment-level", "label"],
    "time-sample": ["time", "thread", "core-index", "thread-state", "cp-kernel-callstack", "cp-user-callstack",
                    "sample-type"],
    "os-signpost": ["time", "thread", "process", "event-type", "scope", "identifier", "name", "format-string",
                    "backtrace", "subsystem", "category", "message", "emit-location"],
}


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


def effect_end_ms():
    """{type: EffectTiming.endMs}, read from the effects' sources."""
    consts = {m.group(1): int(m.group(2)) for m in re.finditer(r"const val (\w+_MS) = (\d+)",
                                                                (EFFECTS / "CardMotion.kt").read_text())}
    out = {}
    for f in (EFFECTS / "effects").glob("*.kt"):
        m = re.search(r"EffectTiming\(durationMs = (\d+), peakMs = (\d+)(?:, fillMs = (\d+))?\)", f.read_text())
        if m:
            duration, peak = int(m.group(1)), int(m.group(2))
            fill = int(m.group(3)) if m.group(3) else peak
            out[f.stem.lower()] = max(duration, peak + consts["SETTLE_MS"], fill + consts["POP_MS"])
    return out


def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


# --- recording ----------------------------------------------------------------------------------

def build_driver(a):
    team = os.environ.get("TEAM_ID")
    if not team:
        sys.exit("TEAM_ID is not set")
    r = subprocess.run(["xcodebuild", "-project", str(DRIVER), "-scheme", "EffectsDriver", "-configuration",
                        "Release", "-destination", f"id={a.udid}", "-derivedDataPath", str(DRIVER_BUILD),
                        f"DEVELOPMENT_TEAM={team}", "-allowProvisioningUpdates", "build-for-testing"])
    if r.returncode != 0:
        sys.exit("the driver didn't build")


def record(a, out, card, xctestrun):
    out.mkdir(parents=True, exist_ok=True)
    trace = out / "run.trace"
    cmd = ["xcrun", "xctrace", "record", "--device", a.udid, "--all-processes", "--output", str(trace),
           "--no-prompt", "--instrument", "Hitches", "--instrument", "Time Profiler",
           "--instrument", "Points of Interest"]
    log_path = out / "xctrace.log"
    with open(log_path, "w") as rec_log:
        rec = subprocess.Popen(cmd, stdout=rec_log, stderr=subprocess.STDOUT, text=True)
        for _ in range(240):
            time.sleep(0.5)
            if "Ctrl-C to stop" in log_path.read_text():
                break
            if rec.poll() is not None:
                raise RuntimeError(f"xctrace exited:\n{log_path.read_text()}")
        time.sleep(1.5)
        env = dict(os.environ, TEST_RUNNER_EFFECT_CARDS=card, TEST_RUNNER_EFFECT_PLAYS=str(a.plays),
                   TEST_RUNNER_EFFECT_RELAUNCH="1", TEST_RUNNER_EFFECT_AFTER_RELAUNCH="0" if a.no_relaunch else "1")
        with open(out / "test.log", "w") as log:
            t = subprocess.run(["xcodebuild", "test-without-building", "-xctestrun", str(xctestrun),
                                "-destination", f"id={a.udid}",
                                "-only-testing:EffectsDriver/EffectsDriver/testPlay"],
                               env=env, stdout=log, stderr=subprocess.STDOUT)
        time.sleep(1)
        rec.send_signal(signal.SIGINT)
        rec.wait(timeout=900)
    if "disconnected" in log_path.read_text():
        print(f"  warning: {log_path.read_text().strip()}", flush=True)
    with open(out / "tables.xml", "w") as f:
        subprocess.run(["xcrun", "xctrace", "export", "--input", str(trace), "--xpath", TABLES],
                       stdout=f, stderr=subprocess.DEVNULL)
    if not a.keep_trace:
        subprocess.run(["rm", "-rf", str(trace)])
    return t.returncode


def measure(a):
    xctestrun = next(DRIVER_BUILD.glob("Build/Products/*.xctestrun"), None)
    if xctestrun is None:
        sys.exit("build the driver first: TEAM_ID=T scripts/measure_effects_ios.py --udid UDID --build-driver")
    names = cards()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "meta.json").write_text(json.dumps({"plays": a.plays, "relaunch": not a.no_relaunch,
                                               "started": time.strftime("%Y-%m-%d %H:%M:%S")}, indent=2))
    for t in (a.types.split(",") if a.types else TYPES):
        # A fresh install: no Metal shader cache, as on a user's first catch.
        run(["xcrun", "devicectl", "device", "uninstall", "app", "--device", a.udid, BUNDLE])
        r = run(["xcrun", "devicectl", "device", "install", "app", "--device", a.udid, str(APP)])
        if r.returncode != 0:
            sys.exit(f"install failed: {r.stderr}")
        code = record(a, out / t, f"{t}:{names[t]}", xctestrun)
        lines = [line for line in (out / t / "test.log").read_text().splitlines()
                 if " plays" in line or "didn't" in line or "error:" in line]
        print(f"{t:9s} {names[t]}: test exit {code}; {' | '.join(lines)[:200]}", flush=True)
    report(out)


# --- reading the export back --------------------------------------------------------------------

def guess_schema(node):
    row = node.find("row")
    if row is None or len(row) < 4:
        return None
    first, fourth = row[0].tag, row[3].tag
    if first == "sample-time":
        return "time-sample"
    if first == "event-time":
        return "os-signpost"
    if first == "start-time" and fourth == "display-name":
        return "hitches-updates"
    return None


def read_tables(path):
    """{schema: [row dicts of column → element]}. Exported rows share values: the first use carries
    id="N", later ones <tag ref="N"/>, across tables, so every row is resolved in order."""
    root = ET.parse(path).getroot()
    tables, ids = {}, {}

    def resolve(el):
        ref = el.get("ref")
        if ref is not None:
            return ids[ref]
        if el.get("id") is not None:
            ids[el.get("id")] = el
        for child in el:
            resolve(child)
        return el

    for node in root.iter("node"):
        schema = node.find("schema")
        name = schema.get("name") if schema is not None else guess_schema(node)
        cols = ([c.findtext("mnemonic") for c in schema.findall("col")] if schema is not None
                else COLUMNS.get(name, []))
        rows = tables.setdefault(name, [])
        for row in node.findall("row"):
            rows.append(dict(zip(cols, [resolve(el) for el in row])))
    return tables


def num(el):
    if el is None or el.tag == "sentinel":
        return None
    try:
        return int(el.text)
    except (TypeError, ValueError):
        return None


def label(el):
    if el is None or el.tag == "sentinel":
        return ""
    return el.get("fmt") or "".join(el.itertext())


def plays(tables):
    """[(message, tap_ns, end_ns)] of the plays that ticked the card ("kept"). A play starts at the UI
    test's "Synthesize event" (the tap itself), not at the driver's signpost: a frame can land between."""
    begins, out, kept, taps = {}, [], [], []
    for r in tables.get("os-signpost", []):
        name, kind, t = label(r.get("name")), label(r.get("event-type")), num(r.get("time"))
        ident, msg = label(r.get("identifier")), label(r.get("message"))
        if label(r.get("subsystem")) != SUBSYSTEM:
            if msg == "Synthesize event" and kind == "Begin":
                taps.append(t)
            continue
        if name == "play" and kind == "Begin":
            begins[ident] = (msg, t)
        elif name == "play" and kind == "End" and ident in begins:
            m, start = begins.pop(ident)
            tap = min((x for x in taps if start <= x <= t), default=start)
            out.append((m, tap, t))
        elif name == "kept":
            kept.append((t, msg))
    return [max((p for p in out if p[0] == msg and p[2] <= t), key=lambda p: p[2])
            for t, msg in kept if any(p[0] == msg and p[2] <= t for p in out)]


def frames(tables):
    """(start_ns, duration_ns) of PokeCollect's commits."""
    return sorted((num(r.get("start")), num(r.get("duration"))) for r in tables.get("hitches-updates", [])
                  if label(r.get("process")).startswith("PokeCollect"))


def cpu_samples(tables):
    """(time_ns, main | gc | other | mtl) per running 1 ms sample."""
    out = []
    for r in tables.get("time-sample", []):
        if label(r.get("thread-state")) != "Running":
            continue
        th = label(r.get("thread"))
        if "(PokeCollect," in th:
            kind = "main" if th.startswith("Main Thread") else "gc" if "GC" in th else "other"
            out.append((num(r.get("time")), kind))
        elif "(MTLCompilerService," in th:
            out.append((num(r.get("time")), "mtl"))
    return out


def measure_play(start, end, commits, samples, end_ms):
    ups = [u for u in commits if start <= u[0] <= end]
    if len(ups) < 2:
        return None
    limit = ups[0][0] + (end_ms + LEAD_MS) * 1_000_000
    ups = [u for u in ups if u[0] <= limit]
    span = (ups[0][0], ups[-1][0] + ups[-1][1])
    gaps = [(b[0] - a[0]) / 1e6 for a, b in zip(ups, ups[1:])]
    cpu = Counter(kind for t, kind in samples if start <= t <= span[1])
    effect_cpu = Counter(kind for t, kind in samples if span[0] <= t <= span[1])
    n = len(ups)
    return {"frames": n, "late": sum(g > LATE_MS for g in gaps), "gap_max": max(gaps),
            "commit_p50": statistics.median(u[1] / 1e6 for u in ups),
            "main_ms": effect_cpu["main"] / n, "other_ms": effect_cpu["other"] / n, "gc_ms": effect_cpu["gc"],
            "mtl_ms": cpu["mtl"]}


def report(out):
    out = Path(out)
    meta = json.loads((out / "meta.json").read_text())
    ends = effect_end_ms()
    lines = [f"iPhone 16e · fresh install per type · {meta['plays']} plays + relaunch · {meta['started']}",
             "frames = PokeCollect's commits during the effect; late = gaps over 1.5 vsyncs (25 ms); gap = worst gap "
             "(ms); mtl = MTLCompilerService CPU ms from the tap; main / other = app CPU ms per frame on the main "
             "thread / other threads (warm plays); gc = GC thread ms per warm play",
             "type      | first ever: frames late   gap  mtl | second: late   gap  mtl | plays 3+: n frames late   gap "
             " mtl main other gc | relaunch: frames late   gap  mtl"]
    summary = {}
    for d in sorted(p for p in out.iterdir() if p.is_dir()):
        tables = read_tables(d / "tables.xml")
        commits, samples = frames(tables), cpu_samples(tables)
        measured = {}
        for msg, s, e in plays(tables):
            m = measure_play(s, e, commits, samples, ends[d.name])
            if m:
                measured[msg.split(" ", 1)[1]] = m
        summary[d.name] = measured
        f, two, r = measured.get("1"), measured.get("2"), measured.get("R1")
        warm = [m for k, m in measured.items() if k not in ("1", "2", "R1")]
        line = f"{d.name:9s} |"
        line += (f" {f['frames']:18d} {f['late']:4d} {f['gap_max']:5.0f} {f['mtl_ms']:4.0f} |"
                 if f else " " + "-" * 33 + " |")
        line += (f" {two['late']:12d} {two['gap_max']:5.0f} {two['mtl_ms']:4.0f} |" if two else " " + "-" * 23 + " |")
        line += (f" {len(warm):12d} {sum(p['frames'] for p in warm):6d} {sum(p['late'] for p in warm):4d} "
                 f"{max(p['gap_max'] for p in warm):5.0f} {sum(p['mtl_ms'] for p in warm):4.0f} "
                 f"{statistics.median(p['main_ms'] for p in warm):4.1f} "
                 f"{statistics.median(p['other_ms'] for p in warm):5.1f} "
                 f"{statistics.median(p['gc_ms'] for p in warm):2.0f} |" if warm else " " + "-" * 48 + " |")
        line += (f" {r['frames']:16d} {r['late']:4d} {r['gap_max']:5.0f} {r['mtl_ms']:4.0f}" if r else " " + "-" * 32)
        lines.append(line)
    text = "\n".join(lines)
    (out / "report.txt").write_text(text + "\n")
    (out / "summary.json").write_text(json.dumps(summary, indent=1))
    print(text)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--udid", help="the iPhone's UDID (xcrun devicectl list devices)")
    p.add_argument("--types", help="comma-separated; default all 18")
    p.add_argument("--plays", type=int, default=4, help="per type: play 1 is the first ever, the rest warm")
    p.add_argument("--no-relaunch", action="store_true", help="skip the play after a relaunch")
    p.add_argument("--keep-trace", action="store_true")
    p.add_argument("--build-driver", action="store_true")
    p.add_argument("--out")
    p.add_argument("--report", metavar="DIR")
    a = p.parse_args()
    if (a.build_driver or a.out) and not a.udid:
        p.error("--udid is required to build the driver or measure")
    if a.build_driver:
        build_driver(a)
    if a.report:
        report(a.report)
    elif a.out:
        measure(a)


if __name__ == "__main__":
    main()
