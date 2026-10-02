# Implementation decisions

## 2026-10-02 — Phase 4 Career/Rescue

- Room v4 adds focus sessions and segments through explicit migration 3→4.
  Earlier migrations remain. Pre-update DB/WAL/SHM copies stay phone-private in
  `files/phase4-preservation/`; prior snapshots remain untouched.
- Active duration uses `SystemClock.elapsedRealtime`, paired with Android
  `BOOT_COUNT` and a process-owner token. Same-boot process loss closes a segment
  for review: saved checkpoint time remains, candidate time is excluded until
  confirmed. Reboot/unknown continuity keeps only the checkpoint. Recovery review
  time never counts. Resume always creates a new segment.
- Save checkpoints every five seconds while the process can execute. No new
  foreground service, exact alarm, wake lock or permission is needed. Android may
  suspend/kill the process; checkpoints are best effort, not a five-second loss
  guarantee. Same-boot monotonic reconstruction still requires user confirmation.
  Official API references checked for elapsed time across sleep and boot count:
  https://developer.android.com/reference/android/os/SystemClock
  https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT
- Rescue caps at exactly 300,000 active milliseconds; waiting at the Continue/Stop
  choice is excluded. Continue retains session identity and removes this cap.
- Segments snapshot timezone/day boundary; monotonic duration is allocated across
  real operational boundaries. Observed wall-time/timezone changes split the
  segment and anchor future allocation to the new wall time without adding time.
  Active segment duration retains integer milliseconds to avoid losing fractional
  seconds at repeated pauses; targets stay integer seconds and UI shows mm:ss.
- New targets start tomorrow or later. Existing effective dates cannot be replaced;
  append a different future date. Existing 30-minute seed and history are preserved.
  Timed minimum milestones are recorded independently of manual occurrence status;
  a timer never silently completes a scheduled item or claims an application was sent.
- Start requires deliberate-work confirmation and one of the six specified activity
  labels. Optional company/role remain self-reported; Review labels duration timed.
- Physical lifecycle fixtures use a debug-only activity and separate database.
  No synthetic focus sessions are inserted into the owner's personal plan.
  Phase 5/6 features and release APKs remain outside this phase.

## 2026-09-30 — Initial package audit

Read README and every supplied file (01–08). The user also references
`09-build-start-guide.md` was initially absent. The user supplied it in the next
turn; it has now been read in full. Preflight is complete.

The following clarifications apply the existing document hierarchy, without
adding V1 features. Corresponding source documents are amended below.

1. Attention threshold: section 05 defines **accumulated** selected-package
   foreground time within an eligible window. Replace “continuous” in 02;
   interruptions do not reset that window's aggregate.
2. Carry-forward: section 05 retains the original missed occurrence and creates
   a linked follow-up only when explicitly selected. It does not leave the
   original indefinitely open (contrary to the wording in 02).
3. Finite sequence: the anchor plus finite offsets supplies an implicit count
   of `1 + offsets.size`. A separate end/count is not required without a tail.
4. Historical identity: title, category, priority, essential status, instruction
   and source text must be snapshotted on CommitmentVersion, alongside its rule,
   reminder policy and duration. Editing Commitment's current fields alone must
   not rewrite historical text. AT-11 requires this.
5. Phase 0 seeds the editable 30-minute Career standard and onboarding settings
   required by AT-01. Timer execution remains phase 4. AT-30 is checked for the
   features available at each gate and fully rerun at release.
6. Physical USB device is the development test target. No emulator is required
   to build or run gates. The release matrix in 07 remains outstanding until
   tested on the required physical Android versions; one phone does not prove
   coverage of every version. Never mark an unrun acceptance test passed.
7. DST preview requires acknowledgement of a gap adjustment or overlap offset
   choice before save, reconciling FR-SCHED-003 with the defaults in 05.

## Toolchain investigation

No Java, Gradle, adb or sdkmanager found on PATH; no JDK/SDK in checked standard
installation locations. Windows detects a Samsung ADB USB interface. Downloaded
official platform-tools into the ignored workspace `.tools` folder. ADB reports
`unauthorized` initially. The phone owner approved debugging; the connection now
reports `device`: Samsung SM-A556E, Android 16 / API 36.

