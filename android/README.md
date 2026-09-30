# Puzzles — Android

Native Kotlin + Jetpack Compose port of the iOS app in `../Puzzles/`: Hashi, Train
Tracks and Sudoku. It has the same puzzles, rules, colours, fonts and behaviour. Both
apps are kept in lockstep; read [`../CLAUDE.md`](../CLAUDE.md) before changing
anything.

- Package / application ID: `online.hirak.puzzles`
- minSdk 26 (Android 8.0), target/compile SDK 37 (Android 17)
- No third-party libraries at runtime, only AndroidX (Compose, Activity, Lifecycle).
  The unit tests use JUnit and `org.json`.
- No network, no ads, no analytics. Progress is stored on the device in SharedPreferences.

## Build

You need JDK 17 or newer and the Android SDK. Android Studio provides both; just open
the `android/` folder. From the command line:

```bash
cd android
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected phone / running emulator
./gradlew testDebugUnitTest      # cross-platform parity tests
./gradlew lintDebug
```

`local.properties` (gitignored) points at the SDK. Android Studio writes it for you,
or create it yourself: `sdk.dir=/path/to/Android/sdk`.

On this Mac the SDK is at `/usr/local/share/android-commandlinetools` and JDK 21 is at
`~/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1`.

## Parity with iOS

`../tools/parity.sh` compiles the Swift puzzle generators and writes
`../shared/fixtures/puzzles.json`, containing 36 puzzles plus RNG test vectors. It
then runs `ParityTest`, which requires the Kotlin generators to reproduce every one
exactly. Run it after touching anything in `model/` or `Puzzles/Models/`.

## Release build for Google Play

Google Play takes an Android App Bundle (`.aab`) signed with your **upload key**.

1. **Create an upload key** (one time; keep the file and passwords safe and out of git):

   ```bash
   keytool -genkeypair -v -keystore ~/puzzles-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **Create `android/keystore.properties`** (gitignored):

   ```properties
   storeFile=/Users/you/puzzles-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

3. **Build the bundle:**

   ```bash
   ./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
   ```

   Without `keystore.properties` the bundle still builds, but it's unsigned and Play
   will reject it.

4. **Bump the version for every upload:** `versionCode` must increase each time. Set
   `versionCode` / `versionName` in `app/build.gradle.kts`, and keep them in step with
   `project.yml` on iOS.

## Publishing on Google Play (one-time setup)

1. Create a Google Play Developer account at <https://play.google.com/console>. There's
   a one-time $25 fee and identity verification. New *personal* accounts must run a
   **closed test with at least 12 testers for 14 days** before production access is
   granted.
2. **Create app** → name "Puzzles", *Game*, *Free*.
3. Keep **Play App Signing** on (the default). Google holds the app signing key and you
   upload with your upload key.
4. Complete the **App content** forms. For this app the answers are:
   - **Privacy policy URL:** required for every app. A short page saying the app
     collects no data is enough; host it on hirakonline.com.
   - **Data safety:** no data collected, no data shared.
   - **Ads:** no ads.
   - **Content rating:** fill in the IARC questionnaire (puzzle game, no objectionable
     content → Everyone / PEGI 3).
   - **Target audience:** 13+ is simplest. Choosing under-13 brings Families policy
     requirements.
5. **Store listing:** short and full description, the 512×512 icon (`play/icon-512.png`),
   a 1024×500 feature graphic, and at least 2 phone screenshots. Take them from an
   emulator or phone with `adb exec-out screencap -p > shot.png`.
6. Upload `app-release.aab` to **Testing → Closed testing**, add testers, and after the
   14-day test apply for production.
