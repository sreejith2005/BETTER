# Phase 3 phone checks

Device: Samsung Galaxy A55 (SM-A556E), Android 16. These steps are performed by
the phone owner while Codex verifies the resulting permission and stored state.
Never uninstall BETTER or use Clear data. The live test screen uses a separate
private debug database; normal BETTER keeps the personal plan.

## Prepare

1. Keep the USB data cable connected and unlock the phone with your PIN.
2. Swipe down twice. Turn Flight mode on, then turn Wi-Fi off if it remains on.
3. Leave notifications off initially. To check: Settings → Apps → BETTER →
   Notifications → Allow notifications off.
4. Reply ready. During automated UI tests, leave the phone unlocked and do not
   tap or swipe. Codex will say when the UI run is finished.

## Enable notifications when prompted for the delivery check

1. Settings → Apps → BETTER → Notifications → Allow notifications on.
2. Leave sound/channel settings at their existing values. In-app route:
   BETTER → Settings → Request reminder permission → Allow. If no prompt
   appears after a prior denial, use Notification settings on the same page.
3. There is no exact-alarm permission to grant. This build intentionally uses
   ordinary inexact alarms. Keep Alarms & reminders access unchanged.
4. Reply enabled. Codex schedules labelled test commitments and opens the test
   screen. Press Home to check background delivery; do not force-stop the app.

## Notification action

1. When `BETTER test: complete` appears, swipe down to open the notification
   shade and expand the notification if necessary.
2. Tap Complete. Unlock if requested. The exact test occurrence should open
   with a Record completion dialog.
3. Tap Confirm complete. This records self-reported completion. It should cancel
   the remaining test ladder. It does not add Career timer minutes.
4. Tell Codex that you saw the notification and confirmed it. Seeing a system
   post in logs alone does not count as this manual observation.

## Reboot when Codex says the recovery fixture is ready

1. Hold Side key + Volume down together for about two seconds, releasing when
   the power menu appears. Tap Restart → Restart.
2. Keep USB attached. After startup, unlock once using the PIN/password.
3. Approve Allow USB debugging if prompted. Do not open BETTER yet; background
   boot recovery is checked first. Reply rebooted and unlocked.
4. Future steps should remain scheduled; old steps must not appear as a burst.
   Android may delay delivery. Codex captures the actual post time separately
   from the planned time.

## Restore after tests

1. Settings → Apps → BETTER → Notifications → Allow notifications off, restoring
   the original denied permission. Open normal BETTER; Today should show that
   notifications are off while due records remain available.
2. Restore Flight mode off and Wi-Fi on, matching the start of this Phase 3
   session. Keep any other user settings unchanged.
3. Codex removes only its separate debug fixture database and notifications.
   No personal database, private preservation snapshot, or app storage is reset.

## If USB disappears

Unlock → Settings → Developer options → USB debugging off, then on → reconnect
the data cable → Allow debugging. If still absent, try another USB port/data
cable. Secure Folder means ADB installation and instrumentation use phone user 0.

## Additional release checks

Phone-wide time/timezone changes, battery saver and full lock-screen/privacy
coverage are recorded separately if performed. Do not change those settings
unless a specific test is ready. They can affect other phone apps. Deterministic
DST/timezone tests are not evidence of a manually changed device clock.

## Verification note

Do not run instrumentation between reboot and observing background recovery:
it force-stops BETTER and cancels alarms. Use the read-only synthetic fixture
snapshot helper and alarm state first. The completed Phase 3 session repeated
the reboot for this reason. Final results are in phase3-checklist.md.
