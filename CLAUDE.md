# Puzzles — iOS + Android

One app, two native codebases that must stay feature-identical:

- **iOS** — SwiftUI, `Puzzles/` + `project.yml` (XcodeGen). App Store.
- **Android** — Kotlin + Jetpack Compose, `android/`. Google Play.

## The rule: every change goes into both apps

Any feature, fix, rule text, color, level config or behaviour change made on
one platform must be made on the other **in the same commit/PR**. Never leave
one app behind. If a change can't sensibly apply to one platform (e.g. an iOS
gesture quirk), say so explicitly in the commit message and in the file map
below.

Before finishing any change, run:

```bash
tools/parity.sh
```

It regenerates `shared/fixtures/puzzles.json` from the **Swift** puzzle logic
and runs the **Kotlin** unit tests (`android/app/src/test/.../ParityTest.kt`),
which require identical puzzles for identical seeds. If puzzle
generation/solving changes, update both `Models/*.swift` and
`android/.../model/*.kt`, then commit the regenerated fixture.

Build checks:

```bash
# iOS
xcodegen generate   # after adding/removing files
xcodebuild -project Puzzles.xcodeproj -scheme Puzzles -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO
# Android (JDK 17+; this Mac has JDK 21 at ~/Library/Java/JavaVirtualMachines)
cd android && ./gradlew assembleDebug testDebugUnitTest
```

## File map (iOS ⇄ Android)

| iOS (`Puzzles/`) | Android (`android/app/src/main/java/online/hirak/puzzles/`) |
| --- | --- |
| `Models/RNG.swift` | `model/Rng.kt` |
| `Models/Hashi.swift` | `model/Hashi.kt` |
| `Models/Tracks.swift` | `model/Tracks.kt` |
| `Models/Sudoku.swift` | `model/Sudoku.kt` |
| `Models/HashiEngine.swift` | `game/HashiEngine.kt` (+ shared `game/GameEngine.kt`) |
| `Models/TracksEngine.swift` | `game/TracksEngine.kt` |
| `Models/SudokuEngine.swift` | `game/SudokuEngine.kt` |
| `Support/Store.swift` | `data/Store.kt` |
| `Support/Theme.swift` | `ui/Theme.kt` |
| `Views/Components.swift` | `ui/Components.kt` |
| `Views/GameIcons.swift` | `ui/GameIcons.kt` |
| `Views/HomeView.swift` | `ui/HomeScreen.kt` |
| `Views/*GameScreen.swift` (rules text) | `ui/GameScreens.kt` |
| `Views/HashiBoardView.swift` | `ui/HashiBoard.kt` |
| `Views/TracksBoardView.swift` | `ui/TracksBoard.kt` |
| `Views/SudokuBoardView.swift` | `ui/SudokuBoard.kt` |
| `Views/SwipeBackDisabler.swift` | `ui/BoardGesture.kt` (`systemGestureExclusion`) |
| `PuzzlesApp.swift` | `MainActivity.kt` |
| `Fonts/*.ttf` | `res/font/*.ttf` |
| `Assets.xcassets/AppIcon` | `res/mipmap-*` + `android/play/icon-512.png` |

## Invariants worth knowing

- Puzzles are fully determined by `(level, seed)`. Never iterate a Swift
  `Dictionary`/`Set` (random order per launch) or a Kotlin `HashMap` where
  the order feeds the RNG — sort first. Resuming a saved game depends on it.
- Saved progress is keyed `puzzles.<game>.<level>.<seed>`; the open puzzle per
  level is `puzzles.current.<game>.<level>`. Keep key names the same on both.
- Version numbers: bump `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` in
  `project.yml` and `versionName`/`versionCode` in `android/app/build.gradle.kts`
  together.
