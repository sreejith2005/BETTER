# 04 — Technical architecture and data model

## 1. Stack and module boundaries

V1 is native Kotlin with Jetpack Compose and a single Activity, ViewModel/state holder per feature, coroutines/Flow, Room over SQLite for durable domain data, and DataStore for small preferences. Use a domain layer with pure schedule expansion and state-transition functions so it can be unit tested without Android. Platform adapters handle alarms, notifications, UsageStats, document picker, boot/time change events, and permissions. Depend on interfaces, not platform classes, in the scheduler. Choose dependency versions and Android SDK levels when coding from current official guidance.

Suggested modules/packages: `domain` (rules, time, states), `data` (Room entities/DAOs/migrations), `platform` (alarms, notifications, usage, export), `feature/today`, `feature/plan`, `feature/career`, `feature/review`, `feature/settings`. A modular Gradle split is optional; the boundaries are required.

All writes that change a schedule, occurrence, or alert state use one Room transaction. A reconcile worker regenerates a rolling 45-day horizon and pending notifications from authoritative records after save, boot, timezone change, app upgrade, or permission change. Never treat scheduled Android alarms as the database of truth.

## 2. Storage conventions

- IDs: UUID strings; timestamps: UTC epoch milliseconds; local dates/times: ISO strings plus IANA timezone ID where relevant; durations: integer seconds.
- Persist the raw rule and a human-readable schedule summary. Do not store a recurrence as free text alone.
- Every occurrence has an immutable `occurrence_key = commitment_id + rule_version + planned_local_date + ordinal` or a stable UUID persisted on first generation. Regeneration upserts by key and never duplicates a resolved occurrence.
- Use explicit schema migrations and migration tests; never use destructive migration on personal history.
- Sensitive content remains in app-private storage. Export is user initiated, versioned, and should offer device-protected file destination via the system picker. Explain that exported files are outside app control.
- Maintain an append-only action log for schedule edits, status changes, timer actions, and mode changes; it is user-visible history, not covert telemetry.

## 3. Entity contract

| Entity | Key fields | Relationships and invariants |
|---|---|---|
| `AppSettings` | dayBoundary, timezonePolicy, quietHours, privacyMode, exportSchema | Singleton; effective-dated changes logged |
| `Commitment` | id, title, category, priority, essential, archivedAt, instructionText, sourceText | Stable identity; many versions and occurrences |
| `CommitmentVersion` | id, commitmentId, effectiveFrom, effectiveUntil, scheduleRuleId, reminderPolicyId, duration, title/category/priority/essential/instruction/source snapshots, createdAt | No overlapping effective intervals per commitment |
| `ScheduleRule` | id, type, anchorDateTime, zoneMode, zoneId, frequency, weekdays, interval, sequenceOffsets, tailInterval, endDate/count, anchoring | Validated typed payload; immutable once referenced |
| `Occurrence` | id/key, commitmentId, versionId, plannedStart/end, dueWindow, localDate, state, actualAt, reason, mode/override provenance | One row per generated due item; resolved history immutable except correction action |
| `OccurrenceOverride` | occurrenceId, newDue, disposition, reason, createdAt | Overrides one instance; source remains traceable |
| `ReminderPolicy` | id, level, offsets, maxPerDay, quietHoursBehavior | Immutable version snapshot |
| `ReminderDelivery` | occurrenceId, policyVersion, plannedAt, deliveredAt, result, systemId | Unique occurrence+policy+step; records attempted versus observed delivery |
| `Routine` / `RoutineVersion` | id, effective dates, title, ordered step snapshots | Future edits versioned; historical runs reference exact version |
| `RoutineRun` / `StepResult` | occurrenceId, routineVersionId, stepId, state, timestamp | No silent rewrite of prior step labels |
| `ScheduleProfile` / `ProfileVersion` | id, effective dates, day assignments | Versioned structure |
| `ProfileBlock` | versionId, start, duration, fixed/flexible, linkedId, priority | Can flag overlaps; does not auto-move fixed items |
| `LifeModeActivation` | mode type, start/end, policySnapshot, createdAt | More than one can be queued; precedence defined in `05` |
| `DayOverride` | date, item dispositions, reason | Highest schedule modifier for that date |
| `FocusSession` / `FocusSegment` | id, activity, start/end wall and monotonic markers, state, interruptionReason | Only confirmed active segments count |
| `AttentionRule` / `AttentionObservation` | packageName, eligibleWindows, threshold, cooldown, cap; observed interval and duration | Opt-in; no raw app usage history beyond needed aggregate |
| `ActionLog` | id, entity, entityId, action, old/new summary, timestamp | Append only; supports audit and troubleshooting |

Future entities such as `Project`, `Goal`, `InventoryItem`, `VaultItem`, external `CalendarEvent`, and Health Connect samples are **not V1 tables** merely because they appear in the long-term PRD.

## 4. State machines

**Occurrence:** `PENDING → DUE → OVERDUE → COMPLETED | SKIPPED | CANCELLED | MISSED`. Reschedule keeps the same occurrence ID and adds an override; after save, return to `PENDING` until new due time. Day override suppression uses `SKIPPED_BY_OVERRIDE`, distinct from `SKIPPED`. A correction is an explicit action-log event; the original transition remains auditable. Completion after due time is `COMPLETED` with `completedLate=true`, never both completed and missed.

**Focus session:** `CREATED → RUNNING ↔ PAUSED → COMPLETED | ABANDONED | INTERRUPTED`. Persist active segment start, same-boot monotonic marker, wall-clock start, and periodic checkpoint. Working in another app does not pause the timer. On same-boot process restart, derive elapsed from monotonic time and ask the user to confirm the interval. On reboot, close at the last persisted checkpoint and ask for review; never award an interval whose clock continuity is uncertain. Wall clock assigns operational date.

**Reminder:** `PLANNED → FIRED | SUPPRESSED | CANCELLED | FAILED`. `FIRED` means the app posted a notification request, not that the user saw it. Notification action handlers are idempotent and check current occurrence state.

## 5. Data flow and transaction order

Create/edit → validate rule → compute ten-item preview → transaction writes immutable version and any one-off override → reconcile future occurrences → cancel stale scheduled alerts and enqueue relevant new ones → UI displays confirmed local state. On action: transaction changes occurrence/session and logs it → reconcile alerts → redraw Today/Review. Usage Access data is read on demand or at a conservative interval, converted to short-lived aggregate; it never blocks Today rendering.

## 6. Backup and recovery

JSON archive includes schema version, settings, commitments/versions, rules, occurrences, routines, profiles, modes, focus history, and log. Exclude permission grants and Android notification IDs; recheck permissions and rebuild alerts after import. Import uses staging validation and one atomic replace/merge choice; V1 only supports **replace after preview**. Detect duplicate/corrupt IDs and invalid rule references before commit. Export/import round trip must preserve history and future schedule preview.
