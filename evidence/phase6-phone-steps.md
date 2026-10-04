# Phase 6 phone steps

Keep USB connected and phone unlocked. Do not tap during automated tests.
Never uninstall BETTER, clear its storage, or use its personal-data Delete control
for this gate. All delete-and-restore testing uses the synthetic screen below.

## Usage Access (after the update and automated run)

1. Open BETTER → Settings → Attention / Usage Access.
2. With the rule off and access denied, check that usage is Unavailable, not zero.
3. Tap Open Usage Access settings → BETTER → Allow usage tracking on.
4. Return to BETTER. Keep package com.instagram.android (or enter the package you
   actually use). Tick Opt in → Save Attention settings.
5. Open Instagram for about 30 seconds, then return to BETTER → Settings →
   Attention / Usage Access. Wait up to 15 seconds. Check the selected package,
   observed time, foreground total and Career comparison. If Android supplies
   incomplete/no data, report the Unavailable message exactly.
6. Open Usage Access settings → BETTER → Allow usage tracking off → return.
   Usage must become Unavailable within the next foreground refresh.
7. Untick Opt in → Save Attention settings to restore the default rule-off state.
   Notification permission stays unchanged unless specifically needed for a test.

## Synthetic Android document-picker gate

The agent opens the debug-only screen labelled PHASE 6 · SYNTHETIC ONLY.
This screen uses phase6-ownership-probe.db, never your personal database.

1. Tap 1. Prepare synthetic fixture, then 2. Export synthetic archive.
2. In Android's Save dialog, open the side menu → Downloads (local phone storage).
   Keep BETTER-phase6-synthetic.json → Save. If a same-name file exists, choose a
   new filename, rather than overwrite an unrelated document.
3. Return to the synthetic screen. Type DELETE SYNTHETIC in its confirmation
   field → 3. Delete synthetic records only.
4. Tap 4. Choose synthetic archive → Downloads → the file just created.
5. Read the preview counts before tapping 5. Confirm synthetic restore.
6. The screen must say PASS and Every table matches. Report that result.

## Offline

Before the synthetic picker gate, swipe down twice → Flight mode on → Wi-Fi off
if it remains on. Complete the local Downloads picker steps while offline.
After requested offline tests, restore Flight mode off and Wi-Fi on.

## Readability

Check that Attention/package/window fields, archive preview, and Confirm/Cancel
controls are readable and reachable by scrolling. Full redesign remains deferred.
