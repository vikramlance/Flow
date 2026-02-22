# Research: Progress Gamification & Analytics Enhancement

**Feature**: `002-progress-gamification`  
**Created**: 2026-02-22  
**Status**: Complete — all NEEDS CLARIFICATION resolved  

---

## R-001: Recurrence Schedule Storage

**Question**: What is the best way to store a recurring task schedule (DAILY or specific days-of-week) in SQLite/Room?

**Decision**: Store as a nullable `Int?` bitmask column named `scheduleMask` on the `tasks` table.

- Bit 0 = Monday, bit 1 = Tuesday, … bit 6 = Sunday (matches `java.time.DayOfWeek.value - 1`).
- `null` = DAILY (whole-week schedule); backward-compatible with all existing recurring tasks.
- Value `127` (all bits set) = every day of the week — functionally equivalent to DAILY.

**Rationale**:
- SQLite bitwise AND (`&`) allows efficient in-SQL filtering: `scheduleMask IS NULL OR (scheduleMask & :todayBit) != 0` — no full-table scan, no TypeConverter, no KSP complications.
- Zero TypeConverter means the field requires no extra Room annotation; it's a plain `Int?` property on `TaskEntity`.
- `null` maps cleanly to the "DAILY" concept with a single null-check, preserving backward compatibility for all pre-feature recurring tasks.
- Migration is a single `ALTER TABLE tasks ADD COLUMN scheduleMask INTEGER DEFAULT NULL`.

**Alternatives considered**:
- *CSV String* (`"MONDAY,WEDNESDAY,FRIDAY"`): readable but requires fragile `LIKE '%WEDNESDAY%'` SQL filtering; cannot use an index.
- *TypeConverter-backed `Set<DayOfWeek>`*: best Kotlin type safety, but loses SQL-level filtering capability — the full table must be loaded before filtering in Kotlin. Chosen against.

**Helper object** (domain layer, no Android deps):
```kotlin
object DayMask {
    const val MON = 1 shl 0   // 1
    const val TUE = 1 shl 1   // 2
    const val WED = 1 shl 2   // 4
    const val THU = 1 shl 3   // 8
    const val FRI = 1 shl 4   // 16
    const val SAT = 1 shl 5   // 32
    const val SUN = 1 shl 6   // 64
    const val DAILY = null     // semantic alias

    fun fromDayOfWeek(d: DayOfWeek) = 1 shl (d.value - 1)
    fun isScheduled(mask: Int?, d: DayOfWeek) =
        mask == null || (mask and fromDayOfWeek(d)) != 0
}
```

---

## R-002: Schedule-Aware Streak Algorithm

**Question**: How should `currentStreak` and `longestStreak` be computed when a recurring task only runs on certain weekdays?

**Decision**: Use a pure-Kotlin `StreakCalculator` object that walks calendar days backwards (current streak) and forwards (longest streak), skipping non-scheduled days transparently. Only scheduled days that are NOT in the completion set cause a streak break.

**Algorithm summary**:
- *currentStreak*: Walk backwards from yesterday (or today if today is scheduled but not yet complete — "grace period"). For each day: if it is a scheduled day AND not in `completedDates` → break. If it is not a scheduled day → skip silently. If it is scheduled AND completed → increment.
- *longestStreak*: Walk forward from the earliest completion date through today; apply the same scheduled/skipped logic, tracking the running max.

**Key behaviours**:
- A gap day that is NOT on the schedule (e.g., Sunday on a Mon–Fri schedule) never breaks the streak.
- `schedule = null` → treated as DAILY (every day is scheduled).
- No Android runtime dependencies — uses `java.time.LocalDate` only; fully unit-testable with `ZoneId` overrides for determinism.

**Rationale**: The spec (FR-010, Q3 answer) requires streak counting based on scheduled days. A simple completion-log set lookup per day is O(n) in the number of calendar days traversed, which is negligible for practical streak lengths. The pure-Kotlin approach enables thorough JVM unit testing with no device.

---

## R-003: Analytics Period Selector UI

