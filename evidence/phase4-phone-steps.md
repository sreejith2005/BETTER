# Phase 4 phone checks

Use the USB-connected Samsung, phone user 0. Do not uninstall BETTER or clear its
storage. Tests install an update over the existing debug app. Synthetic timer
checks use the separate `phase4-focus-probe.db`, not your personal Career history.

## During automated UI tests

1. Unlock the phone and leave its screen on.
2. Leave the USB cable connected and do not tap or use Back while tests run.
3. No new permission is required. Notifications may remain denied.

## Owner review and real reboot (when prompted)

The screen will say **PHASE 4 · TEST DATA**. These steps affect only synthetic
Career records.

1. Read the running timer and its activity/evidence text. Tap **Pause**. It should
   show **paused**, with **Resume** and **Stop and save**.
2. Tap **Resume**. Confirm the timer advances. Let it run at least 10 seconds.
3. Swipe down twice → tap the power icon at the top → **Restart** → **Restart**.
4. Unlock the phone after restart. Keep USB connected and approve debugging only
   if Android asks. Tell Codex the restart is complete; Codex reopens the test screen.
5. On that screen, expect **interrupted** and the last-checkpoint explanation.
   Tap **Resume**, wait a few seconds, then **Stop and save**.
6. Report whether the text/buttons were readable and recovery appeared as described.

## Optional personal use after the gate

- BETTER → **Today** → **Start Career** or **Rescue** → choose an activity.
  Optional company/role are self-reported details. Tick the deliberate-work
  confirmation, then **Start timer** or **Start five minutes**.
- Pause whenever interrupted. Work in another app counts while active.
- Rescue stops counting at five active minutes. **Continue toward daily minimum**
  keeps the session; **Stop and save** ends it.
- **Settings** → **Career daily minimum** → enter minutes and a future date in
  YYYY-MM-DD format → **Save future target**. Saved dated entries appear below.
- **Review** → **Career evidence** shows daily/seven-day timed totals, session
  labels and optional application details. Manual occurrence completion never
  creates timed minutes.

Full visual redesign remains after Phase 6, when Pinterest references are requested.
