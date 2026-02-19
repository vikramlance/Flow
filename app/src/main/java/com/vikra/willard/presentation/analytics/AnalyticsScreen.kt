package com.vikra.willard.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vikra.willard.ui.theme.CompletedGreen
import com.vikra.willard.ui.theme.NeonGreen
import com.vikra.willard.ui.theme.SurfaceDark
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productivity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Contribution Graph", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Heatmap Container
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().height(200.dp) // Fixed height for scrolling if needed
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    ContributionHeatmap(history)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Streak Stats (Placeholder)
            Text("Current Streak: 0 Days", style = MaterialTheme.typography.headlineMedium, color = NeonGreen)
        }
    }
}

@Composable
fun ContributionHeatmap(history: List<com.vikra.willard.data.local.DailyProgressEntity>) {
    val calendar = Calendar.getInstance()
    // Move to end of current week (Saturday)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    
    // Find next Saturday to align the grid end
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    
    val weeksToShow = 52
    val totalDays = weeksToShow * 7
    calendar.add(Calendar.DAY_OF_YEAR, -totalDays + 1)
    
    val historyMap = history.associate { it.date to it }
    val scrollState = androidx.compose.foundation.rememberScrollState(initial = Int.MAX_VALUE) // Scroll to end (latest)

    Column(modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Month Labels
                Row {
                    Spacer(modifier = Modifier.width(32.dp)) // Offset for Day Labels
                    val monthCalendar = calendar.clone() as Calendar
                    var lastMonth = -1
                    repeat(weeksToShow) { week ->
                        val currentMonth = monthCalendar.get(Calendar.MONTH)
                        Box(modifier = Modifier.width(16.dp)) { // Match grid week width
                            if (currentMonth != lastMonth) {
                                Text(
                                    text = monthCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    softWrap = false,
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                                lastMonth = currentMonth
                            }
                        }
                        monthCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row {
                    // Day Labels (Stick to left or just part of scroll for now)
                    Column(
                        modifier = Modifier.width(32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(7) { day ->
                            if (day % 2 == 1) { // Mon, Wed, Fri
                                val dayName = when(day) {
                                    1 -> "Mon"
                                    3 -> "Wed"
                                    5 -> "Fri"
                                    else -> ""
                                }
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    modifier = Modifier.height(12.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    
                    // Grid
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val gridCalendar = calendar.clone() as Calendar
                        repeat(weeksToShow) { 
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(7) {
                                    val time = getStartOfDay(gridCalendar.timeInMillis)
                                    val progress = historyMap[time]
                                    
                                    val color = when {
                                        progress == null || progress.tasksCompletedCount == 0 -> Color.DarkGray
                                        progress.tasksCompletedCount >= progress.tasksTotalCount && progress.tasksTotalCount > 0 -> NeonGreen
                                        else -> NeonGreen.copy(alpha = 0.5f)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(color, RoundedCornerShape(2.dp))
                                    )
                                    gridCalendar.add(Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getStartOfDay(timeMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun ConvertDataToColor(history: List<com.vikra.willard.data.local.DailyProgressEntity>, week: Int, day: Int): Boolean {
    // Placeholder logic
    return false
}
