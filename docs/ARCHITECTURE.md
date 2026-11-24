# Architecture

## Stack

| Concern | Choice | Why |
| --- | --- | --- |
| Language / UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3 | Native, no WebView, no JS |
| Build | Gradle 8.11.1, AGP 8.7.3, Kotlin DSL + `libs.versions.toml` | Standard Android Studio import |
| Persistence | Room 2.6.1 (offline-first, single source of truth) | Typed queries, transactions, migrations |
| Preferences | DataStore Preferences 1.1.1 | Theme, start screen, reminders, haptics |
| Async | Coroutines + Flow, `StateFlow` per screen | No callbacks in the UI layer |
| Navigation | Navigation Compose 2.8.5 | 5 tabs + 8 pushed routes, state restored per tab |
| Reminders | WorkManager 2.10.0, unique daily work | Survives reboots, no foreground service |
| Images | Coil 2.7.0 | Local files only; no network permission exists |
| Charts | Compose `Canvas` | No extra dependency, cheap on low-end phones |
| DI | Manual `AppContainer` | No annotation processor; graph readable in one file |

Deliberate omissions: no Hilt (a 12-object graph does not justify KAPT/KSP), no Retrofit/OkHttp (the app
has no `INTERNET` permission), no Firebase, no analytics.

## Layers

```
UI (Compose screens)  ── observes ──▶  ViewModel (StateFlow<UiState>)
        │                                      │
        │ events (Channel → snackbar)          │ suspend calls
        ▼                                      ▼
Components / theme                    Repositories (Room + files)
                                               │
                                               ▼
                                        DAOs → SQLite
```

* **Screens** are stateless apart from local dialog/sheet visibility. All data arrives through a single
  immutable `*UiState` data class, so a recomposition never re-queries the database.
* **ViewModels** build that state in one `combine(...)` over repository flows plus local form state.
  Form state (`*FormState`) is separate from the loaded data so typing never rebuilds the list.
* **Repositories** are the only layer that touches Room, and they map entities to domain models at the
  boundary. Money is stored as integer **paise** — no floating point anywhere in the money path.
* **Domain logic** (`Money`, `DateKeys`, `FinancialCalculator`, `AttendanceCalculator`, `PunchEngine`,
  `BackupMapper`) is pure Kotlin with no Android imports, which is why it is unit-tested on the JVM.

## Database

Eleven tables: `profile`, `profile_info`, `attendance_records`, `work_sessions`,
`official_duty_records`, `expenses`, `finance_records`, `key_dates`, `daily_tasks`, `colleagues`,
`colleague_info`.

* `attendance.dateKey` is the primary key (`yyyy-MM-dd`), which makes "one record per day" a database
  guarantee rather than a UI convention.
* `attendance.officialDutyId` and `official_duty.id` form the link: saving a duty record upserts the
  calendar row as `Outstation`; deleting the duty clears the reference.
* Foreign-key cascades keep `colleague_info` / `profile_info` rows with their parent.
* Money columns are `INTEGER` paise; legacy string amounts are parsed once at import.
* Migrations live in `data/database/Migrations.kt` and are wired through `addMigrations(...)`; the schema
  directory is emitted by KSP so every version is diffable.

## State and lifecycle

* `uiState` uses `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` — the database is not
  observed while the screen is off.
* Snackbar messages travel through a `Channel` (`receiveAsFlow`), so they fire once per event and never
  re-fire on rotation.
* The Home screen re-resolves "today" on resume (`refreshToday()`), so an app left open overnight shows
  the correct date instead of yesterday's.
* Destructive actions (delete expense/duty/key date/task/colleague, wipe, reset) always confirm first.

## Notifications

`ReminderScheduler` creates two channels (documents, general), enqueues one unique daily
`KeyDateReminderWorker` with a ~9 AM initial delay, and re-schedules when the lead-time or the enabled
toggle changes. The worker queries key dates, filters by the lead window and posts a single summary
notification. Permission is requested only when the user turns reminders on.

## Import / export

`BackupCodec` reads both the legacy `localStorage` dump and the native envelope with hand-written
traversal (no generated serializers), because half-filled dumps are common and one odd field must not
reject a file. `BackupMapper` previews first (imported / updated / skipped with reasons), then applies in
one Room transaction. Legacy PKs are preserved so re-importing a file is a no-op instead of a duplicate.

## Design system

Forest/emerald brand ramp, green-tinted neutrals, 18 dp hairline cards, Inter type scale, and a dark
palette that is defined explicitly rather than derived. The signature surface is `BalanceCard` — a deep
forest panel with a soft radial highlight — used for the payer balance and today's attendance. Charts are
custom `Canvas` drawings so the app stays dependency-light and animates smoothly on budget hardware.
