package com.flow.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.util.Calendar
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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else {
                // Stats summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("Total", uiState.totalCompleted.toString(), NeonGreen, Modifier.weight(1f))
                    StatChip("On Time", uiState.completedOnTime.toString(), TaskInProgress, Modifier.weight(1f))
                    StatChip("Missed", uiState.missedDeadlines.toString(), TaskOverdue, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("Streak", "${uiState.currentStreak}d", NeonGreen, Modifier.weight(1f))
                    StatChip("Best", "${uiState.bestStreak}d", NeonGreen.copy(alpha = 0.7f), Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Contribution Graph", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ContributionHeatmap(uiState.heatMapData)
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

/**
 * GitHub-style contribution heatmap.
 * T032/T033/T034: Weekday labels are FIXED (outside horizontalScroll); month labels at top;
 * cells coloured by completion ratio from heatMapData: Map<timestamp -> count>.
 */
@Composable
fun ContributionHeatmap(heatMapData: Map<Long, Int>) {
    val currentDay = Calendar.getInstance().apply {
        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }
    val weeksToShow = 52
    val startDay = (currentDay.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -(weeksToShow * 7 - 1))
    }
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val cellSize = 12.dp
    val cellGap = 4.dp
    val rowHeight = cellSize + cellGap

    Row(modifier = Modifier.padding(8.dp)) {
        // Fixed weekday labels (outside scroll)
        Column(modifier = Modifier.width(32.dp)) {
            Spacer(modifier = Modifier.height(20.dp)) // match month-label row height
            listOf("", "Mon", "", "Wed", "", "Fri", "").forEach { label ->
                Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        // Scrollable heat map (month labels + grid)
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Month labels row
                Row {
                    val monthCal = startDay.clone() as Calendar
                    var lastMonth = -1
                    repeat(weeksToShow) {
                        val month = monthCal.get(Calendar.MONTH)
                        Box(modifier = Modifier.width(cellSize + cellGap)) {
                            if (month != lastMonth) {
                                Text(
                                    text = monthCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "",
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                                    softWrap = false
                                )
                                lastMonth = month
                            }
                        }
                        monthCal.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                // Grid
                Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                    val gridCal = startDay.clone() as Calendar
                    repeat(weeksToShow) {
                        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                            repeat(7) {
                                val dayKey = normaliseToMidnight(gridCal.timeInMillis)
                                val count = heatMapData[dayKey] ?: 0
                                val color = when {
                                    count == 0 -> Color.DarkGray
                                    count == 1 -> NeonGreen.copy(alpha = 0.35f)
                                    count <= 3 -> NeonGreen.copy(alpha = 0.65f)
                                    else       -> NeonGreen
                                }
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
