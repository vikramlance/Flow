package com.flow.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.flow.data.local.AchievementType
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import com.flow.ui.theme.TaskInProgress
import com.flow.ui.theme.TaskOverdue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonGreen)
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    PeriodSelectorRow(
                        selected       = uiState.selectedPeriod,
                        availableYears = uiState.availableYears,
                        onSelect       = viewModel::onPeriodSelected
                    )
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("Total",   uiState.totalCompleted.toString(), NeonGreen,      Modifier.weight(1f))
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
                        ContributionHeatmap(uiState.heatMapData)
                    }
                }
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
                }
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
                }
                if (uiState.achievements.isNotEmpty()) {
                    item { AchievementsSection(uiState.achievements) }
                }
                item {
                    ForestSection(forestData = uiState.forestData, forestTreeCount = uiState.forestTreeCount)
                }
            }
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

@Composable
fun AchievementsSection(achievements: List<com.flow.data.local.AchievementEntity>) {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    Text("Achievements ", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        achievements.forEach { ach ->
            Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(achievementEmoji(ach.type), style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(achievementName(ach.type), style = MaterialTheme.typography.titleSmall, color = NeonGreen, fontWeight = FontWeight.Bold)
                        Text("Earned ${sdf.format(Date(ach.earnedAt))}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        ach.periodLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                    }
                }
            }
        }
    }
}

private fun achievementEmoji(type: AchievementType): String = when (type) {
    AchievementType.STREAK_10     -> ""
    AchievementType.STREAK_30     -> ""
    AchievementType.STREAK_100    -> ""
    AchievementType.ON_TIME_10    -> ""
    AchievementType.EARLY_FINISH  -> ""
    AchievementType.YEAR_FINISHER -> ""
}

private fun achievementName(type: AchievementType): String = when (type) {
    AchievementType.STREAK_10     -> "Budding Habit (10 days)"
    AchievementType.STREAK_30     -> "Growing Strong (30 days)"
    AchievementType.STREAK_100    -> "Iron Will (100 days)"
    AchievementType.ON_TIME_10    -> "Punctual (10 on-time)"
    AchievementType.EARLY_FINISH  -> "Early Bird"
    AchievementType.YEAR_FINISHER -> "Year Finisher"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestSection(forestData: Map<Long, List<String>>, forestTreeCount: Int) {
    var selectedDayTitles by remember { mutableStateOf<List<String>?>(null) }
    var selectedDayLabel  by remember { mutableStateOf("") }
    Text("Your Forest ", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(4.dp))
    Text("Your forest: $forestTreeCount trees", style = MaterialTheme.typography.bodyMedium, color = NeonGreen, fontWeight = FontWeight.Bold)
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
                                Box(modifier = Modifier.size(cellSize).background(forestCellColor(count), RoundedCornerShape(2.dp)).clickable { onDayClick(dayKey, titles) })
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
fun ContributionHeatmap(heatMapData: Map<Long, Int>) {
    val currentDay = Calendar.getInstance().apply {
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }
    val weeksToShow = 52
    val startDay = (currentDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(weeksToShow * 7 - 1)) }
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
