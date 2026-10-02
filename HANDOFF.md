# BETTER V1 — next chat handoff

Updated 2026-10-02. Workspace: `C:\Users\user\Downloads\BETTER`.

## Current state

**Phases 0–4 complete. Stop after the Phase 4 report. Phase 5 has not started.**
The user prefers one phase per chat. No release APK was produced or authorized.
Read all `specs/` files and recorded decisions, then `evidence/progress.md` and
`evidence/phase4-checklist.md` before continuing. Specs remain the source of truth.

## Installed app and preservation

- App ID `dev.better.app`, debug `0.5.0-phase4`, versionCode 5, Room schema v4.
- Explicit migrations 1→2, 2→3 and 3→4. Phase 4 adds only focus sessions/segments;
  existing tables remain unchanged. No destructive migration, uninstall or reset.
- Samsung SM-A556E / Android 16 / API 36, phone user 0; serial `RZCY40K24SD`.
- All **221 pre-update rows across nine tables**, including reminder delivery
  history, passed preservation comparison. Snapshot DB/WAL/SHM hashes matched.
- Phone-private snapshots remain in `files/phase2-preservation/`,
  `files/phase3-preservation/`, and `files/phase4-preservation/`. Never overwrite
  or casually remove them. No personal database was exported to this PC.
- Final: Flight mode off, Wi-Fi on, notifications denied. Synthetic Phase 4
  database/files removed; normal BETTER relaunched and visually checked.

## Phase 4 result

Career and Rescue timers, active segments, interruption recovery, future-dated
minimum settings, six activity labels, optional company/role, daily/seven-day
Career evidence and inspectable timer actions are implemented.

- 25 host tests and **54 distinct phone tests** passed across recorded runs.
- Final debug app/test builds and lint pass: 0 errors, 14 existing notices.
- Offline real background timing >5 minutes passed. Five paused minutes added
  exactly zero time. Same-boot force-stop recovery required confirmation and
  excluded review time.
- Real owner reboot 74→75 retained exactly the last 56,508 ms checkpoint.
  Owner confirmed interrupted recovery, resumed and saved; 5,656 new ms counted.
- Five-minute Rescue cap, continue-to-30, stop-leaves-25, date splitting, DST,
  target history and clock/timezone handling use controlled-clock phone/host tests.
  A full real-time 30-minute Rescue run was not waited through.
- Owner confirmed readable controls and recovery. Full visuals remain deferred.

Full evidence and limits: `evidence/phase4-checklist.md`. Key files:
`phase4-verified-build.txt`, `phase4-timer-device-final.txt`,
`phase4-regression-device.txt`, `phase4-data-preservation.txt`,
`phase4-live-accuracy.txt`, `phase4-process-result.txt`, `phase4-reboot-result.txt`,
`phase4-owner-result.txt`, and `phase4-final-state.txt` (all under `evidence/`).
Manual instructions: `evidence/phase4-phone-steps.md`.

## Timer semantics and code

- `domain/Focus.kt`: monotonic duration, Rescue cap and operational-date allocation.
- `data/FocusData.kt`, `FocusRepository.kt`: transactional sessions/segments,
  boot/process identity, checkpoints, recovery, target changes and append-only logs.
- `feature/CareerScreen.kt`: Today controls, dated target settings and Review.
- `BetterApplication` owns the production timer repository across activity changes.
  Checkpoints run every five seconds while Android permits execution. Cached
  processes can be suspended; this is not a five-second maximum-loss guarantee.
- Same-boot process loss retains saved checkpoint credit and freezes a candidate
  interval for explicit confirmation/rejection. Waiting in recovery never counts.
  Reboot/unknown continuity closes at the checkpoint and offers Resume/Stop.
- Rescue caps at 300,000 active milliseconds and waits for Continue/Stop. Continue
  retains the same session. Targets use seconds; segments retain milliseconds.
- Future target dates are append-only and must start tomorrow or later. Timed
  minimum milestones never silently complete a scheduled occurrence or claim
  application submission. Manual completion never creates timer minutes.
- Debug `probe/FocusProbe.kt` uses only a separate synthetic database. Tools
  `check-focus-live.py`, `check-focus-process.py`, `read-focus-probe.py` must never
  be pointed at personal storage. The fixture was cleaned after verification.

## Existing execution/reminder workflow

- `ExecutionRepository.kt` uses versioned `format: 1` JSON snapshots in append-only
  `EXEC_*` ActionLog rows. Undo must preserve changed dependent history.
- `domain/Reminders.kt`, `data/ReminderRepository.kt`, `ReminderData.kt`,
  `platform/AndroidReminders.kt`, and `feature/ReminderSettings.kt` implement
  bounded inexact reminders and distinct planned/posted/opened/missed evidence.
- Phase 3 report: `evidence/phase3-checklist.md`. Actual alert was about 80 seconds
  late; reboot recovery took several minutes. Timing is best effort; no exact
  alarm permission is requested.
- Instrumentation force-stops BETTER and cancels alarms. Do not use it to inspect
  live reboot recovery. Read the fixture first, then launch; relaunch normal BETTER
  after tests. USB loss is not an app failure; verify `adb devices -l`.

PowerShell: `. ./tools-env.ps1`, then `./gradlew.bat` and `adb`.
Local `.tools/` contains JDK 17, SDK 36, platform tools and Gradle 8.13.
Use `--user 0` because Secure Folder is present. Install debug updates with
`adb install --user 0 -r`. No emulator needed. PowerShell `>` creates UTF-16
text here; Python readers must handle that when reading redirected evidence.

## Scope boundaries

Next scope only when requested: **Phase 5 routines/profiles/modes/day overrides**.
Phase 6 covers Attention and archive export/import. Keep basic UI readable.
**After Phase 6, request the user's Pinterest references before full visual redesign.**
Phase 7 still needs broader Android, battery-saver, lock-screen and accessibility
coverage. Keep release tasks guarded. No release APK.
