# Phase 1 — scheduling gate

Status: passed on 2026-10-01. Phase 2 may begin in a new chat at the user's request.

## Implemented

- Pure daily, selected-weekday weekly, calendar/completion intervals, finite
  sequence + optional tail, and exact/window/anytime due definitions.
- Calendar-month clamping without monthly drift; separate one-month follow-up resolver.
- IANA zone modes, explicit absolute one-off instant, DST resolution and acknowledgement.
- Next-ten preview and rolling 45-day generation share the same domain engine.
- Immutable content versions; future edits retain protected history; stable occurrence IDs.
- One-occurrence override, archive/restore, exact instruction/source text retention.
- Room schema v2 with explicit 1→2 migration preserving v1 setup and Career target.
- Plan editor/detail; editing is blocked while a preview/save is in flight;
  saving rejects a stale dated preview.

## Gate evidence

- [x] 15 host tests pass (12 scheduling + 3 foundation), 0 failures/errors.
- [x] Debug app/test APK compile; lint has 0 errors and 13 version-update notices.
- [x] Initial 8 physical-device storage tests pass (phase1-storage-device.txt).
- [x] Final 9 storage tests including stale-preview rejection pass.
- [x] Compose add → invalid interval → window preview → save → detail test passes.
- [x] Foundation UI/storage regression suite passes with schema v2.
- [x] Actual user database upgrades without resetting the Phase 0 profile/standard.
- [x] User inspects a saved October sequence preview on the phone.

Final automated evidence: phase1-device-suite-retry.txt, 14/14 tests in 13.735s.
Initial run failed both UI activity launches; a Back gesture was logged during
launch. Isolated editor retry and full suite then passed without code changes.
Retain phase1-device-suite.txt as the failed-run evidence. The cause is suspected
input interference, not proven. Actual app cold launch took 708 ms; captured
phase1-upgrade-ui.xml retains 0/30 minutes, 19:00–21:00 and notifications off.
These automated runs were online. The user subsequently confirmed completing the
manual offline preview/save steps and supplied screenshots of the editor preview
and saved detail. The preview shows October 1, 4, 6, 7, 9, 11, 13, 15, 17, 19.
Offline state is user-confirmed; it was not re-measured through ADB in this turn.
Screenshots were supplied in the chat and are not stored in this evidence folder.

Readability issues remain: raw timestamps, enum-style labels, a dense editor and
an overly long occurrence list. Address basic readability during Phase 2; the
user wants a reference-led visual redesign after the functional phases.

## Boundaries

AT-03 and AT-05–12 are the phase targets. Completion/skip execution buttons,
late-completion review and interactive skip-restart choices belong to Phase 2;
Phase 1 verifies their scheduling semantics through pure and repository tests.
Reminder policy is stored but Android alert delivery is Phase 3. Career's target
remains 30 minutes; effective-dated target editing/timer execution is Phase 4.
This is a debug scheduling preview, not a V1 release.
