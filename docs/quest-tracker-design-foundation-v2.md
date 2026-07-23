# Quest Tracker — Design Foundation v2

> Foundational design document for a gamified goal-tracking mobile app. v2 is a full
> rewrite after a research pass into retention data, competitor performance, and
> motivation psychology. It supersedes v1. Every feature decision in the spec and
> architecture phases should trace back to something here; if a proposed feature
> contradicts this doc, stop and reconsider rather than quietly adding it.

---

## 1. One-line concept

A quest-based goal tracker where completing daily/weekly/monthly quests — some
auto-tracked from phone sensors, some manual — builds visible, permanent evidence of who
the user is becoming. RPG-flavoured, psychology-grounded, honest about growth.

## 2. The psychological spine (everything hangs off this)

**The app's job is to manufacture evidence of identity, and make sure the user sees it.**

Grounding (the two frameworks that matter):

- **Self-Determination Theory (Deci & Ryan):** durable motivation requires autonomy
  (I chose this), competence (I'm getting better), and relatedness (I'm connected to
  others through it). Every mechanic must serve at least one and violate none.
- **Overjustification effect:** external rewards (points, badges) can corrode intrinsic
  motivation for habits people already value. Research on habit trackers found
  non-gamified users ~3.7x more likely to maintain habits after they stopped tracking —
  they internalized the behaviour instead of depending on app rewards. Gamification
  helps most for aversive habits (flossing, mobility work), least for inherently
  rewarding ones (reading, running).

Design consequence: **identity-based rewards, not transaction-based rewards.**
De-emphasize "+15 XP" toasts. Emphasize accumulating proof: "12th week you've trained
3+ times — that's not luck anymore." Attributes are titles earned about yourself, not a
score. Same data, different psychological target: self-concept, not a wallet.
(Self-perception theory: people infer who they are from watching what they repeatedly
do. Make that inference impossible to miss.)

**The contrarian commitment:** this app is designed to *internalize* habits — i.e. to
eventually become unnecessary — rather than to maximize engagement. Commercial apps
resolve this tension in favour of engagement metrics; this is a portfolio-first project
and can afford to be the version that's actually good for the user. "The habit tracker
designed to graduate you" is the thesis.

## 3. Market reality (what the evidence says)

The gamified-habit space is saturated (Habitica, Habit Quest, Habit Hunter, HabitForge,
MainQuest all do quests + XP + levels + character). Key retention facts that shaped v2:

- ~67% of users abandon gamified habit apps by week 4. Heavy gamification (avatars,
  quests, boss battles) shows high initial engagement then steep drop-off; users report
  feeling "trapped by the game." Gamification is personality-dependent — for a large
  minority (~41% in one survey) it actively harms habit formation.
- **Daylio** (barely gamified; simple mood entry + long-term pattern visualization) has
  the best long-term retention in the category (~40% D30). **Finch** (fully gamified but
  *gentle* — pet-care framing, no streak-loss punishment, warm re-entry after missed
  days) leads on active users. **Me+** (heavy UA-driven growth) retains worst (~3% D30).
- Lesson: winners are either radically simple, or gamified-as-care. Complexity and
  shame are the killers. Fitness apps also report ~40% step-tracking failure rates from
  Health Connect/HealthKit background sync breakage — when tracking is wrong, the whole
  gamified system loses trust.

**Genuine gaps this app targets:** (1) auto-tracked quest completion (competitors are
all manual check-off); (2) identity/evidence framing + a reflection loop that treats
growth as a question, not a counter — nobody does this well; (3) variable *insight*
from the user's own data (Daylio's pattern magic, made proactive).

**Strategic lane (decide deliberately):** the market forks into a gentle/self-care lane
(Finch: ~75% women 25–35, competition-averse) and an RPG "level up your life" lane
(e.g. Life Reset: masculine aesthetic, competitive psychographic). This app sits in the
RPG lane by temperament — accept that it's a narrower audience and design coherently
for it, rather than drifting between lanes.

