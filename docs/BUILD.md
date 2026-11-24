# Building Solo Admin

The project is a standard Android Gradle project (Kotlin DSL + version catalog). Nothing in it is
tied to a particular IDE.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 (Android Studio bundles one) |
| Android Studio | Ladybug / Meerkat or newer (any version with AGP 8.7 support) |
| Android SDK | Platform 35, Build-Tools 35.0.0 |
| Gradle | Wrapper included (8.11.1) — do not install Gradle separately |
| Device / emulator | Android 8.0 (API 26) or newer |

## 1. Open or build from the command line

From the project root:

```bash
# Debug APK (installable, uses .debug application id suffix)
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned until you add a keystore)
./gradlew assembleRelease
```

Install the debug build on a connected device:

```bash
./gradlew installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## 2. Open in Android Studio

1. **File → Open** and pick the project folder (the one containing `settings.gradle.kts`).
2. Let the first Gradle sync finish. It downloads AGP, Kotlin, Compose and Room — roughly 600 MB on a
   first run.
3. Pick the `app` run configuration and press **Run**.

If the SDK location is not detected, Android Studio writes `local.properties` for you. From the command
line, create it yourself:

```properties
sdk.dir=/path/to/Android/Sdk
```

## 3. Signing a release build

Generate a keystore once:

```bash
keytool -genkey -v -keystore solo-admin-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias solo-admin
```

Add `keystore.properties` at the project root (never commit it):

```properties
storeFile=solo-admin-release.jks
storePassword=…
keyAlias=solo-admin
keyPassword=…
```

Then add a `signingConfigs` block to `app/build.gradle.kts` inside `android { }`:

```kotlin
val keystoreProps = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

signingConfigs {
    create("release") {
        if (keystoreProps.isNotEmpty()) {
            storeFile = rootProject.file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

`./gradlew bundleRelease` then produces `app/build/outputs/bundle/release/app-release.aab` for Play, and
`assembleRelease` produces an installable APK.

## 3b. CI build (no local toolchain)

`.github/workflows/android.yml` builds and tests on every push. Use **Actions → Android CI → Run
workflow** for an on-demand build, then download the `solo-admin-apk` artifact (it contains
`app-debug.apk` and `SHA256SUMS.txt`). A green run means `testDebugUnitTest` and `assembleDebug`
both passed on a clean machine — the debug APK is directly installable:

```bash
adb install -r app-debug.apk
```

Pushing a `v*` tag additionally runs the **Publish APK** job, which attaches the debug APK, the
unsigned release APK and `SHA256SUMS.txt` to a GitHub Release.

To see why a run failed, read the failing lines posted on
[issue #1](https://github.com/Tushar887427/New-web/issues/1) — the workflow extracts the compiler
and test errors from the build log and comments them there automatically.

## 4. Tests

```bash
./gradlew testDebugUnitTest        # 82 JVM tests, no device needed
./gradlew connectedDebugAndroidTest  # Room + Compose tests, needs a device
```

See [TESTING.md](TESTING.md) for what each suite covers.

## 5. Project layout

```
app/src/main/java/com/soloadmin/hub/
├── MainActivity.kt              entry point, edge-to-edge, notification permission
├── SoloAdminApp.kt              Application: builds the container, seeds, schedules reminders
├── data/
│   ├── database/                Room entities, DAOs, database, migrations
│   ├── repository/              repositories + entity/domain mappers
│   ├── backup/                  JSON codec for legacy and native backups
│   ├── files/                   photo/attachment storage, export files
│   ├── preferences/             DataStore settings
│   └── seed/                    sample data (used by "Reset to sample data")
├── domain/
│   ├── model/                   records, statuses, punch engine
│   └── logic/                   money, dates, financial calculator, backup mapping
├── di/AppContainer.kt           manual dependency graph
├── notifications/               WorkManager reminder worker + channels
├── ui/
│   ├── components/              cards, charts, fields, scaffold
│   ├── navigation/              routes, NavHost, bottom bar
│   ├── screens/                 one folder per feature (screen + ViewModel)
│   └── theme/                   colours, type, shapes, dark mode
└── util/ReportWriter.kt         PDF and CSV report generation
```

## 6. Troubleshooting

* **"SDK location not found"** — create `local.properties` as shown above.
* **Gradle sync fails on a proxy network** — add your proxy to `gradle.properties`
  (`systemProp.https.proxyHost`, `systemProp.https.proxyPort`).
* **Compose compiler mismatch** — the Kotlin and Compose plugin versions are pinned together in
  `gradle/libs.versions.toml`; upgrade them as a pair.
* **Reminders never arrive** — check that notifications are allowed for the app, and that battery
  optimisation is not blocking WorkManager (Settings → Run a reminder check now sends one within a
  minute as a test).
