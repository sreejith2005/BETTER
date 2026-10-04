# Phase 5 — routines and adaptive day report

Phone gate recorded 2026-10-03; report finalized 2026-10-04.
**Phase 5 complete. AT-16–21 pass within the coverage below.** No release APK.

## Implementation

- Ordered routine creation, attachment to scheduled commitments, effective-dated
  versions, saved runs and per-step completed/skipped results with timestamped
  self-report actions. A reopened run uses its original step text/order.
- Schedule Profile creation, duplication, version history, blocks, weekday and
  one-off assignments. Linked blocks reuse stable occurrences; unrelated work
  remains. Proposed standalone blocks and fixed/flexible overlaps are visible.
- Normal, Travel, Vacation, Sick, Late Night, Recovery Day, High Focus and Custom
  mode configuration with dates, editable per-item rules, overlap warning and
  explicit latest-mode confirmation. Expiry restores the applicable base/profile.
- One-day Keep essential only and per-item Keep/Move/Skip, with a required reason.
  Suppressed items remain distinct from missed in Today/Review and do not generate
  reminder attempts. Unstarted suppression can be replaced before its due time.
- Shared preview/reconciliation modifier order: base → profile → latest active
  mode → day override → occurrence override → recorded state. Previews show up to
  45 days and reject stale saves. There is no automatic multi-item reflow.
- Profile/mode/day provenance and block interval properties are snapshotted on
  occurrences. Routine start protects dependent execution history from earlier
  move undo; execution JSON remains compatible with earlier action records.
- Basic readability pass: separated preview cards, plain state labels, item
  dividers, stronger dark-theme button contrast and editor scroll reset.

Primary files: `domain/AdaptiveDay.kt`, `data/AdaptiveData.kt`,
`data/AdaptiveRepository.kt`, `feature/AdaptiveScreen.kt`, and the existing
scheduling/execution repositories and screens. Reminder and Career engines
remain in place; adaptive saves use the existing reminder reconciliation hook.

## Migration and preservation

Debug `0.6.0-phase5`, versionCode 6, app ID `dev.better.app`, Room v5.
Explicit migration 4→5 adds `adaptive_records`, `routine_runs`, and five nullable
occurrence fields for provenance and effective block duration/fixed state.
Existing columns and records remain. Earlier migrations are still registered.
No destructive migration, uninstall, storage reset, or personal database export.

The pre-update database/WAL/SHM snapshot remains phone-private under
`files/phase5-preservation/`. Initial hashes matched the source files.
Phase 2/3/4 snapshots remain untouched. All **12 snapshot files** are byte-identical
to their initial hashes. See `phase5-snapshot-hashes.txt`,
`phase5-snapshot-hashes-final.txt`, and `phase5-snapshot-comparison.txt`.

The immediate post-update comparison passed **229 original rows across 11
tables**, including reminder, Career/session and segment tables. All original
content and evidence fields matched; open occurrence status may advance with time.
See `phase5-storage-device.txt` and `phase5-data-preservation.txt`.

A later recheck after the owner's offline review found a legitimate original
reminder transition `PLANNED → MISSED` after its scheduled time. The strict older
comparison failed on that lifecycle field (`phase5-preservation-recheck.txt`).
The final comparator permits only that elapsed transition and its detail text;
all other original reminder fields, including identity/timing/delivery evidence,
still require exact equality. The final comparison passed all 229 original rows
(`phase5-data-preservation-final.txt`). It reads a temporary phone-private copy of the
snapshot to keep the original snapshot's SQLite shared-memory bytes unchanged.

## Acceptance evidence

| Case | Coverage |
|---|---|
| AT-16 | Create/attach a three-step routine, complete first/last, skip middle; visible checklist and stored order/results; reopening retains run |
| AT-17 | Future step name/order edit; previous run retains original version, labels and results; future run selects new version |
| AT-18 | Workday and Workday + Run profiles; linked occurrence identity retained; weekday/one-off assignments and explicit fixed-overlap acknowledgement |
| AT-19 | New version effective Monday; prior due record unchanged; unrelated commitments retained; historical version selection checked |
| AT-20 | Three-date Travel preview includes move/keep/suppress; overlapping activation rejected without acknowledgement; latest wins on shared dates; expiry restores baseline |
| AT-21 | Ordinary items suppressed with reason; essential defaults retained and explicit changes supported; day closure leaves suppression separate from missed, including visible Review |

Additional checks cover stale-preview rejection, retroactive-edit rejection,
modifier precedence, completed/occurrence-override protection, reminder cancellation
and restoration, migration preservation, fixed events, overnight windows,
execution undo with profile snapshots and completion-anchored suppression stability.