**Question**: What Compose component best represents the four-option period selector (Current Year / Last 12 Months / Specific Year / Lifetime) on the Analytics screen?

**Decision**: A horizontally scrollable `Row` of `FilterChip` components — three fixed chips plus one "Specific Year" chip that opens a compact year-picker `AlertDialog`.

**Rationale**:
- `SingleChoiceSegmentedButtonRow` was evaluated but ruled out: four labels including "Last 12 Months" and a dynamic year value (e.g., "2024") do not fit evenly in the width of a phone screen even at `labelSmall` (11 sp). SegmentedButton also requires uniform button widths, which prevents the year chip label from updating in place.
- `ExposedDropdownMenuBox` is a viable fallback but requires two taps minimum and obscures the heatmap with an overlay; it also touches `@ExperimentalMaterial3Api` in some BOM builds.
- `FilterChip` in a horizontal scroll row: all stable M3 1.3.0 APIs, each chip carries its own tinted/outlined state, the year chip's label self-updates to the chosen year, and the row scrolls gracefully at any screen width without clipping.

**AnalyticsPeriod sealed class** (presentation layer, not persisted):
```kotlin
sealed class AnalyticsPeriod {
    data object CurrentYear   : AnalyticsPeriod()
    data object Last12Months  : AnalyticsPeriod()  // rolling 365 days
    data class  SpecificYear(val year: Int) : AnalyticsPeriod()
    data object Lifetime      : AnalyticsPeriod()
}
```

**Year picker**: A compact `AlertDialog` with a `LazyColumn` of selectable year rows spanning from the year of the earliest TaskCompletionLog entry to the current year. No `DatePickerDialog` needed — avoids Material3 experimental APIs.

**Forest / Analytics state sharing**: `AnalyticsViewModel` holds a single `selectedPeriod: AnalyticsPeriod` in its `AnalyticsUiState`; both the heatmap and the Forest section observe the same state object — no duplication.

---

## R-004: Forest Heatmap Rendering

**Question**: How should the Forest heatmap be rendered in Compose with tree-density cell shading and tap-to-summary?

**Decision**: Extend the existing `ContributionHeatmap` pattern (horizontal scroll `Row` of week `Column`s) with a `ForestCell` composable that renders a `Box` with background colour tier plus emoji icon overlay. No Canvas needed.

**Tree density tiers**:
| Count | Background | Icon overlay |
|-------|-----------|-------------|
| 0     | `#2A2A2A` (empty) | none |
| 1–2   | `#4CAF50` @ 40% | none |
| 3–5   | `#388E3C` @ 75% | 🌿 |
| 6+    | `#1B5E20` (full) | 🌳 |

**Data source**: New `getForestData(startMs, endMs): Flow<Map<Long, List<String>>>` repository method — midnight-epoch → list of distinct recurring task titles completed that day. Count is `value.size`; title list is used for the tap summary sheet.

**Tap interaction**: A `ModalBottomSheet` (M3 stable) opened when `selectedForestDay` state is non-null; lists recurring task titles for that day within a `LazyColumn`.

**Rationale**: Reusing the existing horizontal-scroll week-grid pattern (already tested) avoids re-implementing calendar date arithmetic. The `Box`+emoji approach is render-thread-friendly and keeps the composable leaf simple. Canvas would only be needed for curved/custom tree SVGs — out of scope for this feature.

---

## R-005: Schema Migration Strategy (v5 → v6)

**Decision**: Single Room DB version bump 5 → 6 covering three schema changes in one migration object:
1. `ALTER TABLE tasks ADD COLUMN scheduleMask INTEGER DEFAULT NULL` — nullable, existing rows default to null (= DAILY).
2. `CREATE TABLE task_streaks (...)` — new entity.
3. `CREATE TABLE achievements (...)` — new entity.

No existing row data is modified. The migration is safe to run multiple times (all three DDL statements use `IF NOT EXISTS`/`DEFAULT NULL`).

**Rationale**: A single migration block is simpler to test and avoids intermediate versions that would need separate migration paths. All three changes are additive (no column removal, no type changes) and carry safe defaults.
