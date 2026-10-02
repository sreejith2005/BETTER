# 06 — Implementation roadmap and release gates

Build in slices. At each gate, run the listed tests and demonstrate the feature on an Android emulator/device. Keep a short decision log for changes to the spec.

| Phase | Deliverable | Gate |
|---|---|---|
| 0. Project foundation | Native Compose project, navigation, Room/DataStore, migration setup, domain clock abstraction, test harness, local-only privacy baseline | App launches offline; schema creates; no account/network dependence; AT-01, AT-30 |
| 1. Time and scheduling core | Rule parser/validator, timezone resolver, occurrence generator, next-ten preview, versioned commitment edit scopes | Pure deterministic tests for sequence, DST, late completion, version changes; AT-03, AT-05–12 |
| 2. Execution UI | Today, commitment editor/detail, statuses, daily review, manual fixed events, conflict flagging | Full add→preview→due→complete/skip/move flow; AT-02–04, AT-31 |
| 3. Reminders | Policy ladder, channels, permission flow, alarm adapters, reboot/time-change reconciliation, quiet hours | Real device/emulator scenarios with permission granted/denied and reboot; AT-13–15 |
| 4. Career and Rescue | Seeded 30-minute commitment, focus segments, interruption recovery, activity label and optional application log | Timer accuracy and process-death tests; AT-22–25 |
| 5. Routines and adaptive day | Routine versions/runs, Schedule Profiles, Life Modes, one-day override, overlap preview | Historical integrity, precedence, and suppressed-not-missed checks; AT-16–21 |
| 6. Attention and data ownership | Opt-in Usage Access, contextual intervention, export/import, privacy controls | Permission revocation/cap tests and archive round trip; AT-26–30 |
| 7. Release hardening | Accessibility, battery/notification limits, migrations, error states, offline soak, backup recovery | All AT cases pass; no unresolved data-loss or false-reminder issues |

## Build review rules

At the end of each phase provide: implementation summary, schema/migration impact, passed tests, device/emulator evidence, remaining known limits, and next phase. Do not start a later integration merely because the PRD mentions it. Fix a failing gate before layering on dependent features.

## Definition of V1 ready

All acceptance tests in `07` pass on the supported Android test matrix, including denied permissions and a fresh install. Archive export/import round trip preserves data. Reboot and timezone tests do not duplicate notifications or rewrite history. App works with airplane mode on. Any timing limitations caused by Android are visible to the user. No unapproved medical inference or gamification appears.
