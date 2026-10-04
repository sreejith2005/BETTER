# BETTER V1 — next chat handoff

Updated 2026-10-04 after the Phase 6 phone gate. Workspace: `C:\Users\user\Downloads\BETTER`.

## Current state

**Phases 0–6 implemented and phone gates recorded. Next chat: dedicated UI/UX rebuild after reference intake; no Phase 7 or release.**

Phase 6 session (2026-10-04): debug 0.7.0-phase6 / code 7 is installed in place,
Room v6. Initial and final preservation checks passed 239 original rows across 13 tables.
New phone-private DB/WAL/SHM snapshot: `files/phase6-preservation/`; older snapshots
remain untouched. No personal database was copied to the PC.

Owner confirmed the offline synthetic document-picker export/delete/preview/restore
screen displayed PASS, every table matches. See `evidence/phase6-picker-result.txt`.
Actual denied, granted and owner-revoked Usage Access were verified. Installed UI
shows package, total and observation timestamp when available; revocation shows
Unavailable. Boundary reconstruction uses a bounded lookback and strict package
resume/pause pairs; ambiguous or open sessions remain unavailable.

Final **35/35 host tests and 77/77 physical-phone tests pass**; debug builds and lint
pass (0 errors, 14 existing warnings). All 15 Phase 2–6 snapshot files remain
byte-identical. Full report: `evidence/phase6-checklist.md`; exact manual steps:
`evidence/phase6-phone-steps.md`. The report distinguishes controlled-clock policy
tests from actual permission/picker observations. No real daily Attention soak,
manual Attention notification tap, or packet-level network trace is claimed.
AT-30 offline behavior and manifest/dependency baseline are verified; its network-log
subcheck was not performed. The intermittent profile UI timeout passed on retry and
in the final complete run; earlier failures remain recorded.

The owner has now requested preparation for a dedicated UI/UX rebuild in a NEW
chat. No redesign implementation is authorized in this handoff-preparation turn.
Read the next section and the latest UX decision entry before beginning reference
intake. This is a post-Phase-6 usability/design stage, not automatic authorization
for roadmap Phase 7, release packaging or Play Store work.
The user prefers one phase per chat. No release APK was produced or authorized.
Read all `specs/` files and recorded decisions, then `evidence/progress.md` and
`evidence/phase6-checklist.md` before continuing. Specs remain the source of truth.

## Next chat — UI/UX rebuild brief (owner feedback, 2026-10-04)

The owner manually tried the app and could not understand its purpose, features
or how to use them, despite having originated the idea. Functional test passes
do NOT establish usability or product acceptance. Text overload and long scrolling
pages caused the owner to stop reading. This is a structural comprehension problem,
not a request to merely change colors, fonts or cards.

Design first for this owner. The desired direction is **clean, creative, immersive,
expressive and non-minimalistic**, with clear hierarchy and little competing text.
Opening BETTER should feel encouraging: a manageable opportunity to improve part
of one's life. Keep factual accountability without guilt, childish gamification or
claims that the app guarantees a particular feeling or outcome.

The owner will bring multiple Pinterest images/links and identify liked parts.
No single reference is the target. Record each reference and the specific liked
element in the existing decisions document; distinguish explicit preferences from
inference. Wait until the owner says the reference batch is complete before
settling on a combined direction. Do not invent missing images or copy an entire
unrelated app. Ask focused questions only where the direction remains unclear.

Read the original product intent, audit the existing screens and journeys, then
propose understandable navigation, plain-language labels, focused task flows,
progressive disclosure and a coherent visual system. Preserve access to advanced
scheduling without presenting every option at once. Prioritize what to do today,
starting/stopping Career, getting help through Rescue, adding/editing commitments,
routines, changes to one's day, reviewing history, and optional Attention/privacy.
Do not silently remove or alter verified underlying behavior while simplifying UI.

Make the proposed direction concrete with representative reviewable screens and
flows before broadly rebuilding Compose. Use owner feedback to validate actual
comprehension and task completion on the physical phone; screenshots and passing
automated tests alone are insufficient. Check short/empty/dense/error/permission
states, back navigation, keyboard/scroll behavior, contrast, tap targets, and larger
text. Avoid replacing text overload with unexplained icon-only controls.

Age-based onboarding/personalization (roughly 18–30 versus 30–50), collecting
demographics, and later Play Store marketing were brainstorming only. They are
deferred, not implementation requirements. Do not infer technical ability from age;
if personalized simplicity is revisited, discuss explicit user preferences/needs.
No new demographic collection, monetization or publishing work is authorized.

Maintain one source of decisions in `specs/10-implementation-decisions.md`, update
this handoff and evidence/progress as work proceeds, and carry forward all phone
preservation rules below. Latest detailed UX intent is recorded in that document.

