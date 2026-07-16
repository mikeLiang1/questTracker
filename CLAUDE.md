# Quest Tracker

Design rationale and build plan: [`docs/quest-tracker-design-foundation-v2.md`](docs/quest-tracker-design-foundation-v2.md)
and [`docs/quest-tracker-build-plan.md`](docs/quest-tracker-build-plan.md). Read both before
implementing anything — every feature should trace back to something in them.

## Working rhythm (important)

Build one phase (per `quest-tracker-build-plan.md`'s phase breakdown) at a time, in its own
chat:

1. Implement the phase.
2. **Stop and report what was built — do not start the next phase automatically.** The user
   verifies the output (reads the diff, runs the build/tests, checks the feel of anything UI).
3. Once verified, the user starts a **new chat** for the next phase. This keeps each phase's
   context small and keeps the "self-review pass" the build plan asks for (read every file
   delegated, write one sentence per file saying what it does) honest — it doesn't work if
   context has already scrolled past what was built.

Don't chain multiple phases together in one sitting even if the next phase seems obvious —
ask first.

## Architecture rules (from the design docs — do not violate)

- `:core` is pure Kotlin, zero `android.*`/`androidx.*` imports, enforced by the
  `verifyNoAndroidImports` Gradle task. Module graph: `:app → :data/:health → :core`.
- All time access goes through the injected `Clock` interface, never `System.currentTimeMillis()`.
- Gains are permanent and never decay. A missed health-data sync never marks a quest failed or
  dents consistency scoring. Occasional misses are absorbed as neutral rest days.
- `QuestKind.SideQuest` (one-off tasks) never feed attributes — only `Recurring` quests do.
- Notification copy is only ever the quest name at a user-chosen time. No guilt framing, ever.
