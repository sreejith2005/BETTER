# BETTER — build specification package

**Version:** 1.0 · **Date:** 2026-09-30 · **Status:** V1 build baseline

BETTER is a single-user, Android-first, local-first personal execution system. This package converts the prior product ideation into a buildable first release. The documents are the source of truth for V1. New ideas go into a backlog until V1 passes its acceptance tests.

## Read order

1. [01 — Master PRD](01-master-prd.md): purpose, principles, end-state direction.
2. [02 — V1 scope](02-v1-scope.md): frozen requirements and explicit exclusions. **Use this as the V1 feature contract.**
3. [03 — UX and flows](03-ux-flows.md): screens, states, copy, interaction rules.
4. [04 — Architecture and data model](04-architecture-data.md): stack, storage, entities, state transitions.
5. [05 — Scheduling and reminders](05-scheduling-reminders.md): deterministic recurrence, modes, overrides, delivery.
6. [06 — Roadmap](06-implementation-roadmap.md): build order and gates.
7. [07 — Acceptance tests](07-acceptance-tests.md): concrete release checks.
8. [08 — Android research](08-android-research.md): platform facts, permission behavior, sources.

## Decision hierarchy

For V1 implementation: `02` and `05` define behavior; `04` defines persistence and architecture; `03` defines presentation; `07` verifies the result; `01` guides ambiguous product choices. If documents conflict, record the conflict and resolve it in these files before changing code. An example or possible future feature in `01` does not expand V1 scope.

## Build instruction to give Codex

> Build BETTER V1 from this package, phase by phase in `06-implementation-roadmap.md`. Treat `02-v1-scope.md` and `05-scheduling-reminders.md` as the behavior contract. Run the relevant tests in `07-acceptance-tests.md` after each phase. Do not add later features to V1. Record assumptions and platform limitations. Keep all personal data on device unless the user explicitly enables export.

## Change control

During implementation, write new ideas to a `BACKLOG.md` in the codebase. Fix contradictions or defects in these specifications with a dated decision note. Promote a feature into V1 only by updating its requirement, UX flow, data impact, and acceptance test together. Versioned routines and profiles protect historical records from later edits.

## Important interpretation

“Strict” means direct, factual feedback and useful follow-up. It never means shame, false claims of verification, infinite notifications, or pretending Android can force behavior. The app records what was scheduled and what the user reported or timed. Medical schedules contain only user-entered or clinician-provided instructions; BETTER never computes doses or clinical advice.
