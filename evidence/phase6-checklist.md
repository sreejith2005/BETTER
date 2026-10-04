# Phase 6 report — Attention and data ownership

Date: 2026-10-04. Phase 6 implementation and phone gate are complete within the
coverage and limits below. Scope is Phase 6 only, AT-26–30. No Phase 7, full redesign
or release APK is included. AT-30's packet/network-log subcheck was not performed;
offline operation and local-only baseline were verified as described below.

## Implementation and migration

- Installed in place: `dev.better.app`, debug `0.7.0-phase6`, versionCode 7.
  Baseline was verified as debug `0.6.0-phase5`, Room 5 before changing it.
- Room 6 uses explicit migration 5→6, adding `attention_settings` and
  `attention_attempts`. Existing domain tables and migration paths remain intact.
  There is no destructive migration, uninstall or storage reset.
- Attention requires both owner-granted Android Usage Access and saved opt-in.
  Only the selected package is retained for calculation. Raw events are discarded.
  Strict foreground resume/pause pairs are clipped to the requested interval;
  a bounded 24-hour lookback reconstructs sessions crossing its start. Missing,
  ambiguous, locked, denied or revoked data is unavailable, never assumed zero.
  The displayed total includes observation time and selected package; observations
  older than two minutes cannot trigger an intervention.
- Editable eligible, office and commute windows, threshold, and disabled mode
  titles govern intervention. Office/commute usage is excluded from eligibility.
  Quiet hours and Career complete/running suppress it. Durable claims enforce one
  prompt per window, a 60-minute cooldown and at most two per operational day.
  Notifications offer the existing Rescue confirmation; they do not start work.
- Versioned JSON uses archive format 1 / Room 6, Android CreateDocument/OpenDocument,
  a table-count preview and explicit replacement confirmation. Validation checks
  typed fields, keys, references, scheduling and adaptive data in temporary phone
  storage before a single replacement transaction. Corrupt/incompatible archives,
  stale previews and unfinished timers are rejected. Maximum archive size: 20 MiB.
  Restoring is replacement, not merge. Permissions are not restored; future alerts
  are rebuilt and old platform notification identities are not imported.
- All 15 domain tables are archived, including settings/theme, versions, overrides,
  reminder evidence, focus history, adaptive records and routine runs. External
  JSON is readable and remains under the owner's document-provider control.
- Privacy controls explain optional access, local storage and external archives.
  Deletion requires an exact phrase and touches domain rows only, never preservation
  files. The app has no INTERNET, broad package inventory or overlay permission,
  automatic cloud backup, account or analytics dependency.
- Attention/archive controls were moved to the top of Settings to address the
  owner's difficulty finding the total. Actual phone screenshot confirms readable
  total, timestamp, package and Career comparison. Full visual work is deferred.

## Acceptance evidence

| Gate | Result and evidence type |
|---|---|
| AT-26 | Initial actual denied state passed with null total; actual granted adapter tests passed and the installed UI displayed `Instagram 336 min 46 sec`, `Observed 2026-10-04T17:39:08.205Z`, package `com.instagram.android`. Owner then revoked access and confirmed Unavailable; UI XML independently shows `Unavailable · Usage Access denied or revoked`, with selected package retained, and AppOps reports `ignore`. These are observed Android event totals, not independent proof of attention. |
| AT-27 | Automated controlled-clock tests on the physical phone pass office exclusions, threshold, per-window claim, cooldown, daily cap, quiet hours, disabled mode and Career complete/running suppression. Null, stale and wrong-package observations do not post. Host tests cover clipping/overnight intervals and incomplete boundaries. Actual daily notification delivery/60-minute soak is not claimed. |
| AT-28 | Physical-phone UI test uses the production detail/history UI: exact user-entered instruction text remains intact after irregular scheduling and a missed occurrence; Unconfirmed and record/review language remain. Archive equality also checks leading/trailing whitespace and punctuation. No generated medical advice was added. |
| AT-29 | Owner completed the actual offline Android picker export → synthetic delete → import preview → restore and confirmed “PASS … Every table matches.” Device marker is `2026-10-04T12:57:38.552506Z`. Final rich automated fixture passed with all 15 tables nonempty, exact table equality and next-ten previews, corrupt/incompatible rejection without mutation, and stale-preview/active-timer refusal. |
| AT-30 | Actual Flight mode on/Wi-Fi off recorded during the synthetic picker check. Offline physical-phone regression exercised cold activity launches, scheduling, timer and review. 66/67 passed; only an obsolete Foundation ban on the newly authorized Usage Access permission failed and was corrected. Manifest/dependency checks establish no required app server access or analytics SDK. No packet capture was taken; network-log coverage is not claimed. |