## Installed app and preservation

- App ID `dev.better.app`, debug `0.7.0-phase6`, versionCode 7, Room schema v6.
- Explicit migrations 1→2→3→4→5→6. Phase 6 adds attention_settings and
  attention_attempts; existing tables/columns remain. No destructive migration,
  uninstall or reset. Archive format 1 restores Room 6 data after preview.
- Samsung SM-A556E / Android 16 / API 36, phone user 0; serial `RZCY40K24SD`.
- All **239 pre-update rows across 13 tables** passed preservation comparison.
  All 15 snapshot DB/WAL/SHM files across Phases 2–6 remain byte-identical. Elapsed
  pending alerts may become MISSED or precisely checked BLOCKED while notification
  permission is denied; identity/content/delivery evidence remains exact.
- Phone-private snapshots remain in `files/phase2-preservation/`,
  `files/phase3-preservation/`, `files/phase4-preservation/`,
  `files/phase5-preservation/`, and `files/phase6-preservation/`. Never overwrite
  or casually remove them. No personal database was exported to this PC.
- Last verified on 2026-10-04: Flight mode off, Wi-Fi on, notifications denied,
  Usage Access revoked; owner confirmed Opt in unticked and saved. Normal
  BETTER was relaunched after tests. Synthetic picker DB/archive may remain on the
  phone; never confuse them with the live database or preservation snapshots.

## Phase 5 result

Routines and adaptive day are implemented within frozen scope. Full report:
`evidence/phase5-checklist.md`. Exact phone steps: `evidence/phase5-phone-steps.md`.

- Final **31 host tests and 68/68 physical-phone tests passed**. Final debug app/
  test builds and lint pass: 0 errors, 14 existing notices. See
  `phase5-verified-final-build.txt`, `phase5-host-tests.json`, and
  `phase5-device-verified.txt` under `evidence/`.
- Routine versions/runs retain old labels, order, results and timestamped actions.
  Profile blocks/versions and weekday/one-off assignments preserve stable linked
  occurrences. Modes/day overrides preview overlaps and keep suppression separate
  from missed. Future completion-anchored suppression is stable and reversible.
- Modifier precedence: base → profile → latest active mode → day override →
  occurrence override → recorded state. Already due/delivered/acted records freeze.
  Unstarted suppression may be replaced; closed suppression never becomes missed.
- Owner confirmed an offline preview and basic readability, while requesting
  better readability. Small card/contrast/scroll improvements followed. Full
  redesign remains after Phase 6 and Pinterest references.
- One profile UI timeout in the first final suite did not reproduce in an isolated
  retry or the complete 68-test rerun; no code change between those runs. Evidence
  is retained. The cause is unproven. Real three-day mode expiry is not claimed;
  dated boundaries use controlled clocks on the physical phone.
- Existing Career/Rescue and reminder regressions pass. No new OS permission,
  dependency, Attention feature, archive feature or release APK was added.

### Phase 5 code and storage notes

- `domain/AdaptiveDay.kt`: pure policy precedence and safe windowed moves.
- `data/AdaptiveData.kt`: immutable format-1 `adaptive_records` snapshots, stable
  routine/profile families, routine runs and snapshot selection. Kind values are
  ROUTINE, PROFILE, ASSIGN, LINK, MODE and DAY. Later archive work must include
  both new tables, run versions/results and occurrence provenance fields.
- `data/AdaptiveRepository.kt`: validated preview/save transactions, immutable
  version creation, stale-preview rejection, overlap acknowledgement and runs.
- `feature/AdaptiveScreen.kt`: editors, assignments, previews, checklist/history.
- Linked profile blocks reuse existing occurrence IDs; standalone blocks are
  advisory intervals. A routine block needs a scheduled commitment for its run.
  Routine completion does not silently complete the occurrence. Starting a run
  protects it from undo of earlier execution changes.
- New routine/profile versions must take effect on a later future date. Reverse
  pending assignments/modes/overrides through a new previewed replacement. Fixed
  events stay fixed; invalid/DST-ambiguous proposals explain the retained plan.
- Execution JSON remains format 1 and accepts absent new nullable fields in old
  snapshots. Preserve this backward compatibility in subsequent work.

## Phase 4 result (previous gate)

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

Next scope only when requested: **Phase 6 Attention and archive export/import**.
Keep basic UI readable. Do not start Phase 6 as part of the completed Phase 5 task.
**After Phase 6, request the user's Pinterest references before full visual redesign.**
Phase 7 still needs broader Android, battery-saver, lock-screen and accessibility
coverage. Keep release tasks guarded. No release APK.
