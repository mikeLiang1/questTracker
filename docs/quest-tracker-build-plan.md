# Quest Tracker — Build Plan & Model Prompts

> Companion to `quest-tracker-design-foundation-v2.md`. This doc defines the module
> architecture, the build order, and copy-paste prompts for delegating each phase to a
> Sonnet-level model. Phases marked **[YOU]** are learning-critical: write them yourself,
> use the model only for review. Phases marked **[DELEGATE]** are safe to hand off.

---

## Architecture summary

```
:app (Compose UI, ViewModels, DI, nav)
  ├─► :data (Room, DAOs, mappers) ──► :core
  └─► :health (Health Connect adapter, WorkManager sync) ──► :core

:core = pure Kotlin, ZERO Android imports.
        Domain models, quest engine, progression math, reflection logic,
        and the interfaces (QuestRepository, HealthDataSource, Clock).
```

Rules that apply to every phase:
- `:core` never imports anything from `android.*` or `androidx.*`. CI-check this.
- Time is always injected via a `Clock` interface — never `System.currentTimeMillis()`
  inside logic. (Testability + midnight-boundary correctness.)
- Sync failure = absence of data = offer manual completion. Never a failure state,
  never breaks consistency scoring.
- Nothing is ever taken away from the user (no decaying values anywhere).
- UI state is unidirectional: events up, StateFlow down. No logic in ViewModels.

---

## The standing preamble (attach to EVERY prompt below)

Copy this block to the top of every build prompt you give a model. It keeps a
Sonnet-level model inside the architecture without needing the whole design doc.

```
CONTEXT (do not violate):
- Android app, Kotlin + Jetpack Compose, MVVM with StateFlow, unidirectional data flow.
- Module structure: :app → :data → :core and :app → :health → :core.
- :core is PURE KOTLIN: no android.* or androidx.* imports, ever.
- All time access goes through the injected Clock interface.
- Persistence is Room (in :data). DI is [Hilt|Koin — fill in your choice].
- Domain rules: gains are permanent and never decay; a missed health-data sync must
  never mark a quest failed or affect consistency scoring; occasional missed days are
  absorbed as neutral "rest days" per the engine's rules; the app never punishes.
- Quests have a QuestKind: Recurring (dailies/weeklies/monthlies — feed attributes)
  or SideQuest (one-off tasks — count toward lifetime completions, NEVER feed
  attributes).
- Notification copy is only ever the quest name at a user-chosen time. No guilt
  framing, no "you haven't done X" messaging, ever.
- Write idiomatic, modern Kotlin. Prefer immutable data classes and pure functions in
  :core. Include KDoc on public APIs.
- Every deliverable includes unit tests. Tests are the definition of done.
- If a requirement is ambiguous, state your assumption in a comment and proceed —
  do not silently invent product behaviour.
```

---

## Phase 0 — Project scaffold [DELEGATE]

**Goal:** empty multi-module project that compiles, with the dependency arrows enforced.

**Prompt:**
```
[PREAMBLE]

Task: Create the Gradle scaffold for a multi-module Android project named QuestTracker.
Modules: :app (Android application, Compose), :data (Android library), :health
(Android library), :core (pure Kotlin/JVM library — kotlin("jvm") plugin, NOT the
Android library plugin).

- Version catalog (libs.versions.toml) for all dependencies: Kotlin, Compose BOM,
  Room, WorkManager, Health Connect client, coroutines, [Hilt|Koin], JUnit5, Turbine.
- :app depends on :data and :health; :data and :health depend on :core. No other
  inter-module dependencies.
- Add a Gradle check (or Konsist test) that fails the build if :core contains any
  android.* / androidx.* import.
- Single Activity, Compose entry point, one placeholder screen showing "Quest Tracker".
- minSdk: set to Health Connect's minimum supported API level — state which level you
  used and why.

Definition of done: `./gradlew build` passes; the :core purity check demonstrably
fails if an androidx import is added.
```

---

## Phase 1 — Core domain models & quest engine [YOU — model for review only]

**Goal:** the entire game logic, fully tested, no Android in sight. This is the phase
that teaches you the most and the one interviewers will ask about. Write it yourself.

What it contains: `Quest`, `QuestKind` (Recurring/SideQuest — side quests never feed
attributes), `QuestType` (Maintenance/Progression/Outcome), `Cadence`
(Daily/Weekly/Monthly), `CompletionRecord`, `ReminderSchedule` (time + days, owned by
the quest; the engine exposes "next due reminders" as pure data — actual OS scheduling
lives in :app), `Attribute` + accrual math (recurring completions only), consistency
scoring with rest-day absorption, milestone thresholds, diminishing returns for
progression quests, and the `QuestRepository` / `HealthDataSource` / `Clock` interfaces.

