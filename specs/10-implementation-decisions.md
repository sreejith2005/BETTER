# Implementation decisions

## 2026-10-03 — Phase 5 routines and adaptive day

- Room v5 uses explicit migration 4→5 and retains all earlier migrations. It adds
  `adaptive_records` (immutable format-1 routine/profile versions, assignments,
  routine links, mode activations and day overrides) and `routine_runs`. Logical
  entities share a typed snapshot table; repository transactions validate their
  references. It also adds nullable occurrence provenance and block duration/fixed
  snapshots. Old occurrence columns, Career and reminder records are unchanged.
- Routine steps use their version's ordered indices as stable step identities.
  Each run snapshots its exact title/order/text and records completed/skipped
  results; append-only step actions carry timestamps. Reopening resumes the same
  run. Completing a routine does not silently complete its scheduled occurrence.
- Routine/profile edits append a later effective date (tomorrow or later). A new
  identity can start today. Assignment and policy changes may start today but
  never rewrite already due, delivered, acted or resolved records. Future
  unacted suppression remains recalculable until its scheduled start; after that,
  it remains suppressed history, never missed. Dates are calendar dates as in 05.
- Precedence is base → profile → latest explicitly activated mode → day override
  → occurrence override → recorded state. Explicit Keep restores base timing;
  unspecified items inherit the preceding layer. Keep essential only restores
  essential items unless individually changed. Normal is an empty special policy
  for its date range, replacing earlier modes on those dates.
- A one-off profile assignment wins over weekday assignment for that date;
  otherwise the latest applicable weekday assignment wins. Profile versions are
  selected by effective date. Linked blocks reuse occurrence IDs, snapshot their
  duration/fixed flag, and cannot make an existing fixed commitment flexible.
  Unlinked blocks are proposed timeline intervals, without duplicate obligations.
  A routine-linked block also selects its scheduled commitment for run identity.
- Moves propose a start within the original allowed window. Fixed items never
  move automatically. Out-of-window or ambiguous DST proposals retain the earlier
  plan and visibly explain that an explicit occurrence edit is needed. No medical
  instruction text is changed or inferred. Per-item changes are explicit.
- Fixed profile blocks also retain their proposed start against later mode moves
  or Keep policies. Overnight-window moves resolve on the following calendar date
  where appropriate. A future completion-anchored suppression is excluded from
  terminal-chain selection until it is protected, keeping repeated reconciliation
  stable and allowing an explicit replacement policy to restore it before due.
- Routine start logs against both the run and its occurrence. Undo of an earlier
  move cannot rewrite a subsequently started run's occurrence history. Nullable
  profile/mode/day fields round-trip through execution snapshots; old format-1
  snapshots without those fields still parse.
- After the owner's offline review, preview cards, plain state labels, dark button
  contrast and editor scroll reset were improved. Full redesign remains deferred.
- A later preservation comparison allows an original PLANNED reminder to become
  MISSED only after its scheduled time, including the accompanying detail text.
  All other original reminder fields remain exact. The immediate migration
  comparison and the final comparison both passed 229 original rows.
- Preview covers up to 45 days from the effective date, including dates beyond
  today's materialized horizon. Completion-anchored rules show only their next
  known occurrence. Preview/save compare authoritative state and run in a Room
  transaction; overlap retention and overlapping mode replacement need explicit
  acknowledgement. Saving triggers the existing reminder reconciliation.
- The pre-update DB/WAL/SHM snapshot is private on the phone under
  `files/phase5-preservation/`. Previous snapshots are untouched. Preservation
  comparison reads a temporary private copy so SQLite cannot change the original
  snapshot's shared-memory read marks. No personal database is exported to the PC.
- Migration testing follows the existing exported-schema harness, checked against
  the official [MigrationTestHelper reference](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).
  Dependencies and permissions are unchanged. Phase 6, release packaging and full
  visual redesign remain out of scope; request Pinterest references after Phase 6.

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

## 2026-10-04 — Phase 6 implementation and verification decisions

- Add Room v6 with explicit 5→6 migration: attention_settings and attention_attempts.
  Preserve all existing tables/columns, migrations and phone-private snapshots.
- Attention is opt-in as well as permission-gated. Selected-package raw events
  are used transiently for foreground intervals, then discarded. A bounded
  24-hour lookback reconstructs carry-in sessions; totals are clipped to today
  and eligible overnight windows. Package RESUMED/PAUSED pairs are used because
  class names do not identify activity instances and STOPPED can arrive late.
  Ambiguous multi-resume streams remain unavailable rather than double-counted.
  Missing events, an incomplete boundary/session, locked storage or revoked access
  are unavailable, not zero. Observations expire after two minutes for decisions.
- Editable eligible, office and commute windows; default office exclusion is
  09:00–18:00, inherited from configured work hours. No commute duration is
  invented: exclusions are blank until entered. Configured mode titles can suppress
  Attention. Quiet hours and running/complete Career suppress it independently.
- One claim per eligible window, minimum 60-minute cooldown, maximum two per
  operational day. Claims are committed before posting; a crash is not retried.
  POSTED means Android post request, not user observation. No blocking/overlay.
- Existing best-effort WorkManager checks and foreground Attention refresh sample
  usage. This cannot promise intervention while Instagram is still foreground:
  an unclosed usage interval is conservatively unavailable until Android supplies
  a complete interval. Android/OEM delays remain visible limits.
