# 05 — Scheduling, Day Engine, and reminder engine

This document defines V1 behavior. Implement schedule expansion as a pure function of rule version, date range, timezone, and overrides. Return identical outputs for identical inputs.

## 1. Time model

An occurrence has `plannedLocalDate`, optional `localTime`, `zoneMode`, `zoneId`, derived UTC instant, due window, and an operational-day key. `LOCAL_FLOATING` means the chosen wall time in the device's current timezone for future unstarted occurrences. `ANCHORED_ZONE` means wall time in a named IANA zone. `ABSOLUTE_INSTANT` applies only to one-off fixed events. Default routines and Career use local floating time. Once an occurrence is due, completed, skipped, or otherwise acted on, its planned instant and zone snapshot do not change.

Operational day begins at the configured boundary (default 04:00). The day key for local time before 04:00 is the prior calendar date. Use real timezone rules; never add `24*60*60` seconds to mean “tomorrow.” In a DST gap, choose the first valid local instant after the gap and flag the preview. In an overlap, default to the earlier offset and allow the user to choose later. Require acknowledgement of a gap adjustment or overlap offset selection before save. Persist the chosen resolution. On timezone change, re-expand only unacted floating occurrences; anchored and historical instances remain fixed.

## 2. Rule types and semantics

| Type | Input | Expansion |
|---|---|---|
| `ONE_OFF` | local date/time or instant | One occurrence |
| `DAILY` | anchor date, every N days | Dates anchor + N days; local time resolved per date |
| `WEEKLY` | selected weekdays, every N weeks, anchor week | Chosen weekdays in matching weeks |
| `INTERVAL_CALENDAR` | anchor, period in days/weeks/months | Advance planned date; late completion has no effect |
| `INTERVAL_COMPLETION` | interval after actual completion | Next occurrence generated only after complete; skipped requires explicit restart/skip policy |
| `SEQUENCE_THEN_REPEAT` | anchor due date, ordered positive date offsets, optional tail interval | First occurrence at anchor; then apply each offset to previous planned date; afterward repeat tail interval |

For months, `+1 calendar month` preserves day-of-month if present and clamps to the last day otherwise; subsequent monthly steps refer to the original anchor day to avoid permanent drift. For `SEQUENCE_THEN_REPEAT`, offsets are relative to the preceding **planned** date, never actual completion. Example anchor 1 October, offsets `+3d,+2d,+1d`, tail every 2 days → 1, 4, 6, 7, 9, 11, 13 October. A follow-up “one month after start” is a separate one-off derived from the anchor date, previewed and then stored as its own commitment in V1. V1 does not maintain a live dependency if the source start changes; the editor warns that follow-up date must be reviewed.

Schedule rule validation: positive intervals, increasing sequence dates, finite bounded expansion, no duplicate instances, valid weekday set, a finite sequence without a tail has implicit count 1 + number of offsets (an optional end/count may shorten it). The preview shows ten instances or all remaining if fewer, including DST/timezone notes and any conflicts.

## 3. Occurrence generation and edits

Generate a rolling horizon of 45 days, plus unresolved historical occurrences. Use deterministic keys and upsert. Once an occurrence has user action or due delivery, do not delete it. `Edit this occurrence` writes an override. `This and future` closes current version at the selected occurrence boundary and creates a new version; unacted future occurrences are regenerated. `Entire series` is allowed only when there is no historical acted occurrence; otherwise it behaves as “this and future” and explains why.

Calendar recurrence is based on the planned timeline. Completing a Monday occurrence Tuesday does not move Wednesday. Completion-anchored recurrence uses actual completion timestamp; if skipped, the UI asks whether to restart from skip time, preserve a manually selected next date, or stop series. Default is stop pending user choice. This prevents a hidden medical schedule shift.

