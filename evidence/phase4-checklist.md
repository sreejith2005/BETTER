# Phase 4 — Career/Rescue report

2026-10-02. **Phase 4 complete; AT-22–25 pass within the coverage below.**
Scope: Phase 4 only, AT-22–25 plus relevant regressions. No release APK.

## Implemented

- Today Career timer with deliberate-work confirmation, six activity labels,
  optional company/role, pause/resume/stop and current-day progress.
- Five-minute Rescue segment, explicit Continue/Stop choice, same-session
  continuation and no credit for waiting or paused intervals.
- Saved monotonic segments, process-owner and boot identity, explicit same-boot
  recovery review, checkpoint-only reboot recovery and durable action history.
- Operational-day allocation, frozen segment timezone/boundary, clock-change
  re-anchoring without extra time, and durable timed minimum milestones.
- Future-dated target settings retaining the seeded 30-minute standard and prior
  history; daily/seven-day Career Review with dated targets and timed evidence.
- Basic readable controls and text. Full visual redesign remains after Phase 6.

## Data preservation and schema

Debug `0.5.0-phase4` (versionCode 5) installed over `dev.better.app` with `-r`,
phone user 0. Room v4 adds `focus_sessions` and `focus_segments`; migration 3→4
is explicit and older migrations remain registered. Existing tables are unchanged.

Pre-update DB/WAL/SHM hashes matched phone-private copies in
`files/phase4-preservation/` (`phase4-snapshot-hashes.txt`). All **221 original
rows across nine tables** passed preservation comparison, including the reminder
ledger (`phase4-data-preservation.txt`). Phase 2/3 snapshots remain untouched.
No personal database was exported, no app was uninstalled, and no storage reset
or destructive migration was used.

## Automated evidence

| Check | Result | Evidence |
|---|---|---|
| Host tests | 25 passed, including 5 timer domain cases | phase4-host-tests.json |
| Career storage/UI | Final 15 passed (initial 13 also passed) | phase4-timer-device-final.txt |
| Phases 0–3 regressions | 38 passed | phase4-regression-device.txt |
| Installed data preservation | 1 passed; 221 original rows | phase4-data-preservation.txt |
| Final debug app/test build and host tests | Passed | phase4-verified-build.txt |
| Lint | Passed; 0 errors, 14 existing dependency/version notices | phase4-verified-build.txt |

**54 distinct physical-phone tests passed** across the recorded runs. Additional
final timer tests cover paused reboot and durable minimum milestones excluding
unconfirmed recovery time. APK hash: `phase4-debug-apk-sha256.txt`.

Phone: Samsung SM-A556E, Android 16/API 36, USB serial redacted.
The live elapsed-time gate uses Flight mode on, Wi-Fi off, notifications denied.
Automated storage/UI tests preceded that connectivity change.

## Acceptance coverage

- **AT-22:** Default 30; future 40; old days remain 30. Storage and visible
  settings tests pass. Retroactive/duplicate-date target changes are rejected.
- **AT-23:** Deterministic background/pause/resume allocation, process reopen,
  confirmation/rejection, repeated recovery, reboot with larger uptime, and wall
  time/timezone changes pass on the physical phone. Real background/pause and
  same-boot process-loss and owner reboot checks below pass.
- **AT-24:** Five-minute cap, stopping leaves 25, and Continue reaches 30 in one
  session pass with a controlled clock and UI tests on the physical phone.
  Five-to-thirty-minute Rescue coverage is accelerated; a real 30-minute session
  was not waited through.
- **AT-25:** Labels, deliberate-work confirmation, company/role persistence and
  visible self-reported application details pass. No submission is inferred.

## Live phone gate

Uses the production timer with a separate debug-only database,
`phase4-focus-probe.db`. Test controls never write synthetic sessions to the
personal plan. See `phase4-phone-steps.md` for exact owner instructions.

### Real background time and pause — passed