## Data preservation

The personal database never left the phone. A new `files/phase6-preservation/`
DB/WAL/SHM snapshot was created once before the update. Existing Phase 2–5 snapshots
were not overwritten. Initial comparison passed **239 original rows across 13
tables**. Live clock reconciliation may advance open occurrence states or expired
pending reminders. A pending reminder became BLOCKED while notification permission
remained denied during this session. The final comparator permits that only for an
elapsed planned alert, notifications disabled, exact blocked reason, and unchanged
null attempted/posted/observed timestamps; every other original field stays exact.
The established expired PLANNED→MISSED allowance also remains.

AT-29 used `phase6-ownership-probe.db` and isolated test databases, never the live
database. The synthetic Downloads archive may remain on the phone. All 15 snapshot
file hashes match the authoritative pre-update list (`phase6-snapshot-hashes-verified-before.txt`
versus `phase6-snapshot-hashes-final.txt`; empty `phase6-snapshot-comparison.txt`).
Final row comparison passed again: **239/239 original rows across 13 tables**.
`phase6-verified-observations.txt` records the final summary and revoked null total.
Its inherited summary text mentions MISSED only; the tested comparator also includes
the narrowly checked BLOCKED transition described above.

## Build and regression results

- Host: **35/35 pass**, `phase6-host-tests.json`.
- Debug app/test builds pass: `phase6-stable-build.txt` and
  `phase6-final-test-build.txt`. Lint: **0 errors, 14 existing warnings**.
- Initial phone subset: **9/9 pass**, `phase6-initial-device.txt`.
- Earlier combined run: **75/77 pass**, `phase6-device-final.txt`. The known
  profile UI timeout reproduced, then its unchanged isolated suite passed **3/3**
  (`phase6-adaptive-ui-retry.txt`). The other failure was the legitimate elapsed
  PLANNED→BLOCKED transition described above; the comparator was narrowly corrected.
- Final combined physical-phone run: **77/77 pass**, no skipped permission check,
  `phase6-device-verified.txt` (75.601 seconds). It includes Foundation, scheduling
  storage/editor, execution storage/UI, reminders storage/UI, focus storage,
  Career/Rescue UI, adaptive storage/UI, onboarding, ownership storage, health UI,
  actual revoked Usage Access/privacy baseline, and live preservation.
- Earlier diagnostic and failed build/test logs are retained, not treated as passes.
  The final run includes the strengthened all-table fixture and comparator; the
  app implementation was unchanged after its successful stable build.

## Phone evidence and limits

Phone: Samsung SM-A556E, Android 16/API 36, USB serial `RZCY40K24SD`, user 0.
Permission/picker instructions: `phase6-phone-steps.md`. Actual granted evidence:
`phase6-usage-home-observation.txt`, `phase6-usage-home-summary.txt`,
`phase6-attention-visible.xml` and `.png`. Offline picker evidence:
`phase6-picker-result.txt`, `phase6-offline-state.txt`, plus owner's confirmation.
Revocation: `phase6-revoked-ui.xml`, `phase6-revoked-appops.txt`,
`phase6-verified-observations.txt`. Final debug APK hash:
`phase6-debug-apk-sha256.txt`. Normal BETTER was relaunched after instrumentation.
Final observed device state: Flight mode off, Wi-Fi on, notification permission
denied, Usage Access revoked. Owner confirmed Opt in unticked and saved after tests.

Usage Access does not guarantee complete event data. An unclosed foreground session
or ambiguous event stream remains unavailable. Checks run on foreground refresh and
best-effort background work; no instantaneous intervention or OEM delivery guarantee
is made. POSTED records an OS post request, not a user observation. New Attention
notification appearance/tap was not manually observed. Real daily cooldown/cap soak,
multiple phone models, all font scales, battery-saver behavior and a fresh reboot
were not tested in this phase. Existing reminder/recovery regressions are automated.

The known intermittent profile UI test timeout remains documented. External document
providers can be cloud-backed; offline picker evidence used local Downloads. There
was no packet-level network trace. Release hardening belongs to the later phase.
