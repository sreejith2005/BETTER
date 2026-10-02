# BETTER V1 gate ledger

## Preflight — complete, 2026-09-30

- Read all nine supplied files: README and 01–08.
- The user added specs/09-build-start-guide.md; it has been read in full.
- Recorded contradictions and resolutions in specs/10-implementation-decisions.md.
- No existing app or build project in this folder.
- Phone owner authorized USB debugging; adb now reports `device`.
- Verified phone: Samsung SM-A556E, Android 16 / API 36, security patch 2026-08-05.
- Phone storage: approximately 1.9 GB available. Airplane mode currently off.
- Original environment lacked Java/Gradle/SDK on PATH or in checked standard locations.
- Workspace-local Temurin JDK 17.0.20.1 runs; Gradle 8.13 runs (see gradle-version.txt).
- Gradle archive checksum matches official SHA-256. Android command-line tools
  SHA-256 matches the official download page (90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a).
- SDK inventory confirms Build Tools 35.0.0 and platform-tools 37.0.1 installed.
- SDK platform 36 installation completed successfully (installer exit 0).
  See sdk-install.txt and sdk-inventory.txt.
- No emulator downloaded. Debug app installation started on the authorized phone.

| Phase | Status | Evidence |
|---|---|---|
| 0 Foundation | Passed | 3 host + 4 device tests; lint; offline user review and cold restart |
| 1 Scheduling | Passed | 15 host + 14 phone tests; offline user review |
| 2 Execution UI | Passed | 17 host + 28 phone tests; original data comparison; see phase2-checklist.md |
| 3 Reminders | Passed | 20 host tests; 39 distinct phone tests; live actions/reboot; phase3-checklist.md |
| 4 Career/Rescue | Passed | 25 host tests; 54 distinct phone tests; live background/pause/process/reboot; phase4-checklist.md |
| 5 Adaptive day | Not started | — |
| 6 Attention/data | Not started | — |
| 7 Hardening | Not started | Full acceptance matrix pending |

No release APK produced. Phase-scoped acceptance is recorded below; full V1
acceptance and the release matrix remain outstanding.

## Phase 3 gate closed — 2026-10-02

Phase 3 is complete. See `phase3-checklist.md` for coverage and timing limits.
Debug 0.4.0-phase3 installed in place with explicit Room 2→3 migration; all 80
original rows across eight tables passed preservation comparison. 20 host tests,
39 distinct phone tests across overlapping runs, debug build and lint pass.
Offline permission denial, actual enabled notification completion/cancellation,
and real reboot recovery were verified. Posting was about 80 seconds late;
recovery was delayed several minutes. Old steps were not reposted; future alarms
were restored. Final notifications off, Flight mode off, Wi-Fi on. Separate live
fixture removed and normal app relaunched. Phase 4 not started; no release APK.
Full visual redesign remains after Phase 6, when Pinterest references are requested.

## Phase 0 verification — 2026-10-01

- Debug app and test APK compilation succeeded.
- Host tests: 3 passed, 0 failed (operational boundary, DST day, setup validation).
  Report: app/build/test-results/testDebugUnitTest/TEST-dev.better.app.domain.TimeTest.xml.
- Initial full build gate failed lint: local.properties drive colon was not escaped.
  Corrected the SDK path to Java-properties syntax and started a lint rerun.
- Other initial lint findings: 13 dependency/version update notices, no app-code warnings.
  Compatible dependency pins are retained; notices are not suppressed.
- Merged manifest confirms no INTERNET permission; automatic backup is disabled.
- Device currently has Flight mode on and Wi-Fi disabled; unlock requested for UI tests.
- Both debug APKs installed successfully for phone user 0. No release APK built.
- Physical-device FoundationTest: 3 passed (see phase0-room-device.txt).
- Initial lint rerun reused cached analysis of local.properties. Forced only the
  analysis/report tasks to rerun: passed, 0 errors, 13 dependency-update notices
  (phase0-lint-final.txt, BUILD SUCCESSFUL in 44 seconds).
- Phone unlocked by user; normal app cold-launch succeeded (664 ms on final launch).
- First isolated UI test failed before assertions because ActivityScenario's activity
  was destroyed. Normal app launch succeeded; isolated retry passed; a subsequent
  full device suite passed all 4 tests in 4.033 seconds. Preserve the initial failure
  in phase0-ui-device.txt; successful evidence is phase0-ui-retry.txt and
  phase0-device-suite.txt. No app crash was observed.
- Visual inspection of phase0-onboarding.png confirms the offline onboarding UI
  renders on the phone in its current dark theme without clipped fields; the form
  scrolls to remaining inputs/actions.
- User is now performing real notification denial, onboarding, and tab review.
  AT-01 and the Phase 0 gate are not yet marked passed.

### Phase 0 gate closed — 2026-10-01

