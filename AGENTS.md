# Repository Guidelines

PeeDetector is an Android 12+ Kotlin/Compose BLE display app.

## Project Structure & Module Organization

- `app/src/main/java/com/example/peedetector/`: application code. Keep BLE I/O in `ble/`, Compose UI in `ui/`, and `MainActivity.kt` thin.
- `app/src/main/res/`: strings, themes, icons, and XML. Use lowercase snake_case names.
- `app/src/test/`: host JVM tests; `app/src/androidTest/`: device/emulator tests.
- `app/build.gradle.kts` configures the app; `gradle/libs.versions.toml` centralizes versions.
- `构建与安装说明.md` covers builds/install; `Doc/` documents BLE handoff.

## Build, Test, and Development Commands

Run these from the repository root:

```powershell
.\run_test.bat                  # JVM tests
.\build_apk.bat                 # Debug build
.\gradlew.bat :app:connectedDebugAndroidTest  # Device tests
.\gradlew.bat :app:lintDebug    # Lint
```

For direct calls, use an ASCII-only `GRADLE_USER_HOME` to avoid the documented Windows worker failure. JDK 17 is required; debug APKs go to `app/build/outputs/apk/debug/`.

## Coding Style & Naming Conventions

Follow Kotlin official style (`kotlin.code.style=official`) with four-space indentation. Use PascalCase for classes, camelCase for functions/properties, and UPPER_SNAKE_CASE for constants. Keep protocol parsing independent of BLE transport and UI. Centralize dependencies and versions in the catalog. Java compatibility is 11; no formatter or coverage gate is configured.

## Testing Guidelines

Use JUnit 4 for host tests and AndroidX/Espresso for device tests. Mirror production packages and suffix classes with `Test`. Cover split CRLF, multiple lines, empty input, reset, and bounded buffers. Run relevant tests before handoff.

## Commit & Pull Request Guidelines

Commit history convention: 待核实; this checkout has no Git metadata. If initializing Git, prefer imperative messages such as `ble: handle split CRLF notifications`. Do not commit `local.properties`, generated builds, IDE state, secrets, or personal paths. PRs should explain behavior, list verification, link issues, and include UI/device evidence.

## Agent Context Rules

At handoff, confirm the root and applicable rules. Use `rg` or headings to find the narrowest relevant section, then read only needed context. Avoid generated output, logs, secrets, and unrelated personal data; expand only for missing evidence or conflicts.

Keep one authoritative source per fact. Use `PROJECT_INDEX.md` for routing when present; keep state in `docs/context/NOW.md`, mappings in `MAP.md`, procedures in `RUNBOOK.md`, decisions in `DECISIONS.md`, risks in `RISKS.md`, and history under `history/`. Update only changed in-scope facts, preserve unrelated work, and distinguish facts, plans, decisions, tests, installation, acceptance, and release. Documented commands are not execution evidence.
