# Quest Tracker — Build Plan & Model Prompts

> Companion to `quest-tracker-design-foundation-v2.md`. This doc defines the module
> architecture, the build order, and the per-phase specs. **All phases are implemented
> by Claude — this project is not a learning exercise.** The historical [YOU]/[DELEGATE]
> labels now indicate only how product-critical a phase's decisions are: [YOU]-labelled
> phases must have their decisions made explicitly and written down in this doc before
> or while building, never invented silently mid-implementation. The user's role is
> verification between phases (see the working rhythm in CLAUDE.md).

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

## Phase 1 — Core domain models & quest engine [decisions locked below]

**Goal:** the entire game logic, fully tested, no Android in sight.

**Contents:** `Quest`, `QuestId`, `QuestKind` (Recurring/SideQuest — side quests never
feed attributes), `QuestType` (Maintenance/Progression/Outcome), `Cadence`
(Daily/Weekly/Monthly), `QuestStatus` (Active/Retired), `ProgressionTarget`,
`CompletionRecord`, `ReminderSchedule` (owned by the quest; the engine exposes
next-due reminders as pure data — actual OS scheduling lives in :app),
`Attribute` + accrual math (recurring completions only), consistency scoring with
rest-day absorption, milestone thresholds + titles, diminishing returns for
progression quests, `CompletionFeedback` (identity-framed completion copy, produced
here so the UI never invents reward copy), the done-for-today computation
(`TodayBoard`), quest escalation, and the `QuestRepository` / `HealthDataSource` /
`Clock` interfaces.

### Locked decisions (resolves open decisions #3 and #4)

**Time.** `Clock` exposes `now(): Instant` and `zone(): ZoneId` (java.time — pure JVM,
zero Android). Engine functions take dates/zones explicitly so they stay pure. A
completion's credited period (`periodStart`) is frozen at record time in the user's
then-current zone — travelling later never rewrites history. Weeks are ISO
(Monday-start); months are calendar months.

**Attribute accrual (recurring quests only).** Base points per completion:
Daily = 1, Weekly = 3, Monthly = 10 — proportional to the commitment window, dailies
stay the fastest-feeling track. One credit per quest per period (duplicates dedupe).
Side quests never accrue.

**Diminishing returns (Progression quests only).** Completions at a given escalation
level earn full base points for the first 15 completions at that level, then 50%
thereafter. Escalating (any target change bumps the escalation level) restores the
full rate. Never zero, never retroactive — banked points are permanent. Maintenance
and Outcome quests always earn full base: showing up is the win.

**Milestones.** Rank r is reached at cumulative attribute points 5·r·(r+1)/2 →
5, 15, 30, 50, 75, 105… (one daily quest reaches rank 1 in ~5 days, rank 3 around
day 30 — frequent early, earned later). Titles per rank: Unwritten (0), Awakened,
Committed, Consistent, Established, Relentless, Exemplar, then Exemplar II/III…
Ranks never regress (points only ever grow).

**Consistency with rest-day absorption.** Per quest, over the fully-elapsed periods
in a rolling window — the current (in-progress) period and periods before the quest
existed are excluded. Window: Daily = 28 days, Weekly = 12 weeks, Monthly = 6 months.
Rest allowance = ⌈evaluated / 7⌉ for daily, ⌈evaluated / 6⌉ for weekly and monthly
(ceiling, so brand-new quests already absorb a miss — weeks 2–4 are where churn
lives). Score = completed / (evaluated − min(misses, allowance)), capped at 1.0.
Always presented as a rate, never a breakable chain; zero evidence scores a neutral
1.0. Absorption is also what makes sync gaps harmless: the engine only ever sees
completions, so a missing day dents nothing until misses exceed the allowance.

**Done for today.** True when every active recurring quest is completed for its
current period. Side quests never block it — they're life admin with no due date,
not identity work.

**Reminders.** Next-due is pure computation: recurring schedules yield the next
(day-of-week, time) occurrence after `from`, skipping occurrences whose period is
already completed; one-shots fire once and are suppressed after completion.
DST-gap local times shift forward (java.time semantics). OS scheduling is Phase 3b.

**Definition of done:** `./gradlew :core:check` passes — the full engine test suite
plus `verifyNoAndroidImports`.

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

### Phase 3 decisions (locked during implementation)

- **Quick-add recurring quests are always Maintenance.** Progression targets and
  Outcome measures need more thought than a 5-second sheet allows; they belong to the
  quest-management flow (later phase). The sheet captures cadence + attribute only.
- **Quick-add reminder mapping.** Side quest → one-shot at the next occurrence of the
  chosen time (today if still ahead, else tomorrow). Daily quest → every day at that
  time. Weekly/Monthly quest → weekly on the day the quest was created (the sheet has
  no day picker; weekly is the least-surprising nudge for a monthly quest and is
  editable in Phase 3b).
- **"Done for today" sits above the cleared list; it never replaces it.**
  (Amended 2026-07-23 — originally "replaces the recurring list only", as the Phase 3
  prompt specified. In use, ticking the last quest made the whole board vanish
  mid-interaction, which read as clearing one quest deleting the rest.) The banner
  now renders above a "Cleared today" section listing the completed recurring quests
  with their ticks filled — banked gains stay visible. Open side quests stay listed
  below as before; they still never block or dilute "done".
- **Auto-tracked quests keep the manual tick** alongside live progress, honoring the
  design rule that absent health data never blocks completion.

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

### Phase 3b decisions (locked during implementation)

