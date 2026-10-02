# Phase 3 — reminder report

2026-10-02. **Phase 3 complete; AT-13–15 pass within the coverage below.**
Phase 4 has not started. No release APK was produced.

## Implemented

- None, Light, Standard, Persistent and configured Escalating policies, bounded
  attempts, durable step identity and per-occurrence/day caps.
- Quiet hours, delayed/coalesced alerts, explicit per-version exceptions and
  cancellation when occurrences resolve, move, are archived or close.
- Commitments, Career and User-entered health reminder channels; contextual
  permission request and visible in-app due state with permission denied.
- Next-batch inexact alarm, WorkManager handoff/safety checks, durable recovery
  for boot/version/timezone/clock changes that skips past steps.
- Exact occurrence links and confirmed Complete/Move/Skip actions; stale links
  show current history without repeating actions.
- Distinct planned, claimed, posted, opened, blocked, missed, coalesced, cancelled
  and failed evidence. Uncertain claimed attempts are not retried.
- Lock-screen title privacy and readable settings. No full visual redesign.

## Data preservation

Debug `0.4.0-phase3` (code 4) was installed in place, retaining `dev.better.app`.
Explicit Room 2→3 migration adds the ledger and default-false
`commitment_versions.criticalReminder`; migration 1→2 remains registered.
No uninstall, storage reset or destructive migration was used.

Phone-private pre-update DB/WAL/SHM snapshot hashes match originals
(`phase3-snapshot-hashes.txt`). All **80 original rows across eight tables** passed
post-upgrade comparison (`phase3-data-preservation.txt`). Phase 2/3 snapshots
remain on the phone. No personal database was exported to this computer.

## Automated verification

| Check | Result | Evidence |
|---|---|---|
| Host tests | 20 passed | phase3-host-tests.json |
| Final debug build and lint | Passed; 0 errors, 14 dependency/version notices | phase3-live-fix-build.txt |
| Storage/migration/foundation/scheduling/execution/preservation | 32 passed | phase3-storage-device.txt |
| Onboarding/editor/execution/reminder UI | 6 passed | phase3-ui-device-retry.txt |
| After timing fix: reminder storage/UI | 11 passed, including one new regression | phase3-live-fix-device.txt |

**39 distinct automated phone tests** passed across overlapping runs on Samsung
SM-A556E / Android 16 / API 36. Manual probe helpers are excluded. Debug artifact
hash: `phase3-debug-apk-sha256.txt`.

Initial UI failures were an obsolete Phase 2 text expectation and an assertion
before asynchronous cancellation finished. Expectations/waiting were corrected;
initial logs remain. Live testing found a real clock-sampling bug: a second
`now()` call made ordinary eligibility appear delayed by milliseconds. The engine
now uses one captured instant. An advancing-clock regression and the actual
notification below verify the correction.

## Physical-phone reminder gate

Live commitments used a separate debug database and production adapters.
All times below are IST on 2026-10-02.

### AT-13: policy and quiet hours

Host/device tests verify defined offsets/caps, quiet-hour delay/coalescing, day
closure, cancellation and DST behavior. Every policy was not separately waited
through as real OS notifications; that coverage is deterministic.

### AT-14: actions, stale links and reboot

- Actual alert planned **22:11:06.587**, posted **22:12:26.682** (80.095 seconds
  late), opened **22:12:32.806**, completed **22:12:36.552**.
- Owner confirmed seeing it and tapping Complete → Confirm complete. Exactly
  one completion action was stored; both follow-ups are CANCELLED.
- Automated UI/storage tests cover stale links and idempotent actions.
- First reboot inspection used instrumentation, which force-stopped BETTER and
  invalidated background recovery evidence. Owner repeated Restart.
- Boot count **74**: with BETTER closed and no instrumentation inspection,
  recovery ran automatically at **22:24:55.714**. Old steps became MISSED without
  posts, completed follow-ups stayed cancelled and future alarms were restored.
  Recovery took several minutes; immediate restart delivery is not claimed.
- Evidence: `phase3-reboot-verified.json`, `phase3-reboot-restored-alarms.txt`.
  Valid second reboot inspection used read-only synthetic fixture snapshots.

### AT-15: permission denied and granted

- Flight mode on, Wi-Fi disabled, notifications denied: fixture became OVERDUE,
  alert BLOCKED, posted/opened timestamps empty, no OS notification active. UI
  showed notifications off and blocked/missed status. Evidence:
  `phase3-denied-visible.txt` and probe denied logs.
- Owner granted notifications in Settings. Future delivery/completion above
  succeeded. Final granted check was online; offline coverage is the denied case.
- No exact-alarm permission is declared or requested. The implemented path uses
  inexact alarms, a best-effort notice and in-app due state.

## Restoration and cleanup

Owner restored notifications off. ADB verified permission denied, Flight mode off
and Wi-Fi enabled. Cleanup cancelled the live fixture's notifications/alarm and
deleted only `phase3-reminder-probe.db`; absence was verified. Normal BETTER was
relaunched after instrumentation. Personal storage and preservation snapshots
remain. See `phase3-fixture-cleanup.txt` and `phase3-final-state.txt`.
Exact manual instructions: `phase3-phone-steps.md`.

## Limits and stop point

Android timing is best effort, demonstrated by delayed posting and recovery.
Posting alone is not observation; the completed alert also has owner confirmation.
Optional precise alarms are not implemented. Phone-wide time/timezone changes,
battery saver, full lock-screen/privacy/accessibility coverage and other Android
versions were not manually tested. Deterministic DST tests do not replace that
Phase 7 matrix.

Stop after this report. Career/Rescue is Phase 4, adaptive day Phase 5, and
Attention/data ownership Phase 6. Request Pinterest references after Phase 6
before full visual redesign. No release APK.
