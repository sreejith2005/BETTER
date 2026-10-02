# 08 — Android implementation research and constraints

**Checked:** 2026-09-30. These are platform facts and architecture recommendations, not guarantees of device-specific delivery. Recheck official Android and Google Play guidance when implementing and publishing.

| Area | Current finding | BETTER V1 decision |
|---|---|---|
| Notification permission | Android 13+ requires runtime `POST_NOTIFICATIONS` for ordinary notifications; denial blocks them. [Android Developers](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | Request in context; keep in-app due list and visible denied state. |
| Exact alarms | Android 14 denies `SCHEDULE_EXACT_ALARM` by default to many newly installed apps; check `canScheduleExactAlarms()` and degrade gracefully. `USE_EXACT_ALARM` is restricted to qualifying app categories. [Android Developers](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) | Ordinary reminders use inexact alarms; precise user-marked alerts only if permission/policy fit. Do not promise exact delivery. |
| Background work | WorkManager persists across app restarts/reboots and suits maintenance, but is not a precise wall-clock alarm mechanism. [Android Developers](https://developer.android.com/develop/background-work) | Use WorkManager for reconciliation; AlarmManager for time-based alert attempts. |
| App usage | `UsageStatsManager.queryEvents` requires `PACKAGE_USAGE_STATS`; events are retained only a few days and may be unavailable before unlock. [Android Developers](https://developer.android.com/reference/android/app/usage/UsageStatsManager) | Opt-in Usage Access, selected package, local aggregate, unknown state on missing data. |
| Package visibility | Broad `QUERY_ALL_PACKAGES` is restricted by Google Play policy; targeted queries are preferred. [Google Play policy](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en-GB) | Do not request broad installed-app inventory. |
| Calendar | Reading device calendar directly requires `READ_CALENDAR`; calendar intents can hand off user actions without direct permission. [Android Developers](https://developer.android.com/identity/providers/calendar-provider) | V1 manual fixed events; read-only integration later with separate consent. |
| Health Connect | Availability differs by Android version; permissions are granular and revocable. [Android Developers](https://developer.android.com/health-and-fitness/health-connect/availability), [permissions guidance](https://developer.android.com/health-and-fitness/health-connect/ui/permissions) | Health Connect later; V1 manual, source-labelled health tracking. |
| Room | Room adds compile-time query checks and migration support over SQLite. [Android Developers](https://developer.android.com/training/data-storage/room/sqlite-room-migration) | Use Room with explicit migration tests; no destructive migration. |

## Implementation cautions

Android can delay or suppress background work under device power policies, permission changes, and force-stop. BETTER can reliably preserve the local plan and reconcile when reopened; it cannot guarantee that every notification is seen at the planned second. The UI and tests must distinguish scheduled, posted, and observed action. This is an implementation inference from the documented permission and background behavior above.

UsageStats is a privacy-sensitive optional integration. It can support a contextual rule, but no inference that a person was actually paying attention to Instagram or working on Career should be presented as fact. Timed Career work is user-labelled evidence. For Play distribution, recheck current target SDK, sensitive-permission policies, and app review requirements before release.