Candidate baseline: JDK 17, Gradle 8.13, AGP 8.13.2, compile/target SDK 36.
AGP's official compatibility table supports API 36.1, Gradle 8.13 and JDK 17:
https://developer.android.com/build/releases/agp-8-13-0-release-notes
Current Play target guidance identifies API 36:
https://developer.android.com/google/play/requirements/target-sdk
No release APK is authorized before all gates.

## 2026-09-30 — Phase 0 baseline

- Application ID `dev.better.app`, display name BETTER; retain this ID for updates.
- minSdk 26 (native java.time); compile/target SDK 36; JDK 17, AGP 8.13.2,
  Gradle 8.13; Kotlin and Compose compiler 2.1.20, KSP 2.1.20-1.0.32.
- Compatible pinned libraries: Compose BOM 2025.04.01, Material 3, Room 2.7.1,
  Activity 1.10.1, Lifecycle 2.9.0, DataStore 1.1.4. Room 2.7 requires Kotlin 2+.
  https://developer.android.com/jetpack/androidx/releases/room
  https://android-developers.googleblog.com/2025/04/whats-new-in-jetpack-compose-april-25.html
- The command-line JDK/SDK/Gradle toolchain satisfies the build requirement of
  guide 09. Android Studio remains optional for editing; no IDE or emulator
  installation is required for the proven command-line/USB workflow.
- Schema v1 stores local setup, the Career commitment, effective-dated Career
  standards and append-only actions. Upcoming phases add versioned rules and
  occurrences through explicit migrations. No artificial historical migration
  is invented for a brand-new database; test the exported v1 schema and reopen.
- Disable cloud backup and device transfer for private domain data. V1's explicit
  document-picker archive will be the supported portability path in phase 6.
- The new guide suggests wider Android testing before distribution. Keep 07's
  stricter release matrix as written; do not silently waive it for a personal APK.

## 2026-10-01 — Phase 1 time semantics

- Windows crossing the operational boundary close at max(day boundary, window end),
  so a chosen 23:00–06:00 window is not marked missed at 04:00. Operational-date
  allocation remains fixed to the start. ANYTIME closes directly to MISSED at the
  boundary. Both clarify ordering ambiguities in 05; no extra feature is added.
- Completion-anchored intervals preview only the next known occurrence; later
  dates depend on actual completion. Calendar-day/week/month addition preserves
  the actual local completion time, resolving DST by the rule's persisted policy.
- Consecutive monthly sequence offsets preserve the day of their monthly run's
  starting date. A day/week offset starts a new monthly run. Monthly tails use the
  final finite sequence date as their anchor to avoid February drift.
- Schema v2 adds typed rule columns, immutable version content snapshots,
  deterministic occurrence IDs and one-occurrence overrides. The v1 Career
  commitment identity and effective-dated standard are retained.
- Ordinary repeated schedules materialize the current calendar date plus the
  next 44 dates. Existing unresolved history is retained; new series do not invent
  years of past history. An explicitly entered past one-off is retained as such.
- With one generated item per planned date, a selected future occurrence defines
  the version boundary at the beginning of its planned date. This permits moving
  that item earlier on the same date; protected old-version records still win.
- Single-occurrence editing changes its title, instructions/source and due time;
  series duration, priority and reminder policy remain on its source version.
  The editor states this explicitly. Reopening uses the saved override values.
- Dependency-update lint notices remain visible; they are not suppressions or
  app-code failures. Final Phase 1 build pins remain the verified Phase 0 baseline.


## 2026-10-01 — Phase 2 execution and review

- Room remains schema v2. Execution writes versioned JSON snapshots (`format: 1`)
  to the existing append-only ActionLog alongside the ordinary occurrence and
  override records, in one Room transaction. Undo appends a correction; it never
  removes the original action. Archive remains reversible.
- Complete records confirmation at the actual tap/save time and labels evidence
  self-reported. Completing after the due endpoint is late, including correction
  of a missed occurrence. The original planned date remains. Manual Career
  completion does not invent timed minutes; timer execution remains Phase 4.
- Skip, cancel and move require a reason. Completion-interval skips default to
  stop, with explicit restart-from-skip or manually previewed next-date choices.
  After stopping, Edit schedule with a new future anchor restarts the series.
  Moves preserve occurrence identity and calendar-series dates. A second Career
  move requires an explicit additional override and reason, including editor moves.
- Completion-chain generation selects the last terminal occurrence, not an
  ordinary move/edit. A generated successor key includes its completed predecessor
  so an early completion can produce a next item on the original planned date.
