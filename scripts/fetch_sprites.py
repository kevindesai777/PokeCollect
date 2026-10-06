#!/usr/bin/env python3
"""Download the classic 96x96 sprites into the app's resources, for local builds only.

    python3 scripts/fetch_sprites.py

Output: shared/src/commonMain/composeResources/files/sprites/{id}.png  (gitignored)

The sprites are The Pokemon Company's artwork, so they are never committed to this repo.
Running this script packages them into *your* build, so the app shows them without ever
touching the network. Skip it and the app falls back to dex-number placeholders.
"""

import json
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# Pin the sprites repo so every build gets identical images. Bump deliberately.
SPRITES_SHA = "a3a1432e688ea028f12c51371d5253037cb9f17b"
SPRITE_URL = f"https://raw.githubusercontent.com/PokeAPI/sprites/{SPRITES_SHA}/sprites/pokemon/{{id}}.png"

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "shared/src/commonMain/composeResources/files"
DATASET = RESOURCES / "pokemon.json"
OUT = RESOURCES / "sprites"


def fetch(species_id):
    target = OUT / f"{species_id}.png"
    if target.exists() and target.stat().st_size > 0:
        return species_id, "cached"
    req = urllib.request.Request(SPRITE_URL.format(id=species_id), headers={"User-Agent": "pokecollect-sprite-build"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            target.write_bytes(r.read())
        return species_id, "ok"
    except Exception as e:  # one missing sprite should not sink the build
        return species_id, f"failed: {e}"


def main():
    ids = [p["id"] for p in json.loads(DATASET.read_text(encoding="utf-8"))["pokemon"]]
    OUT.mkdir(parents=True, exist_ok=True)

    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(fetch, ids))

    failed = [(i, s) for i, s in results if s.startswith("failed")]
    fetched = sum(1 for _, s in results if s == "ok")
    size_kb = sum(f.stat().st_size for f in OUT.glob("*.png")) // 1024
    print(f"{len(ids) - len(failed)}/{len(ids)} sprites ({fetched} downloaded, {size_kb} KB total) -> {OUT.relative_to(ROOT)}")
    for i, s in failed:
        print(f"  #{i}: {s}", file=sys.stderr)


if __name__ == "__main__":
    main()