## Test results

| Check | Final result | Evidence |
|---|---|---|
| Host tests | 31 passed, 0 failed | phase5-host-tests.json |
| Full physical-phone suite | 68/68 passed in 65.353 seconds | phase5-device-verified.txt |
| Adaptive storage/migration | 11 passed within final suite | phase5-device-verified.txt |
| Adaptive UI | 3 passed within final suite | phase5-device-verified.txt |
| Earlier-phase regressions | 53 passed within final suite | phase5-device-verified.txt |
| Installed-data comparison | 1 passed; 229 original rows | phase5-data-preservation-final.txt |
| Debug app/test builds | Passed | phase5-verified-final-build.txt |
| Lint | 0 errors, 14 existing notices | phase5-verified-final-build.txt |
| Private snapshot hashes | All 12 files unchanged | phase5-snapshot-comparison.txt |

Initial runs passed 66 distinct phone tests before the additional edge-case tests.
Their evidence remains in `phase5-storage-device.txt`, `phase5-ui-device.txt` and
`phase5-regression-device.txt`. The first final suite passed 67/68, with a timeout
waiting for a profile-assignment overlap preview (`phase5-device-final.txt`).
That test passed in isolation (`phase5-profile-ui-retry.txt`), then the complete
68-test suite passed with no intervening code changes. The timeout's cause is
unproven; it is retained rather than attributed to user input or an app defect.

The initial build caught a syntax error in the new JSON codec; it was corrected.
Subsequent builds and lint passed. Source review then added focused fixes/tests
for overnight moves, fixed profile blocks, routine-dependent undo and reversible
completion-anchored suppression. Earlier outputs are retained honestly.

## Physical phone and owner review

Samsung SM-A556E, Android 16/API 36, phone user 0, USB connected. Automated phone
runs use separate synthetic databases, with notifications denied. The initial
automated runs were online; deterministic clocks exercise future dates, mode
expiry, day closure and version switching on the real phone.

The owner confirmed the day-override preview worked with Flight mode on and
Wi-Fi off, and said it was readable but could be improved. This offline check is
owner-confirmed; the offline network state was not sampled by ADB during the
preview. ADB subsequently confirmed Flight mode off and Wi-Fi on. The owner
cancelled the preview without saving a personal override. No synthetic routine or
profile was inserted into the personal plan. `phase5-owner-screen.png` records
the screen after that review; the small readability improvements followed it.
Exact instructions are in `phase5-phone-steps.md`.

The final installed UI was inspected after the readability changes. A preview-only
day override showed the due Career item retained, readable state/interval text,
and separate Confirm/Cancel controls (`phase5-preview-final.png`). The preview
was cancelled without saving. Normal Today then showed the original 30-minute
Career target, Start Career, Rescue and notifications-off notice
(`phase5-today-final.png`). Cold launch succeeded in 823 ms
(`phase5-final-launch.txt`); bringing its existing task forward completed the
screen inspection after the launcher/notification shade initially covered it.

Last verified phone state on 2026-10-03: Flight mode off, Wi-Fi on, notifications
denied (`phase5-final-state.txt`). Temporary phone screenshot/UI-dump files were
removed; evidence images remain in this workspace. Private preservation snapshots
and pre-existing files remain. No personal adaptive change was saved for testing.

## Known limits and stop point

- Mode expiry, Monday switches and historical boundaries use controlled-clock
  tests on the phone; no real three-day wait was performed.
- Policies change unstarted plan items. Already due, delivered, acted and resolved
  records retain their schedule snapshots. Use an explicit occurrence action for
  a due item; a later mode does not rewrite it.
- Moves stay within allowed windows. Fixed events and ambiguous DST proposals
  remain in place with an explanation; the occurrence editor handles explicit
  moves and DST choices. No health directions are inferred or altered.
- Standalone profile blocks are advisory intervals. A runnable routine block needs
  a scheduled commitment. Routine step completion and occurrence completion are
  separate explicit records.
- New versions use later effective dates. To reverse pending policies or
  assignments, preview a replacement; old snapshots remain in history.
- Android reminders remain best effort. This phase runs reminder/Career
  regressions; it does not repeat the earlier real reboot or five-minute soak.
- Wider Android versions, 200% font-scale/accessibility and battery-saver coverage
  remain Phase 7. Full visual redesign stays after Phase 6, then request Pinterest
  references. The owner's readability feedback is retained for that work.
- Stop after the Phase 5 report. Phase 6 is not started. No release APK.
