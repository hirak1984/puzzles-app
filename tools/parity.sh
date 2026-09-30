#!/bin/bash
# Cross-platform parity check for the puzzle logic.
#
# 1. Compiles the iOS Swift puzzle code (Models/RNG, Hashi, Tracks, Sudoku)
#    on macOS and regenerates shared/fixtures/puzzles.json from it.
# 2. Runs the Android unit tests, which require the Kotlin port to produce
#    exactly the same puzzles for the same seeds.
#
# Run after ANY change to puzzle generation/solving/status logic on either
# platform. If the fixture changes, commit it together with the matching
# change on the other platform.
set -euo pipefail
cd "$(dirname "$0")/.."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> Generating fixtures from Swift (iOS logic)"
swiftc -O Puzzles/Models/RNG.swift Puzzles/Models/Hashi.swift Puzzles/Models/Tracks.swift \
    Puzzles/Models/Sudoku.swift tools/fixtures/main.swift -o "$TMP/fixgen"
"$TMP/fixgen" "$TMP/puzzles.json"
"$TMP/fixgen" "$TMP/again.json"
cmp -s "$TMP/puzzles.json" "$TMP/again.json" || { echo "!! Swift generation is not deterministic"; exit 1; }

mkdir -p shared/fixtures
if cmp -s "$TMP/puzzles.json" shared/fixtures/puzzles.json; then
    echo "    fixtures unchanged"
else
    cp "$TMP/puzzles.json" shared/fixtures/puzzles.json
    echo "    fixtures UPDATED — the Swift logic's output changed; commit shared/fixtures/puzzles.json"
fi

echo "==> Running Kotlin parity tests (Android logic)"
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)"
    export JAVA_HOME
fi
(cd android && ./gradlew -q testDebugUnitTest)
echo "==> Parity OK: iOS and Android generate identical puzzles"
