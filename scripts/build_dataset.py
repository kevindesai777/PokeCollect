#!/usr/bin/env python3
"""Build the bundled species dataset from PokeAPI's CSV dump.

Run once, commit the output. The app never touches the network for this data.

    python3 scripts/build_dataset.py

Output: shared/src/commonMain/composeResources/files/pokemon.json

Data: PokeAPI (https://github.com/PokeAPI/pokeapi), BSD-3-Clause.
Pokemon names are trademarks of Nintendo / Game Freak / The Pokemon Company.
"""

import csv
import io
import json
import re
import sys
import urllib.request
from pathlib import Path

# Pin the dump so every build of the dataset is identical. Bump deliberately.
POKEAPI_SHA = "89289e314ae20a34e8159a34a5fe646e656d6149"
CSV_BASE = f"https://raw.githubusercontent.com/PokeAPI/pokeapi/{POKEAPI_SHA}/data/v2/csv"

# URL only, for reference. The app shows classic sprites from scripts/fetch_sprites.py instead
# (gitignored, packaged into local builds only); official artwork is never bundled.
IMAGE_URL = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/{id}.png"

LANG_EN = 9
# Alternate names kept for fuzzy-matching speech input later.
ALIAS_LANGS = {2: "ja-roma", 5: "fr", 6: "de", 7: "es", 8: "it"}

REGIONAL = re.compile(r"-(alola|galar|hisui|paldea)(-|$)")
EXCLUDED_FORMS = re.compile(r"-(totem|cap|gmax|zen|starter)(-|$)")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "shared/src/commonMain/composeResources/files/pokemon.json"


def fetch(name):
    req = urllib.request.Request(f"{CSV_BASE}/{name}.csv", headers={"User-Agent": "pokecollect-dataset-build"})
    with urllib.request.urlopen(req) as r:
        return list(csv.DictReader(io.StringIO(r.read().decode("utf-8"))))


def main():
    species = fetch("pokemon_species")
    species_names = fetch("pokemon_species_names")
    pokemon = fetch("pokemon")
    pokemon_types = fetch("pokemon_types")
    types = {t["id"]: t["identifier"] for t in fetch("types")}
    forms = fetch("pokemon_forms")
    form_names = fetch("pokemon_form_names")

    types_by_pokemon = {}
    for row in sorted(pokemon_types, key=lambda r: int(r["slot"])):
        types_by_pokemon.setdefault(row["pokemon_id"], []).append(types[row["type_id"]])

    names = {}
    aliases = {}
    for row in species_names:
        lang = int(row["local_language_id"])
        if lang == LANG_EN:
            names[row["pokemon_species_id"]] = row["name"]
        elif lang in ALIAS_LANGS and row["name"]:
            aliases.setdefault(row["pokemon_species_id"], {})[ALIAS_LANGS[lang]] = row["name"]

    default_pokemon = {p["species_id"]: p for p in pokemon if p["is_default"] == "1"}

    entries = []
    for s in sorted(species, key=lambda s: int(s["id"])):
        sid = s["id"]
        p = default_pokemon[sid]
        entries.append({
            "id": int(sid),
            "name": names[sid],
            "slug": s["identifier"],
            "generation": int(s["generation_id"]),
            "types": types_by_pokemon[p["id"]],
            "imageUrl": IMAGE_URL.format(id=p["id"]),
            "aliases": aliases.get(sid, {}),
        })

    battle_only = {f["pokemon_id"] for f in forms if f["is_battle_only"] == "1"}
    form_id_by_pokemon = {f["pokemon_id"]: f["id"] for f in forms}
    form_display = {r["pokemon_form_id"]: r["pokemon_name"] for r in form_names
                    if r["local_language_id"] == str(LANG_EN) and r["pokemon_name"]}

    regional_forms = []
    for p in sorted(pokemon, key=lambda p: int(p["id"])):
        ident = p["identifier"]
        match = REGIONAL.search(ident)
        if not match or EXCLUDED_FORMS.search(ident) or p["id"] in battle_only:
            continue
        regional_forms.append({
            "id": int(p["id"]),
            "speciesId": int(p["species_id"]),
            "name": form_display.get(form_id_by_pokemon.get(p["id"]), ident),
            "slug": ident,
            "region": match.group(1),
            "types": types_by_pokemon[p["id"]],
            "imageUrl": IMAGE_URL.format(id=p["id"]),
        })

    # Sanity checks. Fail the build rather than ship a bad dataset.
    ids = [e["id"] for e in entries]
    if ids != list(range(1, len(ids) + 1)):
        sys.exit("species ids are not contiguous from 1")
    if any(not e["types"] or not e["name"] for e in entries):
        sys.exit("species missing name or types")

    dataset = {
        "version": f"pokeapi-{POKEAPI_SHA[:7]}",
        "speciesCount": len(entries),
        "generations": max(e["generation"] for e in entries),
        "pokemon": entries,
        "regionalForms": regional_forms,
    }

    # One record per line: readable diffs when the dataset version bumps.
    def line(obj):
        return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))

    body = ",\n".join("  " + line(e) for e in entries)
    forms_body = ",\n".join("  " + line(f) for f in regional_forms)
    header = {k: v for k, v in dataset.items() if k not in ("pokemon", "regionalForms")}
    text = (
        "{\n"
        + "".join(f' "{k}":{line(v)},\n' for k, v in header.items())
        + ' "pokemon":[\n' + body + "\n ],\n"
        + ' "regionalForms":[\n' + forms_body + "\n ]\n}\n"
    )
    json.loads(text)  # guard against a formatting mistake above

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"{dataset['version']}: {len(entries)} species, {len(regional_forms)} regional forms "
          f"-> {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
