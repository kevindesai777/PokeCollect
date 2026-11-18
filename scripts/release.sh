#!/usr/bin/env bash
# Builds PokeCollect for both stores and uploads it: a draft production release on Play and a
# TestFlight build on App Store Connect, or with submit:true both sent for review. The steps are in
# fastlane/Fastfile; keys in fastlane/.env.
#
#   scripts/release.sh version:1.1 [submit:true] [only:android|only:ios] [upload:false] [tests:false]
#
# Write the release notes in fastlane/metadata/android/en-US/changelogs/default.txt first.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v fastlane >/dev/null || { echo "fastlane not found: brew install fastlane" >&2; exit 1; }

export LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8
# No usage reports, no update prompt mid-release, no generated fastlane/README.md.
export FASTLANE_OPT_OUT_USAGE=YES FASTLANE_SKIP_UPDATE_CHECK=1 FASTLANE_HIDE_CHANGELOG=1 FASTLANE_SKIP_DOCS=1

exec fastlane release "$@"
