# Architecture — Flow Android App

> Living document. Updated by the Implementation AI after each feature lands.
> The Reviewer AI validates every plan against this file before approval.

---

## System Overview

| Layer | Tech | Key Files |
|-------|------|-----------|
| UI | Jetpack Compose + Material 3 (BOM 2024.09.02) | `ui/screens/*.kt`, `ui/components/*.kt` |
| Navigation | Navigation Compose 2.8.x | `navigation/NavGraph.kt` |
| ViewModel | Hilt-injected ViewModels | `ui/viewmodels/*.kt` |
| Domain | Repository pattern (interfaces + impls) | `data/repository/*.kt` |
| Persistence | Room 2.6.1 (AppDatabase) | `data/local/*.kt` |
| DI | Hilt 2.52 | `di/*.kt` |

## Module Structure

Single-module (`:app`). All source lives under `app/src/main/java/…`.

## Canonical Patterns

### Data Flow

```
Composable → ViewModel (StateFlow) → Repository (suspend / Flow) → DAO → Room SQLite
```

- ViewModels expose `StateFlow<UiState>`.
- Repository methods return `Flow<List<T>>` for observation or `suspend` for writes.
- DAOs are the **only** classes that touch SQL.

### State Management

- `MutableStateFlow` + `.asStateFlow()` in ViewModels.
- `collectAsStateWithLifecycle()` in Composables.
- No `LiveData` — pure coroutines.

### Date/Time

- All persisted dates (except `dueDate` — see below) are **UTC milliseconds normalised to midnight** (`normaliseToMidnight()`).
- **`dueDate` exception**: Tasks created via `AddTaskDialog` store `dueDate` as end-of-day (23:59:59.999 PM = `midnight + 86_399_999`), per US3/DEC-004. Recurring tasks refresh to 12:01 AM / 11:59 PM independently.
- All DAO queries filtering by `dueDate` use inclusive range checks `[dayStart, dayEnd]`, **never** exact-midnight equality. The legacy `getTasksDueOn()` exact-equality DAO method exists but is not called by the repository.
- `updateTask()` normalises `dueDate` back to midnight on edit — this is a known inconsistency (FAIL-004) with no functional impact since range queries cover both midnight and end-of-day values on the same calendar day.
- Display formatting converts to device locale timezone.
- `DateUtils.kt` is the single source for date math.

### Task Lifecycle

```
TODO → In Progress → Completed → (tap again) → TODO
```

- Recurring tasks: lazy-reset on app open when `lastResetDate != today`.
- Completion writes a `TaskCompletionLog` entry.
- **Schedule Day Selector** (US4): `TaskEntity.scheduleMask` is a 7-bit bitmask (bit 0 = Monday, bit 6 = Sunday) matching `DayMask.kt`. Presets: ALL_DAYS=127, WEEKDAYS=31, WEEKENDS=96. `null` scheduleMask means "every day" (resolves to 127). Valid range: null or 1–127; values outside this range clamp to null.

### Testing

- **Unit**: JUnit4 + kotlinx-coroutines-test.
- **Instrumented**: Compose UI Test + Room in-memory DB.
- All tests tagged with their feature spec ID (e.g., `@Tag("FR-001")`).

## Database Schema (current version)

| Table | Purpose |
|-------|---------|
| `tasks` | All task entities |
| `task_completion_log` | Per-day completion records (streak & history) |

> Update this section when a migration is added.

## Hard Boundaries

1. **No new Gradle dependencies** without explicit approval in `decisions.md`.
2. **No multi-module refactor** — stays single-module until user requests otherwise.
3. **No LiveData** — coroutines only.
4. **API 16+ compat** — no API-gated calls without `@RequiresApi` + fallback.