User reviewed all four tabs and confirmed the Career standard. The user initially
granted notifications, then explicitly revoked them through Android settings.
ADB confirms POST_NOTIFICATIONS=false with USER_SET. Flight mode=1, Wi-Fi disabled.
Force-stop/cold-launch succeeded in 854 ms; captured UI contains Career 0/30,
preferred window 19:00–21:00, and the notification-off message (phase0-restart-ui.xml).
Setup persistence and permission-revoked behavior passed. Initial no-permission
onboarding was also covered by the isolated Compose fixture. AT-01 foundation
behavior passes; AT-30 remains partial until scheduling/timer/archive exist.
No upgrade migration exists yet; v1 schema-open/reopen harness passed.
No release APK produced. Phase 1 is now authorized by the roadmap gate.

## Phase 1 — 2026-10-01

See phase1-checklist.md for implementation and coverage. Debug build and lint pass
(phase1-verified-build.txt); 15 host tests pass. Initial device scheduling-storage
suite passed 8/8, including v1→v2 migration, sequence persistence, version history,
one-occurrence overrides, timezone freeze, DST acknowledgement, completion anchoring,
and archive/restore. Final storage/UI checks are pending. No Phase 2 code started.
- Phase 0 is closed. Phase 1 awaits its phone UI review before Phase 2 begins.

### Phase 1 automated gate update

Final device suite passed 14/14 in 13.735 seconds, including all 9 scheduling
storage cases and both Compose flows (phase1-device-suite-retry.txt). The initial
run failed UI launches with activity-destroyed errors; a concurrent Back gesture
was logged. Isolated editor and full-suite retries passed without code changes.
Actual upgraded app cold-launched in 708 ms and retained the original 30-minute
Career target, preferred 19:00–21:00 window, and denied notification permission
(phase1-upgrade-ui.xml). This run was online. User has been given exact taps for
an offline sequence preview/save review. No release APK or Phase 2 work started.

### Phase 1 gate closed — 2026-10-01

User confirmed the offline sequence preview/save check and supplied screenshots
showing the expected ten dates and saved commitment detail. Phase 1 passes within
the boundaries in phase1-checklist.md. Full V1 acceptance remains outstanding.
User requests new chats for subsequent phases, functionality first, and a later
visual redesign using Pinterest references. Basic readability is needed during
Phase 2; visual polish belongs before final Phase 7 acceptance/release checks.
No Phase 2 implementation started in this chat. See HANDOFF.md at the project root.

## Phase 2 gate closed - report finalized 2026-10-02

Phase 2 is complete. See `phase2-checklist.md` for scope and limitations.

- Final build: `phase2-final-build.txt`, successful debug app/test builds, host
  tests and lint. 17 host tests pass; lint has 0 errors and 13 existing notices.
- Final physical-phone suite: `phase2-device-suite-final.txt`, 28/28 pass in
  29.156 seconds. Includes execution UI/storage, fixed-event conflict handling,
  late completion, interval restart, undo, and Phase 0/1 regressions.
- Original-data comparison passes for 79 pre-update rows across eight tables.
  Schema stays v2; app updated in place to 0.3.0-phase2. No uninstall/reset.
  Snapshot is private on the phone; no personal database was exported to this PC.
- Testing used Samsung SM-A556E / Android 16 / API 36, phone user 0, with Flight
  mode on, Wi-Fi disabled and notifications denied. No emulator or release APK.
- Initial UI failures and retry remain recorded. Missing Review-tab wiring and
  test interactions/expected text were corrected before the full passing suite.
- Phone disconnected after tests. On 2026-10-02 Windows/ADB still did not see it
  after the user reported reconnecting, so an extra cold-launch screenshot was
  not obtained. This is separate from the completed automated phone UI gate.
- Basic readability improved; full visual redesign deferred. Phase 3 not started.
  `HANDOFF.md` is updated for a separate Phase 3 chat.

## Phase 4 gate closed — 2026-10-02

Phase 4 is complete; AT-22–25 pass within `phase4-checklist.md` coverage.
Debug 0.5.0-phase4 (code 5), Room v4, installed in place with explicit 3→4
migration. All 221 original rows across nine tables passed preservation; all
phone-private snapshots remain. No personal database export, uninstall or reset.

25 host tests and 54 distinct physical-phone tests pass across recorded runs.
Final debug app/test builds and lint pass (0 errors, 14 existing notices).
Real offline background time >5 minutes counted; five paused minutes added zero.
Same-boot process loss required confirmation and excluded review time. Owner reboot
74→75 recovered exactly 56,508 ms from the checkpoint; Resume/Stop added 5,656 ms.
Owner confirmed readability and recovery. Rescue five-to-thirty-minute behavior
uses controlled-clock tests, not a real 30-minute soak.

Synthetic timer fixture removed; normal app relaunched. Final Flight mode off,
Wi-Fi on, notifications denied. HANDOFF.md updated. Stop after the Phase 4 report;
Phase 5 not started and no release APK produced. Full visual redesign stays after
Phase 6, when Pinterest references will be requested.
