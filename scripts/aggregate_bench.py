#!/usr/bin/env python3
"""Summarizes LatencyBenchTest logs (BENCHROW lines) per configuration.

Usage: python3 scripts/aggregate_bench.py <log files...> [--ref ENGINE:VARIANT] [--diffs]

One table row per (engine, variant): medians of each phase, model-only accuracy on labelled
questions, and, with --ref, how many outputs are byte-identical (whitespace-trimmed) to the
reference configuration on the same question. Greedy decoding is deterministic, so a speed-up
that changes no output cannot change accuracy.
"""
import statistics
import sys
from collections import defaultdict

args = [a for a in sys.argv[1:] if not a.startswith("--")]
ref = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--ref=")), None)
show_diffs = "--diffs" in sys.argv

rows = defaultdict(list)
for path in args:
    for line in open(path, encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if not f or f[0] != "BENCHROW" or f[3] == "WARMUP":
            continue
        rows[(f[1], f[3])].append({
            "tag": f[2], "index": int(f[4]), "create": int(f[5]), "send": int(f[6]),
            "prefill_tok": f[7], "prefill_tps": f[8], "decode_tok": f[9], "decode_tps": f[10],
            "ttft": f[11], "exact": f[13], "state": f[14], "output": f[15].strip(), "question": f[16],
        })


def med(values):
    values = [float(v) for v in values if v not in ("", None)]
    return statistics.median(values) if values else float("nan")


def p90(values):
    values = sorted(float(v) for v in values)
    return values[min(len(values) - 1, int(round(0.9 * (len(values) - 1))))] if values else float("nan")


reference = None
if ref:
    engine, variant = ref.split(":")
    reference = {r["question"]: r["output"] for r in rows.get((engine, variant.upper()), [])}

header = ["engine", "variant", "n", "create", "send", "total", "p90 total", "prefill tok", "prefill t/s",
          "decode tok", "decode t/s", "ttft s", "exact", "errors"] + (["same as ref"] if reference else [])
print("\t".join(header))
for (engine, variant), rs in sorted(rows.items()):
    totals = [r["create"] + r["send"] for r in rs]
    labelled = [r for r in rs if r["exact"] in ("0", "1")]
    errors = sum(1 for r in rs if r["output"].startswith("ERROR"))
    cells = [engine, variant, len(rs), f"{med(r['create'] for r in rs):.0f}", f"{med(r['send'] for r in rs):.0f}",
             f"{med(totals):.0f}", f"{p90(totals):.0f}", f"{med(r['prefill_tok'] for r in rs):.0f}",
             f"{med(r['prefill_tps'] for r in rs):.0f}", f"{med(r['decode_tok'] for r in rs):.0f}",
             f"{med(r['decode_tps'] for r in rs):.1f}", f"{med(r['ttft'] for r in rs):.2f}",
             f"{sum(r['exact'] == '1' for r in labelled)}/{len(labelled)}", errors]
    if reference:
        shared = [r for r in rs if r["question"] in reference]
        same = sum(r["output"] == reference[r["question"]] for r in shared)
        cells.append(f"{same}/{len(shared)}")
    print("\t".join(str(c) for c in cells))

if reference and show_diffs:
    print("\nOutputs that differ from the reference:")
    for (engine, variant), rs in sorted(rows.items()):
        for r in rs:
            if r["question"] in reference and r["output"] != reference[r["question"]]:
                print(f"[{engine} {variant} #{r['index']}] {r['question']}\n    ref: {reference[r['question']]}\n    got: {r['output']}")
