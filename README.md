# Quest Tracker

A gamified goal-tracking Android app. Design rationale and build plan live in
[`docs/`](./docs).

## Module graph

```
:app (Compose UI, ViewModels, Hilt, nav)
  ├─► :data (Room, DAOs, mappers) ──► :core
  └─► :health (Health Connect adapter, WorkManager sync) ──► :core

:core = pure Kotlin, zero Android imports.
```

`:core` never imports `android.*` / `androidx.*` — enforced by the `verifyNoAndroidImports`
Gradle task (part of `check`).

## Build

```shell
./gradlew build
```
