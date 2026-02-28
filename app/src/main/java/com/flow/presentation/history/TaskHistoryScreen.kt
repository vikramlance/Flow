package com.flow.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import com.flow.presentation.analytics.ContributionHeatmap
import com.flow.presentation.home.HomeViewModel
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    taskId: Long,
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val task = uiState.homeTasks.find { it.task.id == taskId }?.task
    val history by viewModel.getTaskHistory(taskId).collectAsState(initial = emptyList())
    val streak by viewModel.getRawTaskStreak(taskId).collectAsState(initial = 0)

    // T031/US11: Compute Jan 1 of current year → end of today for the streak heatmap
    val nowMs           = System.currentTimeMillis()
    val jan1OfYearMs    = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val endOfTodayMs    = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    // Convert TaskCompletionLog to Map<date, count> for the Heatmap component
    val heatMapData: Map<Long, Int> = history
        .filter { it.isCompleted }
        .associate { it.date to 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: "Task History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Streak Header
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌱", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "$streak Days",
                            style = MaterialTheme.typography.displayLarge,
                            color = NeonGreen
                        )
                        Text("Current Streak", color = Color.Gray)
                    }
                }
            }

            Text("Contribution History (1 Year)", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // T031/US11: date-range-aware heatmap (Jan 1 → today), T032/US11: darker streak green
                    ContributionHeatmap(
                        heatMapData = heatMapData,
                        startMs     = jan1OfYearMs,
                        endMs       = endOfTodayMs
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (task?.isRecurring == false) {
                Text(
                    "Note: Streak tracking is most effective for recurring tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
