# 02 — V1 build scope and feature freeze

**Release definition:** An installable Android app that can run entirely offline on one device, create and execute flexible commitments, deliver best-effort reminders, preserve schedule history, and record daily Career Focus.

## 1. V1 functional requirements

| ID | Requirement | Acceptance reference |
|---|---|---|
| FR-CORE-001 | First launch creates a local profile, timezone, day boundary (default 04:00), and notification preference. No sign-in. | AT-01 |
| FR-CORE-002 | Today shows active profile/mode, next action, daily Career progress, due/overdue/upcoming items, and permission status. | AT-02 |
| FR-CORE-003 | User can add, edit, archive, and inspect a commitment with title, category, priority, schedule, duration, reminder policy, and optional source instructions. | AT-03 |
| FR-CORE-004 | Occurrences support Complete, Reschedule once or more per policy, Skip with reason, Cancel, and undo before irreversible local archival. History is retained. | AT-04 |
| FR-SCHED-001 | Schedules support one-off date/time; daily/weekly selected weekdays; intervals; and a finite ordered offset sequence followed optionally by a repeating interval. | AT-05–08 |
| FR-SCHED-002 | Due definitions are exact local time, local time window, or anytime in the operational day. | AT-09 |
| FR-SCHED-003 | Creation/edit shows the next ten dated occurrences and selected timezone before save. Invalid or ambiguous local times require resolution. | AT-10 |
| FR-SCHED-004 | User can edit only one occurrence, this and future, or an entire series; editing future creates a new version effective at a boundary. | AT-11 |
| FR-SCHED-005 | Calendar-anchored recurrence is based on planned dates; late completion does not shift the next occurrence. A separate completion-anchored interval is supported. | AT-12 |
| FR-REM-001 | Reminder levels: None, Light (one), Standard (due plus one follow-up), Persistent (bounded repeated alerts), Escalating (configured ladder). | AT-13 |
| FR-REM-002 | Reminder actions open the exact occurrence. Completion/reschedule/skip/cancel closes remaining alerts; stale actions are idempotent. | AT-14 |
| FR-REM-003 | Permission denial shows a visible in-app due list and setup route. Alerts are best effort under Android limits. | AT-15 |
| FR-ROUTINE-001 | Create/edit ordered routine steps and attach a routine to a scheduled commitment. A run records completed/skipped steps. | AT-16 |
| FR-ROUTINE-002 | Editing a routine makes a new version for future runs; historical runs retain step text/order. | AT-17 |
| FR-PROFILE-001 | Create multiple Schedule Profiles with blocks (start, duration, fixed/flexible, linked routine/commitment); assign by weekday or one-off day. | AT-18 |
| FR-PROFILE-002 | Switch profile effective on a selected date. Future plan is regenerated; past records are unchanged. | AT-19 |
| FR-MODE-001 | Modes Normal, Travel, Vacation, Sick, Late Night, Recovery Day, High Focus, Custom can be activated with start/end; rules are user-editable. | AT-20 |
| FR-MODE-002 | One-day override supports Keep essential only or per-item keep/move/skip. Suppressed occurrences carry an override reason, not a missed status. | AT-21 |
| FR-CAREER-001 | Seed an editable Career Focus daily minimum of **30 minutes** (not a forced medical or work target); user may change it in settings with effective date. | AT-22 |
| FR-CAREER-002 | Start/pause/resume/stop timer. Only explicitly active intervals count, including while the user works in another app; session keeps monotonic elapsed time and local date allocation. | AT-23 |
| FR-CAREER-003 | Rescue starts with a five-minute segment; continuing uses the same session and counts toward the daily minimum. | AT-24 |
| FR-CAREER-004 | User labels session activity (search, apply, resume, outreach, interview prep, portfolio/project) and may log application details; passive scrolling is excluded by user confirmation. Timer evidence is labelled “timed,” not independently verified work. | AT-25 |
| FR-ATTN-001 | With optional Usage Access, show Instagram usage totals and compare with Career time for the current day. Package selection is user-visible and can be changed. | AT-26 |
| FR-ATTN-002 | Optional rule: intervene when Instagram reaches a user-set threshold within an eligible window while Career is incomplete. Default office hours are excluded; cooldown and daily cap apply. | AT-27 |
| FR-HEALTH-001 | Any commitment can store user-entered source/instruction text; health items show “Follow your clinician’s instructions.” No dose calculation or generated treatment plan. | AT-28 |
| FR-DATA-001 | Export/import a versioned JSON archive via Android document picker; preview import and reject incompatible schema. Deleting local data requires explicit confirmation. | AT-29 |
| FR-DATA-002 | All primary data stays on device; no analytics SDK, cloud account, or server required. | AT-30 |
| FR-REVIEW-001 | Daily and weekly review show planned versus complete/late/skipped/missed, Career minutes, and source of evidence; no composite life score. | AT-31 |