## 4. Progression model

### 4.1 Multi-dimensional attributes — as identity, not score

Growth spread across life-area attributes (working set: **Body, Mind, Social,
Discipline** — final names TBD in spec). A run feeds Body; study feeds Mind. The profile
shows *shape* ("level 30 Body, level 4 Social" is information about a life). Frame
attribute levels as earned titles ("Consistent — Body"), surfaced with the evidence
behind them.

### 4.2 Three quest types (autonomy rule: the user picks; the app never forces escalation)

| Type | Growth axis | Example | Escalates? |
|------|-------------|---------|-----------|
| **Maintenance** | Consistency | "1 hr gym", "in bed by 11" | Never — showing up is the win |
| **Progression** | Rising number | "walk Xk steps" | Yes, with diminishing returns on farmed levels |
| **Outcome** | Separate measured result | fixed "1 hr gym" → Strength metric climbs | Quest fixed; outcome grows |

Attributes accrue from all three types, so a pure-maintenance user still grows visibly.
Consistency is first-class growth (because it is). Outcome-type logging (sets/reps) is
optional depth — never gate the growth feeling behind data entry most people won't
sustain.

### 4.3 Cadence mechanics (the MapleStory lessons, kept)

- **Cumulative & permanent:** gains bank forever, never decay. Hard rule: *no mechanic
  anywhere may take something away from the user* (loss aversion: losses hurt ~2x).
- **Time-gated:** dailies/weeklies/monthlies are different progression tracks on
  different clocks; something is always near a payoff. The app actively tells the user
  when they're **done for today** — anti-compulsion by design.
- **Always-visible next milestone:** "3 more completions to the next Discipline rank."
  Milestone granularity is a spec-phase decision (frequent enough to feel alive, coarse
  enough to feel earned).

### 4.4 How gains are *felt* (honest-payoff model)

- ❌ No simulated payoff as the engine (in-app bosses/zones/loot/gear/pets economy).
  Fake destinations undercut the thesis and lose to Habitica anyway. Cosmetic flair is
  allowed only as *celebration of a real gain* (evolving avatar, rank title).
- ✅ **Escalating real quests** — the honest "harder boss": the unlock is a harder
  version of the real activity you can now do.
- ✅ **Legible trajectory** — reflect real change back: "3 months ago the walk quest was
  3k and hard; it's 8k and routine."
- 🌟 **Variable insight** (the one ethical use of variable reward): occasionally and
  unpredictably surface a true pattern from the user's own data ("you complete 2x more
  quests on days you walk before 9am"). Anticipation of uncertain-but-genuine insight
  drives return visits without loot-box mechanics. No competitor has this.

### 4.5 Side quests (one-off tasks) — the identity firewall

The app also serves as a trusted capture point for one-off life admin ("call plumber",
"renew rego") — a real need for forgetful users. But one-offs are life admin, not
identity evidence, and if they fed attributes the growth system would become farmable
(add ten trivial todos → Discipline inflates → the metric means nothing). Habitica's
habits/dailies/todos soup is the cautionary tale.

The RPG framing provides the firewall for free:

- **Recurring quests** = dailies/weeklies/monthlies. The progression system. Feed
  attributes.
