# Solo Admin — Gov Office Hub (native Android)

A complete native rebuild of the "Solo Admin — Gov Office Hub" HTML app. Same features, same
calculations, same data — implemented in **Kotlin + Jetpack Compose + Material 3** with Room,
DataStore and WorkManager. No WebView, no JavaScript, no website in an APK.

> **Status:** feature-complete and building. The JVM test suite (82 tests) passes with 0 failures.
> See [docs/TESTING.md](docs/TESTING.md) for exactly what was verified where.

## What it does

| Screen | Features |
| --- | --- |
| **Home** | Attendance overview, punch in/out, break tracking, monthly attendance summary, official-duty summary, leave summary, headmaster recoverable amount, upcoming key-date alerts, quick actions, monthly financial overview |
| **Attendance** | Monthly calendar with per-date status, present / late / early-going / leave / official duty, day editing, missed-punch entry, punch times, break minutes, month statistics |
| **Official Duty** | Duty records, Full/Half/Quarter day modes, start & end times, office visited, location, remarks, optional photo/document attachment, automatic attendance link |
| **Activity Ledger** | Present days, official duty, CL/EL/Commuted leave, expenses, daily tasks, record totals and a merged timeline |
| **Expenses** | Personal vs headmaster, categories, amount & date, notes, reimbursement/recovery status, pending recoverable, full history, category chart |
| **Pay & Finance** | Salary/bill records, received vs pending, net balance, six-month trend, monthly PDF summary, `en-IN` rupee formatting |
| **Key Dates** | Documents with categories, valid-from/until, validity progress, expiry alerts, renewal reminders, notes |
| **Daily Tasks** | Create, edit, complete, delete; pending vs completed filters |
| **Colleagues** | Profiles with photo, cover, role, card theme, flexible `info` label/value pairs, search, edit/delete |
| **My Profile** | Name, bio, photo, editable details, shift start and weekly off (drives late detection) |
| **Admin Settings** | Theme (system/light/dark), dynamic colour, start screen, haptics, charts, compact cards, reminders + lead time, backups, imports, CSV/PDF exports, sample-data reset, full wipe |
| **Backup & Restore** | JSON export/import for both the legacy HTML format and this app's own format |
| **About** | Version, privacy posture, the ported calculation rules |

## Requirements

JDK 17 · Android SDK 35 · Android 8.0 (API 26) or newer. Gradle is included via the wrapper.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 82 JVM tests
./gradlew connectedDebugAndroidTest  # Room + Compose tests (needs a device)
```

Full instructions, including release signing: **[docs/BUILD.md](docs/BUILD.md)**.

### Build the APK with GitHub Actions

Nothing needs to be installed to get an installable APK:

1. Open **Actions → Android CI → Run workflow** on your branch.
2. When the run turns green, open the run and download the **`solo-admin-apk`** artifact — it
   contains `app-debug.apk` plus a `SHA256SUMS.txt`. Sideload it with `adb install -r app-debug.apk`.
3. Each run also uploads **`unit-test-report`** (the JUnit XML) and a job summary with the test
   counts, so a red build tells you which test failed without downloading anything.

The workflow has three jobs: **Build & unit tests** (compiles, runs the JVM suite, assembles both
APKs), **Lint** (informational, never blocks), and **Publish APK** (only for `v*` tags — it opens a
GitHub Release with the debug APK, the unsigned release APK and `SHA256SUMS.txt`).

If the build fails, the failing compiler/test lines are posted as a comment on
[issue #1](https://github.com/Tushar887427/New-web/issues/1), because raw Actions logs are not
always reachable.

## Migrating your existing data

Your old records live in the browser under `localStorage['solo_gov_office_hub_v6']`. Export that JSON
from the HTML app, then use **Settings → Import from the old HTML app**. The import previews what it
will bring in, reports anything it skips and why, and never duplicates records or overwrites them
without asking.

Step-by-step, including how to extract the JSON with DevTools if the export button is gone:
**[docs/MIGRATION.md](docs/MIGRATION.md)**.

## Architecture at a glance

```
Compose screens → ViewModel (StateFlow<UiState>) → repositories → Room
                                   ↘ Channel events (snackbars)
```

* **Offline-first.** The app has no `INTERNET` permission; every record is in a local Room database.
* **Money as integer paise.** No floating point in any money path.
* **Pure-Kotlin domain logic** (`Money`, `DateKeys`, `FinancialCalculator`, `AttendanceCalculator`,
  `PunchEngine`, `BackupMapper`, `DashboardMath`) — unit-tested on the JVM without an emulator.
* **Manual DI** (`AppContainer`) instead of Hilt: a ~12-object graph that stays readable in one file.
* **Charts drawn with Compose `Canvas`** — no chart dependency, cheap on budget phones.

Details: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Preserved from the original app

These rules are ports, not reinterpretations:

* Late = punch-in later than the profile's shift start (default `09:30`).
* `Present`, `Late` and `Early_Going` all count as one present day; `Outstation` is official duty.
* Headmaster expenses are recoverable until marked recovered; personal expenses never are.
* Net pay = salary received − personal spending.
* Saving an official-duty record marks that date `Official Duty` with the note `Official Duty: <office>`
  and keeps a two-way link to the duty record.
* Break minutes accumulate, with a minimum of one minute per break.
* Key dates raise attention inside a 60-day window; the reminder lead time is configurable.
* Status keys, category names, duty modes, colleague themes and date formats are byte-compatible with
  the legacy JSON, which is what makes the import lossless.

## Project layout

```
app/src/main/java/com/soloadmin/hub/
  data/{database,repository,backup,files,preferences,seed}
  domain/{model,logic}
  di/ notifications/ util/
  ui/{components,navigation,screens,theme}
```

## Build status

`main` and this branch build green in CI: `testDebugUnitTest` (82 tests) and `assembleDebug` both
pass on a clean Ubuntu runner, and tag `v1.0.0` published a GitHub Release with
`solo-admin-v1.0.0-debug.apk` (20.3 MB) and `solo-admin-v1.0.0-release-unsigned.apk` (13.7 MB).

## Still open

* **First-run content.** The app currently seeds the demo records on first launch (matching the HTML
  app, whose "Reset to Sample Data" restores them). If you would rather start with an empty database,
  that is a one-line change in `SoloAdminApp.onCreate` plus the seed flag — say the word and I will flip
  it.