- Archive format 1 / Room 6 is full replacement after preview, never merge.
  Validate typed rows, schema, primary/foreign keys and domain references in a
  separate temporary phone database before one transaction replaces the target.
  Reject stale previews. Preserve row order for chronological tie-breaking.
  Exclude permission grants and platform notification IDs; rebuild future alerts.
- Export/import requires any Career timer to be finished. This avoids restoring
  stale monotonic/boot markers as active work. Completed sessions and exact
  recorded segments round-trip. External archives are readable, user-selected
  files outside app control; local destinations are recommended for offline use.
- Delete requires an exact confirmation phrase. Delete domain rows only, never
  preservation directories or external files. AT-29 deletion uses a separately
  named synthetic database exclusively; the personal database is not exported to PC.
- Theme joins the archived settings transaction; legacy DataStore remains a
  fallback until the Room setting exists. The strengthened archive fixture fills
  every one of the 15 tables, including overrides, reminder and Attention evidence.
- The owner completed the actual offline SAF synthetic round trip and reported
  PASS / Every table matches. Granted usage was observed in the installed UI with
  selected package and timestamp. Owner revocation showed Unavailable in the UI
  and null in the actual adapter test. Final combined phone run passed 77/77;
  host tests passed 35/35. Owner restored Opt in off after testing.
- Preservation permits a pending reminder to become BLOCKED only after its due
  time while notifications are disabled, with the exact unavailable-notifications
  reason and all attempted/posted/observed timestamps still null. Other original
  fields remain exact. This is normal live-clock behavior, not migration loss.
- Android official references rechecked during implementation:
  https://developer.android.com/reference/android/app/usage/UsageStatsManager
  https://developer.android.com/training/data-storage/shared/documents-files
- Phase 6 is complete within `evidence/phase6-checklist.md` coverage. Controlled
  clocks establish policy timing; no real daily Attention soak or manual Attention
  notification delivery/tap is claimed. AT-30 offline behavior and the local-only
  manifest/dependency baseline are verified; no packet/network-log trace was taken.
  No Phase 7, full redesign or release APK is included. Request Pinterest references
  and stop; do not begin visual work without a new instruction.

## 2026-10-04 — Owner feedback and next-chat UI/UX rebuild

This entry follows the Phase 6 stop and records the owner's NEW request to prepare
the next chat. It does not start redesign implementation in this chat or Phase 7.

- The owner manually tested BETTER and could not understand its purpose, any of
  the displayed features, or how to use it. Existing automated functional passes
  are retained but are not evidence of usability. Comprehension is a blocking
  product problem before any marketing ambitions.
- Rebuild information architecture, navigation, interaction flows, terminology,
  hierarchy and presentation as needed. Do not limit the work to a cosmetic skin.
  Preserve verified domain behavior and existing personal data while changing how
  users discover and operate features.
- Explicit complaints: overwhelming quantities of text/information, little reason
  to read it, and very long pages requiring repeated scrolling. Use concise visible
  explanations, clear primary actions, focused screens and on-demand detail. Keep
  capabilities discoverable; hiding everything behind unlabeled icons is not a fix.
- Visual intent: clean but NOT minimalistic; creative, immersive and expressive.
  The desired emotional experience is encouragement and a manageable sense of
  making life a little better. Do not equate clean with sparse/generic, or expressive
  with clutter, excessive animation, guilt or childish gamification.
- Initially design for this owner, not hypothetical market segments. Future age
  questions, demographic collection, age-dependent experiences and Play Store
  marketing are ideas only and remain deferred. The suggested age ranges are not
  a validated model of user ability; any later adaptation should consider expressed
  preferences and accessibility needs instead of assuming ability from age.
- The owner will supply multiple Pinterest references with notes on particular
  liked parts. None is a complete target design. Maintain a reference-to-preference
  record HERE, identifying what is explicit, inferred, unresolved or rejected.
  Wait for the owner to finish the reference batch before committing a direction.
  A missing/inaccessible reference must not be imagined or treated as inspected.
- Next-chat sequence: read source specs/current handoff and Phase 6 limits; inspect
  current journeys; gather references; synthesize a coherent design and navigation
  with representative reviewable screens; then implement the agreed direction in
  Compose and validate on the USB phone. A review checkpoint must show concrete
  work, not ask approval for an abstract plan. Avoid fragmented new planning docs.
- Validation must include owner task walkthroughs and plain-language comprehension:
  can the owner explain BETTER's purpose, identify the next action, and complete
  everyday actions without agent coaching? Record observed difficulties and revise.
  Separately run relevant automated regressions, data/snapshot checks, and basic
  accessibility/navigation checks. Do not claim all usability is solved from a
  screenshot or test count. Define measurable checks with the proposed journeys.
- Preserve Career/Rescue, reminder semantics, scheduling previews, adaptive-day
  precedence, versions/history, exact health instructions/Unconfirmed status,
  opt-in Attention, archive integrity and offline local-only baseline. UI copy may
  be simplified without changing medical text or disguising unavailable states.
- Verify installed state before any update: last recorded dev.better.app debug
  0.7.0-phase6 / code 7, Room 6. No uninstall/storage clear/destructive migration,
  no personal DB export to PC, and no overwriting Phase 2–6 private snapshots.
  Any destructive test uses isolated synthetic storage. No release APK or Phase 7.
