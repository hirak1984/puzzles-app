# Puzzles — iOS + Android + Web

One product, three codebases that must stay feature-identical:

- **iOS** — SwiftUI, `Puzzles/` + `project.yml` (XcodeGen). App Store.
- **Android** — Kotlin + Jetpack Compose, `android/`. Google Play.
- **Web** — puzzles.hirakonline.com. Vanilla JS, no build step, in a separate
  repo: [hirak1984/puzzles](https://github.com/hirak1984/puzzles), checked out
  at `../puzzles-web` (`PUZZLES_WEB` overrides). Pushing its `main` deploys
  automatically (Cloudflare Workers Builds), so ask before pushing.

## The rule: every change goes into all three

Any feature, fix, new puzzle, rule text, color, level config or behaviour
change made on one platform must be made on the other two. Apps: **in the same
commit/PR**. Web: a matching commit in `../puzzles-web`, referenced from the
app commit message. Never leave one behind. If a change can't sensibly apply
to one platform (e.g. an iOS gesture quirk), say so explicitly in the commit
message and in the file map below.

A new puzzle means: model + engine + board + rules + home card + icon on each
platform, a fixture section in `tools/fixtures/main.swift`, and matching checks
in `ParityTest.kt` and the web's `tests/parity.test.mjs`.

Before finishing any change, run:

```bash
tools/parity.sh
```

It regenerates `shared/fixtures/puzzles.json` from the **Swift** puzzle logic,
runs the **Kotlin** unit tests (`android/app/src/test/.../ParityTest.kt`), then
copies the fixture into the website repo and runs its **JS** tests
(`tests/parity.test.mjs`). All three must produce identical puzzles for
identical seeds. If puzzle generation/solving changes, update `Models/*.swift`,
`android/.../model/*.kt` and `../puzzles-web/site/js/*.js`, then commit the
regenerated fixture in both repos.

Build checks:

```bash
# iOS
xcodegen generate   # after adding/removing files
xcodebuild -project Puzzles.xcodeproj -scheme Puzzles -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
# Android (JDK 17+; this Mac has JDK 21 at ~/Library/Java/JavaVirtualMachines)
cd android && ./gradlew assembleDebug testDebugUnitTest
# Web
cd ../puzzles-web && npm test && (cd site && python3 -m http.server 8765)
```

## File map (iOS ⇄ Android ⇄ Web)

Android paths are under `android/app/src/main/java/online/hirak/puzzles/`, web
paths under `../puzzles-web/site/`.

| iOS (`Puzzles/`) | Android | Web |
| --- | --- | --- |
| `Models/RNG.swift` | `model/Rng.kt` | `js/rng.js` |
| `Models/Hashi.swift` | `model/Hashi.kt` | `js/hashi.js` |
| `Models/Tracks.swift` | `model/Tracks.kt` | `js/tracks.js` |
| `Models/Sudoku.swift` | `model/Sudoku.kt` | `js/sudoku.js` |
| `Models/HashiEngine.swift` | `game/HashiEngine.kt` (+ shared `game/GameEngine.kt`) | `js/hashi-ui.js` (`mount`) + `js/app.js` |
| `Models/TracksEngine.swift` | `game/TracksEngine.kt` | `js/tracks-ui.js` (`mount`) |
| `Models/SudokuEngine.swift` | `game/SudokuEngine.kt` | `js/sudoku-ui.js` (`mount`) |
| `Support/Store.swift` | `data/Store.kt` | `js/common.js` |
| `Support/Theme.swift` | `ui/Theme.kt` | `styles.css` |
| `Views/Components.swift` | `ui/Components.kt` | `js/app.js` (`renderGame`), `styles.css` |
| `Views/GameIcons.swift` | `ui/GameIcons.kt` | `js/app.js` (`GAMES[*].icon`) |
| `Views/HomeView.swift` | `ui/HomeScreen.kt` | `js/app.js` (`renderHome`) |
| `Views/*GameScreen.swift` (rules text) | `ui/GameScreens.kt` | `js/app.js` (`GAMES[*].rules`) |
| `Views/HashiBoardView.swift` | `ui/HashiBoard.kt` | `js/hashi-ui.js` (`render`) |
| `Views/TracksBoardView.swift` | `ui/TracksBoard.kt` | `js/tracks-ui.js` (`render`) |
| `Views/SudokuBoardView.swift` | `ui/SudokuBoard.kt` | `js/sudoku-ui.js` (`render`, keypad) |
| `Views/SwipeBackDisabler.swift` | `ui/BoardGesture.kt` (`systemGestureExclusion`) | n/a (`touch-action:none` on boards) |
| `PuzzlesApp.swift` | `MainActivity.kt` | `index.html`, `js/app.js` (router, theme toggle) |
| `Fonts/*.ttf` | `res/font/*.ttf` | Google Fonts link in `index.html` |
| `Assets.xcassets/AppIcon` | `res/mipmap-*` + `android/play/icon-512.png` | inline favicon in `index.html` |

Web-only adaptations (intentional): the URL `#/<game>/<level>/<seed>` names
the puzzle so it can be shared (an explicit seed beats resume), and Sudoku
also accepts a physical keyboard (1–9 to enter, Backspace/Delete/0 to erase).

## Invariants worth knowing

- Puzzles are fully determined by `(level, seed)`. Never iterate a Swift
  `Dictionary`/`Set` (random order per launch) or a Kotlin `HashMap` where
  the order feeds the RNG — sort first. Resuming a saved game depends on it.
- Saved progress is keyed `puzzles.<game>.<level>.<seed>`; the open puzzle per
  level is `puzzles.current.<game>.<level>`. Keep key names the same on all
  three (web: `localStorage`).
- Kotlin `HashMap` / JS `Map` / Swift `Dictionary` orders all differ. Where
  key order feeds the RNG, sort the keys on every platform.
- Version numbers: bump `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` in
  `project.yml` and `versionName`/`versionCode` in `android/app/build.gradle.kts`
  together.
