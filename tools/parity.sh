#!/bin/bash
# Cross-platform parity check for the puzzle logic (iOS, Android, web).
#
# 1. Compiles the iOS Swift puzzle code (Models/RNG, Hashi, Tracks, Sudoku)
#    on macOS and regenerates shared/fixtures/puzzles.json from it.
# 2. Runs the Android unit tests, which require the Kotlin port to produce
#    exactly the same puzzles for the same seeds.
# 3. Copies the fixture into the website repo (puzzles.hirakonline.com,
#    default ../puzzles-web, override with PUZZLES_WEB) and runs its JS tests,
#    which hold the web logic to the same output.
#
# Run after ANY change to puzzle generation/solving/status logic on any
# platform. If the fixture changes, commit it together with the matching
# change on the other platforms (and the copy in the website repo).
set -euo pipefail
cd "$(dirname "$0")/.."

WEB="${PUZZLES_WEB:-../puzzles-web}"
if [[ ! -f "$WEB/site/js/rng.js" ]]; then
    echo "!! Website repo not found at $WEB — clone it first:"
    echo "   git clone https://github.com/hirak1984/puzzles.git $WEB"
    exit 1
fi

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

echo "==> Running web parity tests (puzzles.hirakonline.com logic)"
mkdir -p "$WEB/tests/fixtures"
cmp -s shared/fixtures/puzzles.json "$WEB/tests/fixtures/puzzles.json" || {
    cp shared/fixtures/puzzles.json "$WEB/tests/fixtures/puzzles.json"
    echo "    web fixture UPDATED — commit $WEB/tests/fixtures/puzzles.json too"
}
(cd "$WEB" && node tests/parity.test.mjs && node tests/generate.test.mjs > /dev/null)
echo "==> Parity OK: iOS, Android and the web generate identical puzzles"