- Undo is durable across process restart. It is rejected if later edits/actions
  changed the record, or a generated successor has started or changed. An undone
  occurrence remains protected because a correction is itself historical action.
- Conflict blocks use scheduled start plus entered duration, with touching
  endpoints permitted. Flexible blocks are proposed intervals, not reservations
  for the entire allowed due window. Zero-duration items occupy no interval.
  Preview and save/move check overlaps; keeping an overlap requires acknowledgement.
  No event is automatically moved. Fixed events are ordinary local commitments
  with their existing fixed flag, entered via Add fixed event; no calendar access.
- Today is reconciled at foreground resume and every 15 seconds while resumed.
  Closed-app/background reconciliation and notifications belong to Phase 3.
  Review uses recorded operational-day keys, separates terminal states and late
  completion, and offers daily or seven-day facts without a composite score.
- Basic readability: formatted dates/times with timezone, plain status/evidence
  labels, distinct action hierarchy, ten-item Plan/Review pagination, and tab
  scroll reset. Full reference-led visual redesign stays after functional Phase 6.
- The pre-update database snapshot stays under the app's private phone storage
  (`files/phase2-preservation/`). It is a development preservation safeguard,
  not the Phase 6 export feature. No personal database was pulled to the computer.

## 2026-10-02 — Phase 3 reminders

- Room v3 adds a reminder delivery ledger and an immutable version flag for the
  user's quiet-hours exception. Explicit migration 2→3 retains every old column;
  migration 1→2 remains registered. The new pre-update snapshot is phone-private
  `files/phase3-preservation/`; the Phase 2 snapshot is retained.
- All ordinary reminders use inexact AlarmManager delivery. Optional precise
  alarms are not implemented or requested in this phase; the UI states this.
  No USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM permission is declared. An exact due
  time in a schedule is a due definition, not a guarantee of exact OS delivery.
- One Android alarm wakes the next batch from the durable 45-day plan, avoiding
  OEM limits on outstanding alarms. WorkManager 2.10.1 provides the receiver
  handoff and a 15-minute best-effort safety check; existing dependency pins stay.
- Each policy step is consumed at most once for a schedule start. Quiet-hour
  steps coalesce; Android-delayed steps also coalesce to the latest eligible
  step. An already planned step may post up to one hour late, before day close.
  Recovery skips all past steps. A durable boot/version/timezone/clock marker protects
  recovery even if a periodic worker runs before the broadcast receiver.
- Quiet hours follow the current phone timezone. Equal endpoints disable them.
  Delays that reach day close do not revive missed occurrences the next morning.
  A per-version user exception bypasses app quiet hours only, never Android DND.
- A durable claim precedes each OS post attempt. A crash after that claim stays
  unconfirmed rather than retrying a potentially posted alert. Posted, opened,
  blocked, missed, coalesced and cancelled remain distinct. Opening is evidence
  of a notification interaction, not proof that its text was read.
- Notification taps target the exact stable occurrence. Complete/Move/Skip open
  the existing confirmation/preview flow after unlock. Resolved or moved stale
  links open the current record without automatically repeating an action.
  Instruction/source text stays inside the app; lock-screen titles are optional.
- Live alarm/reboot fixtures use a debug-only activity and a separate private
  database. They reuse the production engine, notification adapter and WorkManager
  handoff. No fixture commitments are inserted into the user's personal plan.
- Android behavior checked against official alarm, notification permission and
  WorkManager references on 2026-10-02:
  https://developer.android.com/develop/background-work/services/alarms
  https://developer.android.com/develop/ui/compose/notifications/notification-permission
  https://developer.android.com/jetpack/androidx/releases/work#2.10.1

### Phase 3 verification decisions — 2026-10-02

- Use the same captured instant for quiet-hour eligibility and its comparison.
  Two wall-clock reads can differ by milliseconds and falsely defer an alert;
  the live-discovered bug now has an advancing-clock regression.
- Instrumentation force-stops the target and cancels alarms. Inspect live reboot
  recovery read-only before launching the app or running instrumentation. The
  invalidated first reboot was repeated; boot 74 recovered automatically.
- Record observed timing honestly: actual posting was 80.095 seconds late and
  reboot recovery was delayed several minutes. No immediate-delivery guarantee.