With Flight mode on, Wi-Fi off and notifications denied, Android Settings remained
in front for more than five minutes. Android suspended the fixture's background
ticks. On return, the same process reconstructed **368,630 ms** from monotonic
time; the adjacent clock sample differed by just **7 ms**. Pause persisted
**368,838 ms**, which remained exactly unchanged after five paused minutes.
Resume/Stop added **11,836 ms**, for **380,674 ms** saved total.

Evidence: `phase4-live-accuracy.txt`, `phase4-live-background-baseline.json`,
`phase4-live-background-before-return.json`, `phase4-live-inspect.json`,
`phase4-live-pause.json`, `phase4-live-five-minute-pause.json`,
`phase4-live-resume.json`, `phase4-live-stop.json`. The owner confirmed the paused
screen's text and controls were readable; `phase4-live-paused.png` also shows this.

### Real same-boot process loss — passed

Force-stopped the app during a new synthetic session, waited, then reopened it on
boot 74. Recovery kept the **20,798 ms** saved checkpoint and presented a
**33,714 ms** candidate. Confirmation after a review delay produced exactly
33,714 ms; Stop did not add review time. Evidence: `phase4-process-result.txt`,
`phase4-process-saved.json`, and the start/before-kill/inspect/confirm/stop JSONs.
The debug script used actual process death, not just an object reconstruction.

### Real phone reboot — passed

Owner tapped Pause → Resume, waited, then restarted and unlocked the phone.
Boot count changed **74 → 75**. Before opening BETTER or running instrumentation,
read the synthetic database without launching the app: saved segments totalled
**56,508 ms**. Opening the fixture showed **INTERRUPTED** and exactly 56,508 ms,
with no cross-boot time credited. The owner confirmed the recovery explanation,
tapped Resume, then Stop and save. Durable records show **62,164 ms** completed,
including only **5,656 ms** of new post-reboot active time.

Evidence: `phase4-reboot-checkpoint.json`, `phase4-reboot-recovered.json`,
`phase4-reboot-result.txt`, `phase4-reboot-owner-saved.json`, `phase4-owner-result.txt`.
The first screenshot caught the launcher; reopening brought the existing fixture
to the front. The recovery assertion used stored records and owner observation.
An initial PC JSON read used UTF-8 for a PowerShell UTF-16 file; correcting the
reader encoding allowed the same captured data to pass without repeating reboot.

## Restoration and stop point

The synthetic fixture database/WAL/SHM and its temporary phone JSON files were
removed after recording final evidence. Only Phase 4 synthetic files were removed;
the personal database and all three preservation folders remain. Absence of the
fixture is recorded in `phase4-fixture-cleanup.txt` (the expected missing-file
message verifies deletion).

Normal BETTER cold-launched successfully in 1,360 ms. `phase4-final-today.png`
shows the original 30-minute target and preferred 19:00–21:00 window, zero
synthetic minutes in the personal app, and notifications still denied. Flight mode
is off and Wi-Fi is on, matching the initial state (`phase4-final-state.txt`).
The owner confirmed readability and the final recovery/resume/save flow.

Stop after this Phase 4 report. `HANDOFF.md` and `progress.md` are updated.

## Known limits and boundaries

- Checkpoints run every five seconds only while Android permits execution.
  Cached-process suspension, process death or reboot can leave an older checkpoint.
  No five-second maximum loss or background scheduling guarantee is claimed.
- Timed minimum and manual schedule completion are distinct. Reaching the minimum
  does not silently change an occurrence or prove work/application submission.
- New effective target dates are append-only. Editing an existing future target
  date in place is not provided; use another future date.
- Physical time/timezone changes, broader Android versions, battery-saver and
  accessibility matrix remain Phase 7 work. Deterministic coverage is labelled.
- Phase 5 and Phase 6 have not started. Request Pinterest references after
  Phase 6 before full visual redesign. No release APK was produced.
