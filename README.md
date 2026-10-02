# BETTER

Native Android V1 implementation. Product source of truth: [specs/README.md](specs/README.md).
Current gate status and evidence: [evidence/progress.md](evidence/progress.md).

## Build on this Windows workspace

```powershell
. ./tools-env.ps1
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb devices -l
adb install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 0 -w dev.better.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start --user 0 -n dev.better.app/.MainActivity
```

The USB phone must be unlocked and authorized. No emulator is required.
The initial dependency download requires internet on the build computer;
the installed app has no network permission. On another computer, install JDK 17
and Android SDK 36/Build Tools 35, set JAVA_HOME and local.properties SDK path,
then use the checked-in Gradle wrapper.

## Structure

- `app/.../domain`: pure time/rule/state functions and injectable clock
- `app/.../data`: Room schema, transactions, migrations, DataStore preferences
- `app/.../platform`: Android adapters
- `app/.../feature`: Compose UI and lifecycle-aware state
- `app/schemas`: versioned exported Room schemas
- `app/src/test`: host JVM tests
- `app/src/androidTest`: physical-device Room and Compose tests

Release tasks are guarded until the complete V1 acceptance checklist passes.
Do not clear app storage or uninstall when real personal data exists without
first exporting a backup. Instrumentation tests use separate test databases.
The optional `InstalledDataPreservationTest` runs only with the explicit
`verifyPersonalData=true` instrumentation argument and requires the private
pre-update phone snapshot. See [Phase 2 evidence](evidence/phase2-checklist.md).
