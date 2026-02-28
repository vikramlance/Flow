package com.flow.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import com.flow.ui.theme.TaskInProgress
import com.flow.ui.theme.TaskOverdue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", color = NeonGreen) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        val pagerState    = rememberPagerState(pageCount = { 4 })
        val coroutineScope = rememberCoroutineScope()

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // T027/FR-007: 4-tab pill row synced to pager
            PillTabRow(
                selectedPage = pagerState.currentPage,
                onSelect     = { idx -> coroutineScope.launch { pagerState.animateScrollToPage(idx) } }
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else {
                // T028/FR-007: HorizontalPager with 4 content pages
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        // Page 0: Graph / Contribution Heatmap
                        0 -> LazyColumn(
                            modifier         = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding   = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            item {
                                PeriodSelectorRow(
                                    selected       = uiState.selectedPeriod,
                                    availableYears = uiState.availableYears,
                                    onSelect       = viewModel::onPeriodSelected
                                )
                            }
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // T026/US5: Total chip draws from heatMapData so it can never diverge from the graph
                                    StatChip("Total",   uiState.heatMapData.values.sum().toString(), NeonGreen,      Modifier.weight(1f))
                                    StatChip("On Time", uiState.completedOnTime.toString(), TaskInProgress, Modifier.weight(1f))
                                    StatChip("Missed",  uiState.missedDeadlines.toString(), TaskOverdue,   Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatChip("Streak", "${uiState.currentStreak}d", NeonGreen,                   Modifier.weight(1f))
                                    StatChip("Best",   "${uiState.bestStreak}d",   NeonGreen.copy(alpha = 0.7f), Modifier.weight(1f))
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            item {
                                Text("Contribution Graph", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), modifier = Modifier.fillMaxWidth()) {
                                    // T029/US6: pass period-aware date range so heatmap shows only current period
                                    ContributionHeatmap(
                                        heatMapData = uiState.heatMapData,
                                        startMs     = uiState.heatMapStartMs,
                                        endMs       = uiState.heatMapEndMs
                                    )
                                }
                            }
                        }

                        // Page 1: Lifetime Stats
                        1 -> LazyColumn(
                            modifier         = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding   = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            uiState.lifetimeStats?.let { ls ->
                                item {
                                    Text("Lifetime Stats", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatChip("Done",    ls.totalCompleted.toString(),       NeonGreen,      Modifier.weight(1f))
                                        StatChip("On Time", "${(ls.onTimePct * 100).toInt()}%", TaskInProgress, Modifier.weight(1f))
                                        StatChip("Best", "${ls.longestStreak}d",                NeonGreen.copy(alpha = 0.7f), Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatChip("Unique Habits", ls.uniqueHabitsCount.toString(), NeonGreen.copy(alpha = 0.5f))
                                }
                            } ?: item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No lifetime data yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        // Page 2: This Year
                        2 -> LazyColumn(
                            modifier         = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding   = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            uiState.currentYearStats?.let { cy ->
                                item {
                                    Text("This Year (${Calendar.getInstance().get(Calendar.YEAR)})",
                                        style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatChip("Done",    cy.completedThisYear.toString(),             NeonGreen,      Modifier.weight(1f))
                                        StatChip("On Time", "${(cy.onTimeRateThisYear * 100).toInt()}%", TaskInProgress, Modifier.weight(1f))
                                        StatChip("Best",    "${cy.bestStreakThisYear}d",                  NeonGreen.copy(alpha = 0.7f), Modifier.weight(1f))
                                    }
                                }
                            } ?: item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No data for this year yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        // Page 3: Forest
                        3 -> LazyColumn(
                            modifier         = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding   = PaddingValues(bottom = 24.dp, top = 8.dp)
                        ) {
                            item {
                                ForestSection(
                                    forestData     = uiState.forestData,
                                    forestTreeCount = uiState.forestTreeCount,
                                    bestStreak     = uiState.bestStreak
                                )
                            }
                        }

                        else -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ── T027/FR-007: Pill tab row synced to HorizontalPager ──────────────────────

@Composable
fun PillTabRow(
    selectedPage: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf("Graph", "Lifetime", "This Year", "Forest")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedPage == index,
                onClick  = { onSelect(index) },
                label    = { Text(label) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonGreen,
                    selectedLabelColor     = Color.Black,
                    containerColor         = Color.Transparent,
                    labelColor             = NeonGreen
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled  = true,
                    selected = selectedPage == index,
                    borderColor         = NeonGreen.copy(alpha = 0.5f),
                    selectedBorderColor = NeonGreen
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectorRow(
    selected: AnalyticsPeriod,
    availableYears: List<Int>,
    onSelect: (AnalyticsPeriod) -> Unit
) {
    var showYearPicker by remember { mutableStateOf(false) }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == AnalyticsPeriod.CurrentYear, { onSelect(AnalyticsPeriod.CurrentYear) },
            label = { Text("This Year") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black))
        FilterChip(selected == AnalyticsPeriod.Last12Months, { onSelect(AnalyticsPeriod.Last12Months) },
            label = { Text("Last 12M") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black))
        FilterChip(selected == AnalyticsPeriod.Lifetime, { onSelect(AnalyticsPeriod.Lifetime) },
            label = { Text("All Time") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black))
        val yearLabel = if (selected is AnalyticsPeriod.SpecificYear) "${selected.year}" else "Year"
        FilterChip(selected is AnalyticsPeriod.SpecificYear, { showYearPicker = true },
            label = { Text(yearLabel) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGreen, selectedLabelColor = Color.Black))
    }
    if (showYearPicker && availableYears.isNotEmpty()) {
        YearPickerDialog(years = availableYears, onSelect = { showYearPicker = false; onSelect(AnalyticsPeriod.SpecificYear(it)) }, onDismiss = { showYearPicker = false })
    }
}

@Composable
fun YearPickerDialog(years: List<Int>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Select Year", color = Color.White) },
        text = {
            Column {
                years.sortedDescending().forEach { year ->
                    Text(year.toString(), modifier = Modifier.fillMaxWidth().clickable { onSelect(year) }.padding(vertical = 8.dp), color = NeonGreen, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } },
        containerColor = SurfaceDark
    )
}

@Composable
fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestSection(forestData: Map<Long, List<String>>, forestTreeCount: Int, bestStreak: Int = 0) {
    var selectedDayTitles by remember { mutableStateOf<List<String>?>(null) }
    var selectedDayLabel  by remember { mutableStateOf("") }
    Text("Your Forest ", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(4.dp))
    Text("Your forest: $forestTreeCount trees", style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
    Text("Best streak: $bestStreak days", style = MaterialTheme.typography.bodySmall, color = NeonGreen.copy(alpha = 0.75f))
    Spacer(modifier = Modifier.height(8.dp))
    ForestHeatmap(forestData = forestData, onDayClick = { dayMs, titles ->
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        selectedDayLabel = sdf.format(Date(dayMs)); selectedDayTitles = titles
    })
    selectedDayTitles?.let { titles ->
        ModalBottomSheet(onDismissRequest = { selectedDayTitles = null }, containerColor = SurfaceDark) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Completed on $selectedDayLabel", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (titles.isEmpty()) Text("No recurring tasks completed", color = Color.Gray)
                else titles.forEach { title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83C\uDF31", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ForestHeatmap(forestData: Map<Long, List<String>>, onDayClick: (Long, List<String>) -> Unit) {
    val currentDay = Calendar.getInstance().apply {
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }
    val weeksToShow = 52
    val startDay = (currentDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(weeksToShow * 7 - 1)) }
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val cellSize = 12.dp; val cellGap = 4.dp; val rowHeight = cellSize + cellGap
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.width(32.dp)) {
            Spacer(modifier = Modifier.height(20.dp))
            listOf("", "Mon", "", "Wed", "", "Fri", "").forEach { label ->
                Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                Row {
                    val monthCal = startDay.clone() as Calendar; var lastMonth = -1
                    repeat(weeksToShow) {
                        val month = monthCal.get(Calendar.MONTH)
                        Box(modifier = Modifier.widthIn(min = cellSize + cellGap)) {
                            if (month != lastMonth) { Text(monthCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray, softWrap = false); lastMonth = month }
                        }
                        monthCal.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    val gridCal = startDay.clone() as Calendar
                    repeat(weeksToShow) {
                        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                            repeat(7) {
                                val dayKey = normaliseToMidnight(gridCal.timeInMillis)
                                val titles = forestData[dayKey] ?: emptyList()
                                val count  = titles.size
                                // T029/FR-008: emoji tree cells replace colored squares
                                Text(
                                    text   = if (count == 0) "·" else "\uD83C\uDF32".repeat(count.coerceAtMost(4)),
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = if (count == 0) Color.Gray else Color.Unspecified,
                                    modifier = Modifier.size(cellSize).clickable { onDayClick(dayKey, titles) }
                                )
                                gridCal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun forestCellColor(count: Int): Color = when {
    count == 0 -> Color(0xFF2A2A2A); count <= 2 -> Color(0xFF00E676).copy(alpha = 0.40f)
    count <= 5 -> Color(0xFF00E676).copy(alpha = 0.75f); else -> Color(0xFF1B5E20)
}

@Composable
fun ContributionHeatmap(
    heatMapData: Map<Long, Int>,
    // T028/US6: explicit date range replaces hardcoded 52-week rolling window
    startMs: Long = System.currentTimeMillis().let { now ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }; cal.timeInMillis
    },
    endMs: Long = System.currentTimeMillis()
) {
    val weeksToShow = (((endMs - startMs) / (7L * 24 * 60 * 60 * 1000)).toInt() + 1).coerceAtLeast(1)
    val startDay = Calendar.getInstance().apply {
        timeInMillis = startMs
        // Anchor to the Saturday at or before startMs so weeks always start on Sunday
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val cellSize = 12.dp; val cellGap = 4.dp; val rowHeight = cellSize + cellGap
    Row(modifier = Modifier.padding(8.dp)) {
        Column(modifier = Modifier.width(32.dp)) {
            Spacer(modifier = Modifier.height(20.dp))
            listOf("", "Mon", "", "Wed", "", "Fri", "").forEach { label ->
                Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                Row {
                    val monthCal = startDay.clone() as Calendar; var lastMonth = -1
                    repeat(weeksToShow) {
                        val month = monthCal.get(Calendar.MONTH)
                        Box(modifier = Modifier.widthIn(min = cellSize + cellGap)) {
                            if (month != lastMonth) { Text(monthCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray, softWrap = false); lastMonth = month }
                        }
                        monthCal.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    val gridCal = startDay.clone() as Calendar
                    repeat(weeksToShow) {
                        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                            repeat(7) {
                                val dayKey = normaliseToMidnight(gridCal.timeInMillis)
                                val count  = heatMapData[dayKey] ?: 0
                                val color  = when { count == 0 -> Color.DarkGray; count == 1 -> NeonGreen.copy(alpha = 0.35f); count <= 3 -> NeonGreen.copy(alpha = 0.65f); else -> NeonGreen }
                                Box(modifier = Modifier.size(cellSize).background(color, RoundedCornerShape(2.dp)))
                                gridCal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normaliseToMidnight(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