**Review prompt (after you've written it):**
```
[PREAMBLE]

I have written the :core domain layer myself and want a rigorous review. Do NOT
rewrite it — review it.

Focus on: (1) edge cases in time handling — midnight boundaries, timezone changes,
DST, the user travelling; (2) correctness of consistency scoring around rest-day
absorption — construct adversarial completion histories and trace my code against
them; (3) API design — is anything leaking mutable state or Android assumptions;
(4) missing test cases — list concrete scenarios my test suite does not cover.

Output: a numbered list of findings, each with severity (bug / design smell / nit),
the exact location, and a suggested test that would catch it. Do not produce
replacement implementations unless a finding is severity: bug.
```

---

## Phase 2 — Data layer [DELEGATE]

**Prompt:**
```
[PREAMBLE]

Task: Implement the :data module. The :core module defines these interfaces and
models: [PASTE your QuestRepository interface + the domain models it references].

- Room database with entities mirroring the domain models, DAOs, and mappers
  (entity ↔ domain). Domain models never gain Room annotations — mapping only.
- Implement QuestRepository backed by Room. All reads exposed as Flow<> where the
  interface specifies.
- Migrations: start at version 1 with exportSchema = true and schema files committed.
- Include an in-memory Room test suite covering: CRUD for quests, completion-record
  insertion and querying by date range, and one migration test scaffold.

Definition of done: all repository interface methods implemented and tested against
in-memory Room; no :data type appears in any :core file.
```

---

## Phase 3 — The daily loop UI [DELEGATE, review the feel yourself]

**Goal:** today's quest list, one-tap completion, the "done for today" state.

**Prompt:**
```
[PREAMBLE]

Task: Implement the main screen of the app in :app.

Behaviour:
- Shows today's quests grouped by cadence (daily first). Each quest: title, type
  badge, completion state, one-tap complete for manual quests.
- Below the recurring quests: a "Side Quests" section for one-off tasks (QuestKind.
  SideQuest). Completing one gives the tick and lifetime-count credit but no
  attribute feedback — the UI must visibly not treat it as growth.
- Quick-add: a persistent FAB opening a single-field capture sheet (title → optional
  reminder time → save as side quest). Target: under 5 seconds from tap to captured.
  Adding a recurring quest is a secondary path from the same sheet ("make this
  recurring…"), not a separate flow.
- Auto-tracked quests show live progress (e.g. steps 5,200 / 8,000) from a
  QuestProgress flow the ViewModel exposes — stub the health source for now with a
  fake emitting scripted values.
- When all of today's quests are complete, the list is replaced by a calm
  "done for today" state — intentionally NOT a prompt to do more. No infinite feed.
- Completing a quest shows identity-framed feedback (e.g. "4th week running —
  consistent"), NOT "+XP" toasts. Pull the copy from a CompletionFeedback value the
  :core engine produces; do not invent reward copy in the UI layer.
- MVVM: QuestListViewModel exposing a single UiState data class via StateFlow;
  events via a sealed interface. No business logic in the ViewModel — it calls
  :core use-cases only.

Include: preview composables for each state (loading, list, all-done, empty), and
ViewModel unit tests using Turbine covering the event → state transitions.
```

---

## Phase 3b — Reminders & notifications [DELEGATE]

**Goal:** user-scheduled reminders per quest, delivered reliably, with no-shame copy.

**Prompt:**
```
[PREAMBLE]

Task: Implement quest reminders in :app.

- Each quest can have a ReminderSchedule (time + days for recurring; one-shot for
  side quests), edited from the quest's detail sheet. :core owns the schedule model
  and exposes next-due computation [PASTE signatures]; this phase owns OS scheduling
  and delivery only.
- Use AlarmManager (exact alarms where permitted; degrade gracefully to inexact —
  handle the SCHEDULE_EXACT_ALARM permission flow and the Android 13+ POST_
  NOTIFICATIONS runtime permission, both skippable with the feature simply off).
- Reschedule correctly on boot (BOOT_COMPLETED) and on timezone/time change.
- Notification content: quest title only, at the user-chosen time. Tapping opens the
  quest. Manual quests get a "Complete" action button on the notification itself.
- HARD RULE: no notification may fire that the user did not schedule. No streak
  warnings, no re-engagement pings, no "you haven't done X yet". If a quest is
  already completed for the period, its reminder is suppressed.
- Tests: next-due computation edge cases delegated to :core tests; here, test the
  scheduling adapter with a fake alarm scheduler (schedule, cancel on completion,
  reschedule on boot).
```

---

## Phase 4 — Health Connect integration [DELEGATE the plumbing, YOU own the policy]

The sync *policy* (when to reconcile, how to treat gaps) is product-critical — decide
it yourself, then hand the model the plumbing.

**Prompt:**
```
[PREAMBLE]

Task: Implement :health — a Health Connect adapter implementing this :core interface:
[PASTE your HealthDataSource interface].

- Read steps, distance, floors, active energy, exercise sessions, and sleep sessions
  (sleep may be absent — represent absence explicitly, not as zero).
- Permission flow: expose a suspend function returning granted/denied/unavailable
  (Health Connect not installed). The UI decides what to do — no UI in this module.
- Background sync: a WorkManager periodic worker (15 min, batched) plus an
  on-app-open reconciliation covering the last 48h. Reconciliation policy:
  [PASTE YOUR POLICY — e.g. "re-read the last 48h window and upsert; completion
  decisions are recomputed by :core, never by this module"].
- CRITICAL: any read failure, permission loss, or missing provider results in
  HealthData.Unavailable — never an exception crossing the module boundary, never a
  zero that could be mistaken for real data.
- Tests: fake Health Connect client; cover the failure→Unavailable paths explicitly.
```

---

## Phase 5 — Progression & profile screen [DELEGATE UI, YOU own the math]

**Prompt:**
```
[PREAMBLE]

Task: Implement the profile/progression screen in :app.

- Renders the attribute profile (Body/Mind/Social/Discipline): current title per
  attribute (e.g. "Consistent — Body"), progress toward next milestone, and the
  evidence behind it ("41 sessions over 12 weeks").
- A "lifetime" section: total completions (never resets), completed-chapters archive
  (retired quests).
- Next-milestone visibility: each attribute shows "N more completions to [next
  title]" — values come from :core's milestone functions [PASTE their signatures].
- Nothing on this screen may display loss, decay, or a broken streak. Consistency
  is shown as a rate over the chosen window, not as a breakable chain.
- Previews for: fresh user (all attributes level 0), balanced mid-game user, and a
  lopsided user (high Body, untouched Social) — the lopsided case must read as
  information, not shame; propose neutral copy.
```

---

## Phase 6 — Onboarding [DELEGATE]

**Prompt:**
```
[PREAMBLE]

Task: Implement onboarding in :app. Hard requirement: a new user reaches a working
quest list in under 60 seconds and ≤ 4 taps.

Flow: (1) one screen, three preset "starting classes" [PASTE your three presets and
their quest loadouts], (2) optional Health Connect permission ask — skippable, with
manual mode as the fallback framing, (3) land on the quest list, pre-populated.

- No account creation, no name entry, no attribute explanation, no quest-type
  taxonomy. All of that is discovered later in-app.
- Store the chosen preset via :core's use-case [PASTE signature].
- Include a "later" path on every step. Measure and log (locally) time-to-first-
  quest-list so the <60s claim is testable.
```

---

## Phase 7 — Monthly reflection [YOU design the flow, DELEGATE the implementation]

Spec the 90-second flow yourself first (it's the differentiator). Then:

**Prompt:**
```
[PREAMBLE]

Task: Implement the monthly reflection flow per this spec: [PASTE your flow spec].

Mechanics that must work: the reflection's outcome edits the system — retire quest
(moves to archive), escalate quest (calls :core's escalation function), add quest.
Each edit is applied through :core use-cases [PASTE signatures]; the reflection UI
never mutates state directly.

- Trigger: surfaced (not forced) when a calendar month of history exists.
- Shows the trajectory summary produced by :core [PASTE the summary model].
- Exactly one question, three possible actions, skippable without guilt copy.
- Tests: completing a reflection with each action type produces the correct
  repository state; skipping produces no state change and re-surfaces next month.
```

---

## Phase 8 — Polish & the first insight [DELEGATE selectively]

- Widget (home-screen today-progress — high retention value per the Finch research).
- One hardcoded insight detector in :core (e.g. completion rate vs. morning-walk
  correlation) surfaced occasionally — YOU write the detector, delegate the surfacing
  UI.
- App icon, theming, empty states.
- **Quest Log (v1.5, the deferred "calendar view"):** a journal-style timeline of
  past completions and upcoming scheduled quests — thematically right, and a view
  rather than a loop, so it waits until the core loop demonstrably retains.

---

## Working rhythm with the model

1. One phase per chat. Paste the preamble + the phase prompt + only the interfaces it
   needs. Don't paste the whole codebase — pinned interfaces are the contract.
2. Tests are the acceptance gate: run them yourself before moving on. If a phase's
   tests are thin, that's the first thing to push back on.
3. After each delegated phase, do a self-review pass: read every file, and write one
   sentence per file saying what it does. If you can't, you've delegated understanding
   — stop and study it before continuing. (This is the guardrail for the AI-reliance
   rule you set for yourself.)
4. Keep the design doc and this build plan in the Claude Project knowledge; update
   both when reality diverges. Divergence you don't write down becomes architecture
   nobody decided.

## Open decisions to make before Phase 0 (yours, not the model's)

1. Hilt vs Koin (portfolio-idiomatic vs KMP-ready).
2. The three starting-class presets and their quest loadouts (blocks Phase 6, and
   informs the Phase 1 quest catalogue).
3. Rest-day absorption rule, precisely (blocks Phase 1).
4. Milestone thresholds & diminishing-returns curve (blocks Phase 1 — and write this
   math yourself).
