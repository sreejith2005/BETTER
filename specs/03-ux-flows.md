# 03 — UX, screens, and interaction flows

## 1. Navigation and voice

Bottom navigation: **Today · Plan · Review · Settings**. Persistent `+` creates a commitment; Today has **Start Career** and **Rescue** actions. Avoid a dashboard of dozens of cards. Use large touch targets, system font scaling, accessible labels, clear contrast, and dark mode. Every critical action is usable without internet.

Voice: terse and factual. Examples: “Career: 12 minutes remaining.” “You moved this once today.” “Reminder delayed: notifications are off.” “You skipped this occurrence: too tired.” Avoid praise for app usage, threat language, guilt, XP, emoji rewards, and false certainty. Do not display “verified” for self-reported work.

## 2. Screen inventory

| Screen | Primary content/actions | Required states |
|---|---|---|
| Onboarding | Day boundary, work hours, Career window, optional permissions, sample schedule | Skip permissions; revise later |
| Today | Next action, Career 0/30, active profile/mode, timeline, due/overdue items | Empty; normal; conflict; permission denied; post-reboot reconciliation |
| Commitment editor | Identity, schedule builder, reminder level, instructions/source, next-ten preview | Draft; invalid; DST ambiguity; edit scope |
| Commitment detail | Future occurrences, history, actions, version, reminder delivery state | Pending; due; overdue; complete; skipped; missed |
| Routine editor/run | Ordered steps and run checklist | Empty; active; interrupted; completed |
| Profiles | List, version history, day assignment, edit/new version | No profile; draft conflict; effective date |
| Life Mode/override | Mode dates and rules, day preview, item dispositions | Normal; active; pending; expired |
| Career Focus | Timer, activity label, pause/resume/stop, today total | Running; paused; interrupted; completed |
| Rescue | Five-minute start, continue/stop, next action | Career incomplete; Career done; no current action |
| Attention | Usage total, eligible windows, opt-in rule, permission route | Off; denied; unavailable; active; stale data |
| Review | Daily/weekly facts and reasons | No history; partial current day; complete |
| Settings/data | Permissions, quiet hours, timezone, export/import/delete | Import preview; error; success |

## 3. Key flows

### F-01 — First launch

Open → explain local storage → choose operational-day boundary → set work hours and Career window → create default 30-minute Career commitment → optionally request notifications in context → Today. Usage Access is requested only when the user enables Attention. Declining any permission leaves the plan usable and displays a persistent, non-blocking capability message.

### F-02 — Add irregular health reminder

`+` → “Commitment” → title and optional exact clinician instruction/source → “Sequence” → choose anchor date → enter offsets `+3d, +2d, +1d`, then “every 2 days” → choose local time and reminder level → preview next ten → save. The UI distinguishes “start on anchor date” from “first due date is anchor + offset.” No example dose text is pre-filled. On completion, store self-report and timestamp; on missed date, show “unconfirmed.” A separate follow-up appointment reminder can be created with “one month after start” as a calendar-month offset and its own title.

### F-03 — Change morning structure

Plan → Profiles → duplicate current Workday → insert Run before Gym, change times → preview affected future days and conflicts → activate from selected Monday. Historical days keep the prior profile version. A one-day variation instead uses Today → Day override.

### F-04 — Career Focus

Today → Career card → choose activity label → Start → running timer → Pause when interrupted → Resume → Stop. Count active segments while the user works in another app. At 30 minutes, record minimum met; more time remains possible. If the app process is killed and relaunched in the same boot, reconstruct elapsed time from the persisted monotonic start and ask the user to confirm the session was still active. If the device rebooted, close at the last persisted checkpoint and ask the user to resume or close; never add an interval whose clock continuity is uncertain. A session crossing the operational boundary is split by active interval.

### F-05 — Rescue

Today → Rescue → “Start five minutes” → Career timer begins. After five active minutes, “Continue toward 30” keeps same session; “Stop” records five minutes and shows remaining daily amount. Rescue has no separate score.

### F-06 — Reminder action

Notification tap → exact occurrence detail. Action buttons: Start/Open, Complete, Move, Skip. Move opens available time/date and impact preview; Skip requires reason for a non-negotiable. A resolved or stale notification opens history instead of duplicating an event. Users can mute one item or change its policy in Settings.

### F-07 — Life Mode and day override

Today → mode chip → choose preset or Custom → dates → preview which commitments remain, shift, or suppress → confirm. Mode changes future plan while retaining existing completed occurrences. For a single abnormal day choose “Keep essential only,” then review per-item exceptions. Essential health reminders default to keep; the user may still change them and receives a neutral “check your instructions” prompt.

### F-08 — Instagram intervention

Settings → Attention → explain Usage Access and data retained → select Instagram package → define eligible after-work/focus windows and threshold → enable. When threshold is met and Career incomplete, show one actionable notification: “Instagram 15 min in this window. Career 0/30. Start five minutes.” Tap → Rescue. Office hours are excluded by default. If access disappears, state changes to unavailable; no guessed usage figure appears.

## 4. Shared screen states and error handling

- **Empty:** one clear creation action and a short explanation, never invented tasks.
- **Loading:** local query indicator only when meaningful; core Today must not wait on network or UsageStats.
- **Conflict:** show exact overlapping intervals and whether each is fixed/flexible; offer manual move or keep overlap. Saving is allowed with explicit acknowledgement.
- **Permission denied:** state what will fail and provide Settings route; never loop permission prompts.
- **Offline:** no core degradation; optional integrations show unavailable.
- **Interrupted timer:** elapsed confirmed segments remain; uncertain interval is not counted.
- **Time change/reboot:** show “schedule checked” or a discrepancy needing review, and recalculate pending alerts.
- **Import failure:** no partial import; retain old database and display reason.

## 5. Accessibility and privacy details

Notification content can be set to generic on lock screen (“BETTER reminder”) to protect health text. Health/source instructions appear only after unlock. Destructive actions require confirmation and explain what will be deleted. Text alternatives and state labels must not rely on color alone. Default focus is the next real-world action rather than an engagement feed.