- **:core owns the "when", :app owns the "how".** `nextReminderAfter` (Phase 1) already
  encodes every suppression rule — retired quests, completed periods, spent one-shots,
  DST-gap shift. `ReminderCoordinator` only ever schedules the exact instant it returns,
  so the no-nag law is enforced in one pure, tested place; the AlarmManager adapter never
  decides whether to fire.
- **Alarms stay in sync via a repository collector, not the ViewModel.** The Application
  runs `ReminderCoordinator.keepInSync()`, which re-syncs every quest's alarm on any
  quest/completion change. Completing a quest (manually, from the notification, or via
  auto-tracking) therefore cancels or advances its reminder for free — the daily-loop
  ViewModel stayed untouched, and its tests were unaffected.
- **Exact where permitted, inexact otherwise; both permissions skippable.**
  `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()`, else
  `setAndAllowWhileIdle` (a late nudge still lands in the same period). `POST_NOTIFICATIONS`
  is asked once at launch and never gates anything; a denial just means silent reminders.
  No permission is ever forced.
- **Reminder *editing* UI is deferred to the quest-management/detail flow (a later
  phase).** Quick-add already captures reminders (Phase 3), and there is no quest-detail
  screen yet. This phase delivers the full delivery pipeline — schedule, fire, complete
  from the shade, reschedule on boot/time-change — which is its stated core and its test
  surface. A notification carries its quest id for a future detail deep link; today tapping
  opens the list.
- **Notification content is title-only, low-importance channel, with a "Complete" action.**
  No body text, no category framing, no re-engagement. Manual completion from the shade
  routes through the same `QuestEngine.complete` path the UI uses (manual is never
  second-class).

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

### Phase 4 decisions (locked during implementation)

- **No raw health data is persisted.** Health Connect stays the source of truth for
  metric values; sync converts "target met on day D" into an ordinary
  `CompletionRecord(source = AutoTracked)`. The decision function is pure :core
  (`autoCompletionFor`) — :health reads and persists, but never decides.
- **One reconciliation pass, two triggers.** The 15-min periodic worker and the
  on-app-open refresh run the identical pass: for each active auto-tracked quest,
  re-read day totals for today plus the two previous local dates (≥48h, catching
  late-arriving provider data) and bank completions for periods that hit target and
  aren't already credited. Period dedupe makes re-runs no-ops; already-banked periods
  are skipped without a read.
- **Strictly additive.** Unavailable or below-target days write nothing and revoke
  nothing; a manual completion suppresses auto credit for its period (manual is never
  second-class). Data older than the window is deliberately ignored — rest-day
  absorption already treats those days as neutral, so nothing is lost.
- **`AutoTracking.dailyTarget` is per-day at every cadence:** a weekly/monthly quest
  auto-completes its period when any single day in it reaches the daily target.
- **Failures are silent.** The worker always returns success — the periodic cadence
  is the retry, and there is no user-visible sync-failure state anywhere.
- **Sync is not expedited work.** Pre-S expedited work runs as a foreground service
  whose notification the user never scheduled — collides with the notification rule.
  Plain one-time work starts promptly for a foregrounded app.
- **Live progress is a poll.** Health Connect has no push API for aggregates;
  `observeToday` re-reads today's total every 60s while collected, re-resolving
  "today" each tick so collection across midnight follows the calendar.

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

### Phase 5 decisions (locked during implementation)

- **All profile math and copy come from one pure :core function** (`buildProfile` →
  `ProfileSummary`, surfaced as `QuestEngine.profile`). The ViewModel snapshots
  repository state and reshapes; the UI formats layout only. The no-shame copy rules
  are therefore tested in :core, not enforced by UI discipline.
- **"N more completions to [title]" is the fastest honest path:**
  ⌈points-remaining ÷ best base⌉, where best base is the largest per-completion base
  among the *active* recurring quests feeding the attribute (a weekly completion
  outruns a daily one). Attributes with no active quests fall back to the daily base
  (1 point), so a fresh attribute reads "5 completions to Awakened". Full rate is
  assumed — escalation always restores it, so the estimate is achievable, and a
  pessimistic diminished-rate estimate would read as punishment.
- **Untouched-attribute copy is a plain fact:** "No quests feed Social yet" — the
  lopsided case reads as information, never deficit. With active quests but no
  history: "No completions banked yet". With history: "N completions over M
  weeks/days/months" (span from first credited period to today, rounded up).
- **Consistency is deliberately absent from the profile.** It is quest-scoped, and
  the profile shows accumulated evidence (which only grows); per-quest consistency
  rates belong to the quest-detail flow (later phase). This trivially satisfies the
  no-loss-display rule.
- **Retired side quests are archived too.** The completed-chapters list is simply
  every `Retired` quest (newest first) with its banked completion count; gains stay
  on the attribute cards forever.
- **Navigation is a two-tab bottom bar with plain Compose state** (Today / Profile)
  in MainActivity — no navigation library for two destinations. Revisit when a
  quest-detail screen (and its notification deep link) arrives.

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

## Open decisions

1. ~~Hilt vs Koin~~ — **DECIDED (Phase 0): Hilt** (already in `libs.versions.toml`).
2. The three starting-class presets and their quest loadouts (blocks Phase 6).
3. ~~Rest-day absorption rule~~ — **DECIDED: see Phase 1 locked decisions.**
4. ~~Milestone thresholds & diminishing-returns curve~~ — **DECIDED: see Phase 1
   locked decisions.**