- **Side quests** = one-off tasks. Distinct in the model (`QuestKind: Recurring |
  SideQuest`) and the UI (separate section under today's list). They count toward
  lifetime completions and give the satisfying tick, but **never feed attributes**.

Capture friction is the make-or-break: quick-add (main screen or widget → title →
optional reminder time → done) must take under ~5 seconds or it won't be used.

## 5. Failure design (first-class, not an afterthought)

Churn happens in weeks 2–4 when users miss days. The decisive psychology is the
**what-the-hell effect** (one lapse → "already blown it" → collapse) and the finding
that self-compassion after a lapse predicts faster return than self-criticism.

Rules:

1. **Never headline a broken streak.** Lead with lifetime completions (can't break).
2. **Warm re-entry:** after absence, the first screen is Finch-style welcome-back
   ("one small quest restarts the rhythm"), never a red calendar of missed days.
3. **Rest-day absorption:** occasional misses are automatically neutral in
   consistency scoring — not failed, not punished.
4. **Tracking failures never break anything.** Given ~40% background-sync failure
   rates, a missed auto-sync must never mark a quest failed or dent consistency.
   One-tap manual fallback everywhere. Trust, once broken by wrong data, doesn't
   return — treat sync reliability as the hardest engineering problem in v1, because
   it is.

## 6. The reflection loop (the differentiator — build it with teeth)

Not a journaling prompt (gets skipped). A **90-second monthly ritual**:

1. Show the user their own trajectory (quest escalations, consistency %, attribute
   shape, what barely moved).
2. Ask **one** question: "which of these still points where you want to go?"
3. The answer **does something**: retire a quest, escalate one, add one. Reflection
   that edits the system is an investment loop (Hook model: investment converts
   external triggers to internal ones); reflection that's just writing is churn-bait.
4. Retired quests go to a visible **"completed chapters" archive** — accumulated
   history the user owns (endowment effect + genuine self-knowledge).

The monthly track ties to a goal the *user* defined as mattering, so slow accumulation
climbs toward something chosen — the hedge against Maple's "dailies for a character I
no longer enjoy" failure mode.

## 7. Onboarding (choice overload is the first boss)

Retention data: setup over ~1 minute bleeds users badly. The v1 taxonomy
(attributes × 3 quest types × 3 cadences × Health Connect setup) is a cognitive wall if
front-loaded.

- First session = **one tap to a working app**: pick a preset "starting class"
  (e.g. Body-focused / Mind-focused / Balanced), each pre-loaded with 3–4 sensible
  quests, step auto-tracking on by default.
- The quest-type taxonomy, custom quests, and attribute tuning are **discovered
  progressively** in week 1–2, from inside a working loop. Complexity is earned by
  retention, never front-loaded.

## 8. Reminders & notifications (v1 — serves the core loop)

Psychology: **implementation intentions** — "at 7pm I train" dramatically outperforms
"I'll train today" because the behaviour is attached to a concrete cue. For a forgetful
user the cue *is* the product. Notifications are also the external trigger that brings a
user back before the habit is internalized (Hook model, stage 1).

Rules:

1. **User-scheduled, always.** Reminders are set per quest by the user (time, days).
   The app never decides to nag on its own.
2. **No-shame copy, enforced.** A reminder is the quest's name at the chosen time
   ("Gym hour 🗡️") — never "you haven't done X yet!" or any guilt framing. This is the
   §5 no-punishment law applied to notifications.
3. **Local only.** AlarmManager/WorkManager scheduling; no backend, fits v1 scope.
4. Side quests get optional one-shot reminders at capture time (part of quick-add).

Calendar view is **deferred to v1.5** — it's a view, not a loop, and nothing in the
retention evidence says calendars drive habit apps. The thematically-right version is a
**Quest Log**: past completions + upcoming scheduled quests as a journal-style timeline.

> **Amendment (2026-07, Phase 7b):** the Quest Log ships in v1 after all, as the home
> of written journal entries (gratitude/logging) interleaved with completions — a write
> surface needs a reading surface. This does not soften the §6 warning: free writing is
> still never the *reflection* mechanic. Instead it feeds the loop — saving an entry
> auto-completes any journal-linked quest (writing becomes evidence), entries are
> editable/deletable while the completions they banked are permanent (§5), and a quest's
> tick keeps working with no writing required (no friction, no punishment).

## 9. Social (deferred; design principles locked now)

Not in v1 (avoids backend/auth/sync entirely). When it comes:

- **Relatedness before comparison.** First social feature is Finch-style "Good Vibes"
  peer support (send encouragement) — the strongest retention social mechanic in the
  category, zero comparison cost, cheap to build.
- **Leaderboards only as a fair race** (social comparison theory: upward comparison to
  *similar* others motivates; to distant others, demoralizes). Never rank composite
  self-defined "growth XP" against friends — that compares incommensurable lives and
  punishes the healthy maintenance user. Acceptable designs: (a) a single objective
  metric (weekly steps — the Strava/Fitbit model); (b) **% of own commitments met** —
  self-referenced competition where the 1-hr-gym person and the 15-quest grinder can
  both hit 100% (novel, and dissolves the pathology); (c) Duolingo-style small
  rotating opt-in leagues with weekly resets.
- Everything opt-in.

## 10. What auto-tracks (phone-only, no wearable)

Via **Health Connect** (Android; Google Fit is deprecated) / **HealthKit** (iOS later).

- **Reliable phone-only:** steps, distance, floors, active energy (rough), workouts
  recorded by any other app.
- **Conditional:** sleep — phones don't track it by default; treat as "lights up if a
  sleep source exists," manual fallback, soft nudge to connect a source.
- **Not phone-only (don't design around):** continuous HR, HRV, SpO2, sleep staging.
- Manual entry covers everything else — expected and fine. Maintenance quests are
  one-tap.

> ⚠️ Verify before spec hardens: Health Connect minimum Android version / install
> requirements (sets the device floor).

## 11. Scope (v1)

- **Android-first, KMP later.** Portfolio + learning now; possibly real users later.
- **Local-only. No backend, no accounts, no cloud sync, no social.**
- **Cut to the bone:** the retention evidence punishes complexity. v1 = one coherent
  loop (preset onboarding → quests with auto/manual completion → identity-framed
  progress → failure-safe streaks → first monthly reflection). Attributes can ship as
  a simple 4-way split; escalation math, variable insight, and deep milestone tuning
  can follow once the loop demonstrably retains. Depth can be added; a user bounced
  off week-one complexity cannot be won back.
- **Rejected (probably forever):** gear/loot economies, pets, in-app combat,
  inventory, streak-loss punishment, forced escalation, ranked composite-XP
  leaderboards.

### 11.1 Architecture rule that keeps Android→KMP cheap

All game logic (quest engine, attribute math, progression, models) is **pure Kotlin in
a `:core` module with no Android imports**, from day one. Platform-specific things
(Health Connect now, backend client later) sit behind interfaces in `:core`,
implemented in the Android module — the same seam KMP's `expect`/`actual` will use.
Migration later = make `:core` multiplatform, Room→SQLDelight (or Room KMP), write the
iOS health adapter. Days, not weeks. Also reads well in interviews.

## 12. Guiding principles (the shortlist)

1. Manufacture evidence of identity; make sure the user sees it.
2. Nothing is ever taken away from the user.
3. The app tells you when you're done for the day.
4. Failure is met with a doorway, not a mirror.
5. Wrong data is worse than no data — manual fallback everywhere.
6. Complexity is earned by retention, revealed progressively.
7. Comparison only ever against one's own commitments (or a genuinely fair race).
8. Side quests tick boxes; only recurring behaviour builds identity.
9. Reminders are cues the user chose, never nags the app invented.
10. Success is the user needing the app less.

---

## Open questions / TODO for the spec phase

- [ ] Finalize attribute set and names (Body/Mind/Social/Discipline working set).
- [ ] Define the three preset "starting classes" and their quest loadouts.
- [ ] Milestone granularity (completions per rank tick).
- [ ] v1 quest catalogue: which auto-tracked + which manual ship first.
- [ ] Verify Health Connect min Android version.
- [ ] Design the rest-day absorption rule precisely (how many, what cadence).
- [ ] Spec the monthly reflection screen (the 90-second flow, and what "retire /
      escalate / add" does mechanically).
- [ ] The 3–5 core screens and their states.
- [ ] Decide what v1 ships of variable insight (even one hardcoded pattern-detector
      may be enough to prove the concept).
- [ ] Design the quick-add flow (target: <5s from intent to captured side quest).
- [ ] Decide whether starting-class presets ship with suggested reminder times
      (pre-filled but editable) or none.
