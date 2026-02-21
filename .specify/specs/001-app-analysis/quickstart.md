# Quickstart: Flow — Build, Run & Validate

**Branch**: `001-app-analysis` | **Phase**: 1 | **Date**: 2026-02-20

---

## Prerequisites

| Tool | Required Version | Check |
|---|---|---|
| Android Studio | Ladybug (2024.2.1) or newer | `Help → About` |
| JDK | 17 (bundled with Android Studio) | `java -version` |
| Android Gradle Plugin | 8.5.2 (pinned in `libs.versions.toml`) | auto |
| Kotlin | 2.0.20 | `libs.versions.toml → kotlin` |
| Device / Emulator | Android 7.0+ (API 24+) | AVD Manager |

---

## 1. Clone & Open

```bash
git clone <repo-url>
cd Flow
git checkout 001-app-analysis
```

Open `d:\Android\Flow` in Android Studio via **File → Open**.

---

## 2. Build

```bash
# From repo root (Windows)
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` with APK at
`app/build/outputs/apk/debug/app-debug.apk`.

**Known first-build behaviour**: KSP generates Hilt component sources and
Room DAOs. A clean first build takes ~2 minutes on a mid-range laptop.

---

## 3. Install on Device / Emulator

```bash
.\gradlew installDebug
```

Or drag `app-debug.apk` to a running emulator.

---

## 4. Validate Core Flows

After install, perform these manual checks in order:

### 4a. Onboarding (FR-026, FR-027)
1. Freshly installed app (or cleared data) → launch → onboarding tutorial appears automatically.
2. Complete all tutorial steps.
3. Home screen shows pre-populated demo tasks (including ≥1 recurring task).
4. Kill and relaunch app → onboarding does NOT appear again.

### 4b. Task CRUD & Colours (FR-001–006, FR-004)
1. Tap the `+` button → fill title only → confirm task appears with default colour.
2. Long-press a task → edit panel opens → change title + set a past target date → save.
3. Confirm card turns orange (overdue).
4. Tap card → set status to In Progress → card turns yellow.
5. Tap card → set status to Completed → card turns green.
6. Confirm daily progress indicator (top bar) increments to reflect the completion.

### 4c. Recurring Task & Streak (FR-007–010)
1. Add a new task → enable recurring toggle → save.
2. Mark it complete for today.
3. Navigate to the task's streak view → streak shows `1`.
4. *(Optional)* Change device date to tomorrow → mark complete again → streak shows `2`.

### 4d. Heat Map (FR-011–016)
1. Open the Analytics / History screen.
2. Verify today's cell is coloured (matches completion activity from 4b/4c).
3. Scroll horizontally → weekday labels (Mon–Sun) remain fixed on the left.
4. Return to a task → edit its start date to yesterday → navigate back to heat map → yesterday is now coloured, today's cell for that task is cleared.

### 4e. Focus Timer (FR-017–021)
1. Open timer panel → set duration to 1 minute via custom input.
2. Tap Start → countdown begins.
3. Tap Pause at ~30s → display freezes showing remaining seconds.
4. Tap Resume → countdown continues from paused time.
5. Let timer reach 0 → audible alert plays → "Time is up" message visible.
6. Timer display does NOT disappear.
7. Tap Reset → timer returns to 1:00.

### 4f. Daily Progress Indicator (FR-022–023)
1. Add 3 tasks with today's start date (if not already present).
2. Observe progress = 0% (or appropriate value).
3. Complete 1 task → progress updates immediately.
4. Complete all 3 → progress = 100%.

---

## 5. Build Variant Notes

| Variant | Command | Notes |
|---|---|---|
| Debug | `assembleDebug` | Logging enabled, no ProGuard |
| Release | `assembleRelease` | Requires signing config in `local.properties` |

`local.properties` is gitignored. For release signing add:
```properties
storeFile=<path to keystore>
storePassword=<password>
keyAlias=<alias>
keyPassword=<key password>
```

---

## 6. Running Tests

```bash
# Unit tests
.\gradlew test

# Instrumented tests (requires connected device / emulator)
.\gradlew connectedAndroidTest
```

---

## 7. Database Inspection

In Android Studio open **App Inspection → Database Inspector** while
app is running on a debug device. Tables visible:
- `tasks`
- `task_logs`
- `daily_progress`

DataStore preferences are binary; inspect via `adb` if needed:
```bash
adb shell run-as com.flow cat /data/data/com.flow/files/datastore/settings.preferences_pb
```
