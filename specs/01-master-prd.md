# 01 — Master product requirements document

**Product:** BETTER Personal Operating System · **PRD version:** 1.0

## 1. Product definition

BETTER translates chosen personal standards into a usable day: **intention → schedule → next action → execution → evidence → review → adjustment**. It joins commitments, routines, focus, reminders, life changes, and tracking in one system. It should answer: *What matters? What should I do next? What actually happened? What should change?*

Primary user is one person. Android is first. The product is local-first and useful without an account or network. A future multi-device or multi-user product requires a separate decision.

## 2. Product problem and outcome

The user often knows what matters but loses the link between plan and action, particularly when scrolling Instagram. Rigid habit calendars break when exercise, office timing, travel, illness, late nights, or temporary health instructions change. BETTER should make the next useful action obvious, surface missed commitments honestly, and let a changed lifestyle be configured without rebuilding everything.

Success is measured by execution, not app opens. Primary initial outcome: a daily **30-minute Career Focus minimum**, with actual focused time recorded and a clear recovery path when the day goes off plan. Secondary outcomes: fewer forgotten commitments, predictable reminders, fast schedule changes, and a trustworthy history.

## 3. Agreed product decisions

| ID | Decision |
|---|---|
| PD-01 | Android-first native experience; single-user-first. |
| PD-02 | Local-first data ownership; no login needed for V1. |
| PD-03 | Highly flexible schedules: exact time, windows, irregular sequences, recurrence, exceptions. |
| PD-04 | Routines and Schedule Profiles are versioned; future edits do not rewrite history. |
| PD-05 | Life Modes and one-day overrides can reduce or move expectations without falsely marking them missed. |
| PD-06 | Daily Career Focus minimum starts at 30 minutes. Career activity is deliberate work, not passive feed scrolling. |
| PD-07 | Rescue offers a five-minute start that can continue toward the daily minimum. |
| PD-08 | Instagram intervention is contextual. Office-hour use is allowed by default; intervention targets configured focus windows or after-work use while Career is incomplete. |
| PD-09 | Voice is Spartan: short, direct, factual, and respectful. No childish rewards, XP, badges, or shame. |
| PD-10 | User-entered health and medication instructions may be scheduled and tracked; the app does not invent, alter, or recommend treatment. |

## 4. Universal model

| Concept | Meaning | Example |
|---|---|---|
| Commitment | One thing owed at a time or within a window | Career Focus; appointment follow-up |
| Routine | Ordered reusable actions | Morning system |
| Schedule Profile | Reusable structure for a type of day | Workday + run + gym |
| Life Mode | Temporary policy affecting expectations | Travel; Sick; Late Night |
| Override | One-day or one-occurrence exception | Tomorrow leave early |
| Occurrence | One dated instance of a commitment | Ear drops on 12 October |
| Evidence | How completion was established | Manual confirmation; focus timer |
| Reminder Policy | How due work is surfaced | One alert; escalating ladder |

The future product may add projects, goals, metrics, protocols, inventory, vault, automated reflow, and external integrations. Each is a separate capability; the V1 boundary is in [02](02-v1-scope.md).

## 5. Behavior principles

1. **User control.** Changes to profiles, modes, reminders, and commitments are reversible and previewed before activation.
2. **Temporal truth.** Past occurrences keep the schedule/version and status in force at the time. Completing late is recorded as late; skipped, paused, missed, and never scheduled are distinct.
3. **Planning is advisory.** The timeline suggests action and flags conflict. It does not silently move a fixed event or claim every second is optimized.
4. **Attention with context.** Instagram usage is neutral until a rule the user enabled says it conflicts with a chosen commitment. Office scrolling is not automatically a failure.
5. **Accountability without manipulation.** Alerts prompt real action, have a defined ceiling, and can be turned off. A missed day gets a plain record and a next step.
6. **Health humility.** User-entered directions are copied exactly. The app never derives dose, frequency, or safety guidance from free text.
7. **Graceful degradation.** Denied notification, usage, or calendar access never destroys the core local plan. Missing permission is visible.

## 6. Product experience

Today leads with the next action and daily standard. A tap starts Career Focus, marks a commitment, or opens a routine. The timeline shows fixed and flexible work; a conflict asks the user to resolve it. The user can switch to a different profile starting on a chosen date, activate a temporary mode, or override one day. Reflection shows actual completed, late, skipped, and missed items with reasons and evidence source. These are records, not a score of personal worth.

## 7. End-state direction beyond V1

The long-term Day Engine may reflow flexible work around calendar events; connect to Health Connect; process saved content in a vault; track consumables; connect job application evidence; and support event-relative and conditional rules. These are product direction, **not V1 commitments**. They require explicit specifications, permissions, privacy reviews, and test gates before implementation.