## 2. V1 defaults and bounded policies

- Operational day: 04:00 to next 03:59:59 in the current local timezone. The UI shows calendar dates as well; changing the boundary only affects future classification.
- Career: 30 minutes every operational day, preferred window configurable at onboarding; default 19:00–21:00 if the user accepts it. One move of that day's preferred slot; further movement requires explicit override with a reason. Skipping remains possible and recorded.
- Instagram rule: **off until Usage Access and user opt-in**. Suggested threshold 15 accumulated minutes within an eligible window after the user's configured work end while Career remains incomplete; default work window 09:00–18:00, editable. At most 2 interventions per day, 60-minute cooldown. No overlay or app blocking.
- Standard reminder: due and +15 minutes. Persistent: due then every 30 minutes, max 4 delivered alerts per occurrence per operational day, until resolved or day closes. Escalating: user-configured offsets, max 5 per day. All levels respect quiet hours except an individually marked critical user-entered reminder; V1 does not use Android's emergency/critical notification privileges.
- Default quiet hours: 22:30–07:00, editable. Due alerts delayed to the first permitted time with a “delayed by quiet hours” label. The app must not claim delivery at the original time.
- On day close, unresolved due occurrences become **missed** and an explicitly selected carry-forward rule may create a linked follow-up while retaining the original missed history. An unresolved health item is shown as “unconfirmed” and flagged for review; the app must not infer whether a dose was taken or advise doubling.

## 3. Boundary table

| Capability | V1 | Later | Not planned |
|---|---|---|---|
| Manual commitments, routines, irregular sequences, modes, profile switching | Yes | Richer rule composer | — |
| Timeline conflict detection | Flag overlaps and ask user to move | Automatic multi-item reflow | Silent movement of fixed events |
| Calendar | Manual fixed events | Read-only device calendar import; optional export | Silent modification of external calendars |
| Health | Manual instruction tracking, check-off, weight as optional simple metric | Health Connect, protocol templates from user entries | App-generated dose/treatment instructions |
| Attention | Optional Usage Access totals and bounded contextual intervention | More contexts and trends | Forced Instagram lock, covert monitoring |
| Career | Timer, Rescue, activity label, optional application log | Email confirmation detection | Claim that a timer proves job work |
| Vault, inventory, advanced goals/projects | — | Yes | — |
| Account, sync, team, web/iOS | — | Evaluate later | — |
| Gamification | — | — | XP, badges, loot, punitive streaks |
| AI agent or clinical advice | — | Evaluate narrowly if requested | Autonomous life/medical decisions |

## 4. Release constraints

The application must work with no network, no Usage Access, no calendar, and denied notifications. Accurate out-of-app alert timing is conditional on Android permissions and power management; the app displays that limitation. No accessibility service, VPN, device-admin privilege, or broad package inventory permission is needed. V1 target and minimum SDK values are chosen during implementation against current Play requirements; do not hardcode an outdated target from this document.