An occurrence becomes `DUE` at window start (or exact due time) and `OVERDUE` after window end or exact time. `ANYTIME` becomes overdue at operational-day end; reconciliation at that boundary records `MISSED` immediately rather than persisting a zero-length overdue state. At day close unresolved items become `MISSED`, except a selected carry-forward rule creates a linked follow-up item and retains missed history. For an explicit overnight window ending after that boundary, closure is delayed until the window end; its operational-day key remains the day in which the window began. Health-related items remain “unconfirmed” in copy even if internal state is `MISSED`.

## 4. Schedule Profile and Life Mode precedence

The plan is derived in this order:

1. Base commitments and their versioned schedule rules.
2. Schedule Profile blocks effective for the day.
3. Life Mode policy effective for that day. If multiple overlap, the most recently explicitly activated mode wins; UI displays overlap and asks for resolution before activation. Normal is the absence of a special mode.
4. Day override for that local date.
5. One-occurrence override.
6. Already recorded completion/skip/cancel state always wins over plan recalculation.

A Schedule Profile gives proposed blocks. It does not duplicate a linked commitment: the block attaches to its occurrence by stable ID. Profile changes can move a flexible planned start within its allowed window. A mode may keep, suppress, or propose a move based on user-edited rules. **Essential** commitments default to keep in presets, but a user can explicitly override them. No mode silently marks a commitment complete.

V1 conflict detection compares fixed and proposed intervals. It marks overlap with exact start/end and asks the user to move a flexible item, keep overlap, or change profile/mode. Auto-reflow across an entire day is later. Fixed external appointments can be entered manually as local commitments in V1.

## 5. Reminder scheduling

The database is authoritative. On any plan/state change, calculate the next alert steps for the rolling horizon, cancel stale Android alarm IDs, and schedule new steps. Prefer inexact AlarmManager APIs for ordinary reminders; use WorkManager for reconciliation/retry and non-exact maintenance, not as a promise of minute-perfect delivery. Exact alarms are optional only for a user-marked precise reminder after checking permission and policy suitability. Do not use `USE_EXACT_ALARM` unless the app clearly qualifies under Android/Play rules. If exact access is unavailable, show best-effort timing and schedule inexact fallback. Android 13+ notification permission must be requested in context; denied permission means in-app reminders only.

Level behaviors: `NONE` only in app; `LIGHT` one due alert; `STANDARD` due and +15 minutes if unresolved; `PERSISTENT` due plus +30,+60,+90 minutes, max four per operational day; `ESCALATING` user-configured offsets (max five, strictly increasing). These are **alert attempts**, not guaranteed views. Cancel future steps immediately upon resolution. Enforce quiet hours by delaying an alert to first permitted instant; coalesce multiple delayed steps for the same occurrence into one notification and mark delay. Do not post an entire backlog of stale alerts after reboot.

On reboot, app upgrade, wall-time/timezone change, and exact-permission change: reconcile occurrence states, schedule only future alert steps, and summarize missed steps in app. A periodic WorkManager safety reconciliation may be late and must be idempotent. Notification channels separate ordinary commitments, Career, and user-entered health reminders; user can change channel behavior in Android settings. Lock-screen privacy option hides title/instruction content.

## 6. Attention intervention

After explicit opt-in and Usage Access, read only selected package foreground usage needed for the current local day. Compute usage within eligible windows from usage events where available; treat inaccessible or stale data as **unknown**, not zero. When the current eligible window's accumulated usage crosses threshold and Career is incomplete, post at most one intervention for that threshold; enforce 60-minute cooldown and two per day. Default office hours and commute windows are excluded; user can edit all windows. Suppress while Career timer is running, during quiet hours, or when the active mode disables it. No Accessibility Service, screen overlay, app lock, or background surveillance claim.

## 7. Invariants

- A scheduled item is never silently completed, dosed, or medically rescheduled.
- One occurrence can have only one current terminal state; actions are idempotent.
- Historical version and local date/zone snapshot remain readable after any future edit.
- Notification attempt count never exceeds policy cap per occurrence/day.
- Reminder delivery failure cannot change the underlying commitment state.
- A mode/override suppression is not counted as user failure.
- Due previews, stored occurrences, and reminder schedule are generated from the same pure rule engine.
