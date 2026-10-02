# 07 — Acceptance criteria and test scenarios

Use fixed clock and timezone in domain tests. Pair them with UI and Android integration checks. `AT` IDs map to requirements in `02`. Every test must assert stored state and visible state, not only a successful button click.

| ID | Scenario and expected result |
|---|---|
| AT-01 | Fresh install in airplane mode completes onboarding without account; 30-minute Career standard appears; denying notifications does not block Today. |
| AT-02 | With one due, one upcoming, one completed item, Today shows correct next action, Career remaining, profile/mode, and distinct states. Empty Today gives a create action. |
| AT-03 | Create one-off commitment with title, window, source text, reminder level; detail and next-ten preview match saved values. Invalid interval is rejected. |
| AT-04 | Complete, move, skip with reason, and cancel separate occurrences. History records actions/times; repeated notification action does not duplicate completion. |
| AT-05 | Daily and selected-weekday weekly recurrence expands across a month with expected dates; no duplicate occurrence after reconciliation. |
| AT-06 | Sequence anchor 1 Oct 2026, offsets +3d,+2d,+1d, tail +2d yields 1,4,6,7,9,11,13,15,17,19 Oct. Preview and stored dates match. |
| AT-07 | One-off “follow-up one calendar month after 31 Jan” resolves to last day of February, with visible resolved date; no hidden clinical assumption. |
| AT-08 | Sequence without tail ends after final offset; invalid zero/negative interval rejected; horizon generation does not hang. |
| AT-09 | Exact, windowed, and anytime items transition to due/overdue at defined boundaries; 02:00 maps to prior operational day with 04:00 boundary. |
| AT-10 | On DST gap/overlap in a chosen IANA zone, preview flags resolution and stored UTC instant matches selected local-time policy; future timezone switch changes only unacted floating items. |
| AT-11 | Edit one occurrence changes only it. “This and future” creates a new version; past completed occurrence retains original text/time. |
| AT-12 | Calendar-anchored Monday item completed Tuesday leaves next planned date unchanged. Completion-anchored interval starts from actual completion; skip asks for restart choice. |
| AT-13 | Each reminder level generates only its defined offsets and cap; quiet-hour alerts coalesce and are marked delayed. |
| AT-14 | Completing from notification cancels remaining steps. Tapping a stale alert opens resolved history. Reboot/reconcile does not repost old ladder steps. |
| AT-15 | Deny POST_NOTIFICATIONS and exact access; app still shows in-app due state and best-effort notice. Grant later and future alerts are scheduled. |
| AT-16 | Create three-step routine, run it, skip middle step, complete rest; stored run reflects exact results and order. |
| AT-17 | Edit routine step name/order for future runs; previous run still displays old version. |
| AT-18 | Create Workday and Workday + Run profiles; assign weekdays; timeline uses the chosen profile and flags overlapping fixed blocks. |
| AT-19 | Activate new profile next Monday; prior days stay on old version and unrelated commitments remain. |
| AT-20 | Activate Travel for three dates; preview lists moved/kept/suppressed items; expiry restores baseline. Overlapping mode requires explicit resolution. |
| AT-21 | “Keep essential only” one-day override suppresses ordinary items with override reason; review does not count them as missed. Essential item remains unless explicitly changed. |
| AT-22 | Career default is 30 minutes; changing future target to 40 preserves prior 30-minute days and sets future target to 40. |
| AT-23 | Start, switch to another app for 5 min, return: timer retains active elapsed. Pause 5 min, resume, stop: paused interval is excluded. Same-boot process restart reconstructs monotonic elapsed for user confirmation; reboot counts only through last checkpoint and offers recovery. |
| AT-24 | Rescue runs five active minutes, then Continue; total session reaches 30 without double counting. Stopping at five leaves 25 minutes. |
| AT-25 | Timer session has selected activity and evidence label “timed.” Optional application log stores company/role; timer alone never displays “application submitted.” |
| AT-26 | Without Usage Access, Attention shows unavailable and no zero-minute claim; with access, selected Instagram package usage total is displayed with observation time. Revocation reverts to unavailable. |
| AT-27 | Office-hour Instagram use triggers no default intervention. After-work threshold with Career incomplete triggers at most 2/day and obeys 60-minute cooldown; Career complete/running suppresses it. |
| AT-28 | Add health instruction exactly as entered, schedule irregular dates, then miss one. UI says unconfirmed and offers record/review; no dose adjustment or treatment advice is generated. |
| AT-29 | Export, delete app data, import archive: profiles, versions, history, and next-ten previews match. Corrupt/incompatible archive leaves current data untouched. |
| AT-30 | Airplane-mode cold start, scheduling, timer, review, and archive all work; network logs show no required server call or analytics SDK. |
| AT-31 | Review counts completed late, skipped, missed, and override-suppressed separately; current day is labelled in progress. No XP or composite worth score. |

## Android test matrix

Run on the oldest supported Android version, Android 13, Android 14+, and one current target-version emulator/device. Include notification grant/deny/revoke; exact-alarm access absent/present if implemented; Usage Access grant/revoke; battery saver; reboot; app force-stop/relaunch; device timezone and wall-clock changes; DST using a test timezone; screen locked; airplane mode; font scale 200%; dark mode. Android force-stop can prevent background delivery until relaunch; show reconciliation after launch rather than claiming guaranteed delivery.

## Release evidence

Save automated domain-test results, Room migration results, UI test results, and a short manual device checklist. For notification tests capture planned time, actual post time, permission state, and device power state. A passing domain scheduler test alone does not prove Android delivery timing.
