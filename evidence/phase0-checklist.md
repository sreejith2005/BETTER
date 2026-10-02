# Phase 0 — foundation gate

Status: passed, 2026-10-01. Evidence is linked from progress.md.

## Automated host gate

- [x] Debug app and instrumentation APK compile.
- [x] Operational-day boundary tests (including DST day) pass.
- [x] Invalid time/zone/window validation tests pass.
- [x] Android lint passes with no errors (13 dependency-update notices retained).
- [x] Merged manifest has no network permission or analytics dependency.

## Physical-device gate (Samsung SM-A556E, API 36)

- [x] USB authorized; adb reports `device`.
- [x] User enabled Flight mode; adb confirms value 1.
- [x] `cmd wifi status` confirms Wi-Fi disabled.
- [x] Exported v1 Room schema opens; persisted setup survives reopen.
- [x] Repeating setup does not duplicate Career standard or audit record.
- [x] Invalid setup writes no partial data.
- [x] Compose test asserts saved state and visible Career 0/30 + navigation.
- [x] Fresh normal app launch completes onboarding offline.
- [x] Notifications absent/revoked: automated setup skipped permission; user initially
  allowed then revoked in Settings. Today remains usable. First-prompt denial itself
  was not the user's manual path.
- [x] Force-stop/relaunch retains setup and 30-minute standard.
- [x] User reviews Today / Plan / Review / Settings on the phone.

No upgrade migration exists before schema v2. The v1 schema-open/reopen test is
the initial migration harness, not evidence of a v1→v2 migration.

AT-01 is the Phase 0 acceptance target. AT-30 is a foundation-only check here;
its scheduling, timer and archive scenarios remain pending their phases.
