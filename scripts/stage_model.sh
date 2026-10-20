#!/usr/bin/env bash
# Puts the Gemma model file into the AI pack before building a bundle (bundleRelease).
# The file is gated on Hugging Face (litert-community/Gemma3-1B-IT, Gemma licence) and never committed.
#
#   scripts/stage_model.sh [path/to/gemma3-1b-it-int4.litertlm]   (default: ~/Downloads/…)
set -euo pipefail

name="gemma3-1b-it-int4.litertlm"
src="${1:-$HOME/Downloads/$name}"
dest="$(cd "$(dirname "$0")/.." && pwd)/gemma_pack/src/main/assets/$name"

if [[ ! -f "$src" ]]; then
  echo "Model not found: $src" >&2
  echo "Download $name from https://huggingface.co/litert-community/Gemma3-1B-IT and pass its path." >&2
  exit 1
fi

mkdir -p "$(dirname "$dest")"
rm -f "$dest"
# A hard link costs no disk space; fall back to a copy across volumes.
ln "$src" "$dest" 2>/dev/null || cp "$src" "$dest"
echo "Staged $(du -h "$dest" | cut -f1) at ${dest#"$PWD"/}"
