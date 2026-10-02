# 09 — Start building BETTER on your Android phone

## What is ready

The package is sufficient to **start building V1**. It specifies product behavior, screens, storage, scheduling, reminders, build order, and acceptance checks. It does not mean the entire end-state vision can be delivered as one unchecked code generation step. V1 is the frozen scope in `02-v1-scope.md`; future ideas remain later.

**Recommended stack:** Kotlin + Jetpack Compose, with Room and Android platform APIs as specified in `04-architecture-data.md`. This app is Android-first and relies heavily on reminders, reboot recovery, local storage, and optional Usage Access. Native Android keeps these parts in one language and avoids a React Native bridge. React Native is a valid alternative, but it adds integration work without advancing the stated Android-only goal. The phone is a valid primary test device; no emulator is required to begin.

## Exact order

1. **Create one project folder named `Better`.** Put all ten Markdown files from this package in `Better/specs/`, keeping their names. Keep app source in the same `Better` project root. Do not paste separate copies of the PRD into unrelated chats.
2. **Open `Better` as the workspace in Codex.** Ask Codex to inspect the files and report any contradictions before creating code. The package's decision hierarchy is in `README.md`.
3. **Install Android Studio and its Android SDK/platform tools.** You need these build tools even when using a physical phone. You do not need to create or run an Android Virtual Device. Let Codex check what is already installed and guide any missing setup. [Official installation guide](https://developer.android.com/studio/install).
4. **Prepare the phone.** Enable Developer options (normally tap Build number seven times), turn on USB debugging, connect with a data-capable USB cable, unlock the phone, and approve the computer's RSA debugging prompt. On Windows, install the phone maker's ADB driver if the device is not detected. Confirm that `adb devices` shows the phone as `device`, not `unauthorized`. [Official hardware-device guide](https://developer.android.com/studio/run/device).
5. **Have Codex build Phase 0 first.** It should create the Kotlin/Compose project, local database foundation, tests, and install a launchable app on the phone. This proves the development connection before a large implementation begins.
6. **Build Phases 1–7 in order** from `06-implementation-roadmap.md`. Codex may continue autonomously between phases after each gate passes, but it must report failures and resolve them before dependent work. Review actual app behavior on the phone at the end of each UI phase.
7. **Grant permissions only when the feature asks.** Notifications and optional Usage Access require taps in Android settings. You perform those phone actions; Codex verifies the resulting app state. Do not grant broad or unrelated permissions.
8. **Run the full acceptance checklist** in `07-acceptance-tests.md` on the phone. Include offline use, denied permissions, reminders, reboot, backup round trip, timer interruption, and Instagram intervention. DST rules can be tested with a fixed clock without changing the phone to a DST zone.
9. **Make a release build for personal use.** Ask Codex for a signed installable APK, retain the signing key securely, install the release build on the phone, and repeat critical checks because debug and release behavior can differ. Export a backup before replacing a build with real personal data.
10. **Only then add later features.** Put new ideas in `BACKLOG.md`; do not silently expand V1 while its basic scheduling and data history are still being verified. If distributing to others, add more Android-version/device testing and check current Google Play requirements.

## Prompt for the first Codex build chat

> Read every file in `specs/`, starting with `README.md` and `09-build-start-guide.md`. Build **BETTER V1 only** in this folder using Kotlin, Jetpack Compose, Room, and the specified Android APIs. I will test on my USB-connected Android phone; do not make an emulator a prerequisite. First inspect the specs for contradictions and check my Android build environment and phone connection. Then implement `06-implementation-roadmap.md` phase by phase, running each gate before proceeding. Keep the package as the source of truth, record necessary implementation decisions, and do not add later-scope features. When a step needs an action on my phone, tell me the exact tap sequence and continue after I do it. Provide a concise progress report and test evidence at each phase. Produce a release APK only after the full V1 acceptance checklist passes.

## What may still need decisions during development

Codex should select currently compatible Android SDK and library versions and confirm the phone's Android version. It should choose the app package ID before installation (for example, a reverse-domain identifier you control), then keep it stable for updates. It may need you to choose the app name/icon and tap permission dialogs. Those are routine implementation and device steps; they do not reopen product ideation.
