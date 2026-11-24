# Testing summary

Two layers of tests ship with the project.

## 1. JVM unit tests (`app/src/test/java/com/soloadmin/hub/domain`)

Pure Kotlin, no Android framework, so they run in seconds on any machine or in CI.

`app/src/test/resources/legacy_backup_sample.json` is the fixture `BackupCodecTest` decodes; it is
a verbatim export of the legacy `localStorage` shape.

| Test file | Covers |
| --- | --- |
| `MoneyTest` | Paise arithmetic, en-IN grouping, `₹` formatting, messy input parsing, rounding |
| `DateKeysTest` | `yyyy-MM-dd` parsing, 12-hour display, month keys, weekday indexing, time maths |
| `FinanceCalculatorTest` | Headmaster due / recovered, personal spend, net balance, category totals, attendance counters, ledger total |
| `PunchEngineTest` | Punch in/out, late detection, re-punch, break accumulation, running break minutes |
| `BackupCodecTest` | Legacy `localStorage` JSON decode, native envelope round-trip, BOM handling, quoted amounts, malformed files |
| `BackupMapperTest` | Record mapping, validation, duplicate detection, skip reasons |
| `ReportWriterTest` | Duty history CSV, reminder list CSV, attendance/expense/finance/key-date CSVs |
| `DashboardStateTest` | Weekly-off detection, monthly net balance, key-date attention window (legacy parity) |

**Current result: 82 tests, 0 failures** — confirmed by `./gradlew testDebugUnitTest` in GitHub
Actions (job *Build & unit tests*, run on a clean Ubuntu runner with JDK 17 and SDK 35).

Run them with:

```bash
./gradlew testDebugUnitTest
```

The HTML report lands in `app/build/reports/tests/testDebugUnitTest/index.html`.

## 2. Instrumentation tests (`app/src/androidTest/java/com/soloadmin/hub`)

These need a device or emulator (they exercise real Room and Compose).

| Test class | Covers |
| --- | --- |
| `DatabaseFlowTest` | Attendance upsert per date, duty ↔ calendar link, month query scoping, full snapshot → wipe → restore round-trip, duplicate-free re-import, stored financial figures |
| `AppLaunchTest` | App launches, home header renders, bottom bar reaches all five tabs, settings screen opens |

Run them with:

```bash
./gradlew connectedDebugAndroidTest
```

## What is verified where

* **Financial maths, date logic, punch rules, import validation** — verified on the JVM on every build.
* **Database behaviour and navigation** — written against the same APIs the app uses; run
  `connectedDebugAndroidTest` once on your machine before a release build. The sandbox that produced this
  code had no Android SDK (Google Maven and Maven Central were unreachable), so the Compose and Room
  instrumentation tests could not be executed there.
* **PDF export** uses `android.graphics.pdf.PdfDocument`, which only exists on Android; it is exercised
  manually from Settings → Export reports.

## Manual checklist before shipping

1. Punch in → break → punch out on Home; confirm the calendar shows Late/Present as expected.
2. Add an official duty record; confirm the same date flips to *Official Duty* on the calendar.
3. Log a leave range of three days; confirm all three dates show *Leave* with the right type.
4. Add a headmaster expense, mark it recovered, and confirm the Home recoverable strip drops.
5. Import the legacy JSON backup; confirm the "Review import" counts, then re-import the same file and
   confirm everything is reported as already present.
6. Export each CSV and the monthly PDF, then open them.
7. Turn reminders off, then on; confirm the notification permission prompt appears once.
8. Rotate the device on the calendar screen; confirm the selected month is not lost.
