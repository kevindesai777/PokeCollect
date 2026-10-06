#!/usr/bin/env python3
"""Aggregate EVALROW lines from ModelEvalTest's final run into per-configuration results.

    python3 scripts/aggregate_eval.py final_part1.log final_part2.log ...

Each EVALROW line (tab-separated): EVALROW, config, question index, exact (0/1), field hits
(4 chars: intent generation type caught), unusable (0/1), used model (0/1), latency ms,
question, raw output, expected query.
"""

import statistics
import sys
from collections import defaultdict

FIELDS = ["intent", "generation", "type", "caught"]


def load(paths):
    rows = []
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if parts[0] != "EVALROW" or len(parts) < 11:
                    continue
                rows.append({
                    "config": parts[1], "index": int(parts[2]), "exact": parts[3] == "1",
                    "hits": parts[4], "unusable": parts[5] == "1", "model": parts[6] == "1",
                    "latency": int(parts[7]), "question": parts[8], "raw": parts[9], "expected": parts[10],
                })
    return rows


def main(paths):
    rows = load(paths)
    by_config = defaultdict(list)
    for row in rows:
        by_config[row["config"]].append(row)
    questions = sorted({r["index"] for r in rows})
    print(f"{len(rows)} rows, {len(questions)} questions, {len(by_config)} configurations\n")

    header = f"{'configuration':<30} {'exact':>9} " + " ".join(f"{f[:5]:>6}" for f in FIELDS) + \
        f" {'unusable':>9} {'model':>7} {'p50 ms':>7} {'max ms':>7}"
    print(header)
    print("-" * len(header))
    for config in sorted(by_config, key=lambda c: -sum(r["exact"] for r in by_config[c])):
        rs = by_config[config]
        n = len(rs)
        field = [sum(r["hits"][i] == "1" for r in rs) for i in range(4)]
        latencies = sorted(r["latency"] for r in rs if r["model"])
        p50 = statistics.median(latencies) if latencies else 0
        print(f"{config:<30} {sum(r['exact'] for r in rs):>4}/{n:<4} " +
              " ".join(f"{h:>6}" for h in field) +
              f" {sum(r['unusable'] for r in rs):>9} {sum(r['model'] for r in rs):>7} {p50:>7.0f} {max(latencies, default=0):>7}")

    # Where configurations disagree: useful for error analysis.
    if "--failures" in sys.argv:
        print("\nFailures by configuration:")
        for config in sorted(by_config):
            for r in sorted(by_config[config], key=lambda r: r["index"]):
                if not r["exact"]:
                    print(f"  [{config}] q{r['index']:02d} {r['question']!r} -> {r['raw']}  WANT {r['expected']}")


if __name__ == "__main__":
    main([a for a in sys.argv[1:] if not a.startswith("--")])
