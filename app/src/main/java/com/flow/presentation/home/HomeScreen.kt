package com.flow.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import com.flow.data.local.TaskStatus
import com.flow.util.defaultEndTime
import com.flow.util.endTimeForDate
import com.flow.util.mergeDateTime
import com.flow.util.utcDateToLocalMidnight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import android.media.AudioManager
import android.media.ToneGenerator
import com.flow.data.local.TaskEntity
import com.flow.presentation.timer.TimerPanel
import com.flow.presentation.onboarding.OnboardingFlow
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import com.flow.ui.theme.TaskInProgress
import com.flow.ui.theme.TaskOverdue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTaskHistory: (Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var streakTask by remember { mutableStateOf<TaskEntity?>(null) }

    val todayProgressState = uiState.todayProgressState
    val percentage = todayProgressState.ratio

    // T021: yellow until 100 %, green when all done
    val streakColor = if (percentage >= 1f) NeonGreen else TaskInProgress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow", style = MaterialTheme.typography.displaySmall, color = NeonGreen) },
                actions = {
                   IconButton(onClick = { showTimerDialog = true }) {
                       Icon(Icons.Default.PlayArrow, contentDescription = "Timer", tint = NeonGreen)
                   }
                   IconButton(onClick = onNavigateToAnalytics) {
                       Icon(Icons.Default.DateRange, contentDescription = "Stats", tint = NeonGreen)
                   }
                   IconButton(onClick = onNavigateToHistory) {
                       Icon(Icons.Default.History, contentDescription = "History", tint = NeonGreen, modifier = Modifier.size(28.dp))
                   }
                   IconButton(onClick = onNavigateToAchievements) {
                       Icon(Icons.Default.EmojiEvents, contentDescription = "Achievements", tint = NeonGreen, modifier = Modifier.size(28.dp))
                   }
                   IconButton(onClick = { viewModel.showHelp() }) {
                       Icon(Icons.Default.Info, contentDescription = "Help", tint = NeonGreen, modifier = Modifier.size(28.dp))
                   }
                   IconButton(onClick = onNavigateToSettings) {
                       Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonGreen)
                   }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = NeonGreen
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        },
        containerColor = SurfaceDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Dashboard Header â€” T021: today-focused progress
            if (!todayProgressState.hasAnyTodayTasks) {
                // Empty state: no tasks due today
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks for today",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = streakColor.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, streakColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Today's progress", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            Text(
                                text = "${(percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.displayMedium,
                                color = streakColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(streakColor, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            // Grid Tasks
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.homeTasks, key = { it.task.id }) { item ->
                    val streakCount by viewModel.getRawTaskStreak(item.task.id).collectAsState(initial = 0)
                    TaskItem(
                        task = item.task,
                        urgency = item.urgency,
                        streakCount = streakCount,
                        onStatusChange = { newStatus ->
                            viewModel.updateTaskStatus(item.task, newStatus)
                        },
                        onEdit = { editingTask = item.task },
                        onShowStreak = { onNavigateToTaskHistory(item.task.id) }
                    )
                }
                // ── T017: Upcoming section (FR-003) ─────────────────────────
                if (uiState.upcomingTasks.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(uiState.upcomingTasks, key = { "upcoming_${it.task.id}" }) { item ->
                        val streakCount by viewModel.getRawTaskStreak(item.task.id).collectAsState(initial = 0)
                        TaskItem(
                            task = item.task,
                            urgency = item.urgency,
                            streakCount = streakCount,
                            onStatusChange = { newStatus ->
                                viewModel.updateTaskStatus(item.task, newStatus)
                            },
                            onEdit = { editingTask = item.task },
                            onShowStreak = { onNavigateToTaskHistory(item.task.id) }
                        )
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddTaskDialog(
                onDismiss          = { showAddDialog = false },
                scheduleMaskError  = uiState.scheduleMaskError,
                onAdd = { title, startDate, dueDate, isRecurring, scheduleMask ->
                    viewModel.addTask(title, startDate, dueDate, isRecurring, scheduleMask)
                    showAddDialog = false
                }
            )
        }
        
        editingTask?.let { task ->
            EditTaskDialog(
                task = task,
                onDismiss = { editingTask = null },
                onSave = { updatedTask ->
                    viewModel.updateTask(updatedTask)
                    editingTask = null
                },
                onDelete = {
                    viewModel.deleteTask(task)
                    editingTask = null
                }
            )
        }
        
        streakTask?.let { task ->
            TaskStreakDialog(
                task = task,
                viewModel = viewModel,
                onDismiss = { streakTask = null }
            )
        }
        
        if (showTimerDialog) {
            TimerPanel(onDismiss = { showTimerDialog = false })
        }

        if (uiState.isFirstLaunch) {
            OnboardingFlow(
                onComplete = { viewModel.completeOnboarding() }
            )
        }

        if (uiState.showHelp) {
            HelpOverlay(onDismiss = { viewModel.hideHelp() })
        }
    }
}

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    
    Dialog(onDismissRequest = {}) { // Force completion
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when(step) {
                        1 -> "Welcome to Flow! \uD83C\uDF1F"
                        2 -> "Track Your Progress \uD83D\uDCC8"
                        3 -> "Focus & Streaks \uD83D\uDD25"
                        else -> "Ready to Start?"
                    }, 
                    style = MaterialTheme.typography.headlineSmall, 
                    color = NeonGreen
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when(step) {
                        1 -> "Flow is designed to help you build habits through gamified task management. Tap a task to cycle through: TODO \u2192 In Progress \u23F3 \u2192 Completed \u2705"
                        2 -> "Your main dashboard changes color based on daily completion: Green (\u2265100%), Yellow (\u226550%), or Orange (\u003C50%)."
                        3 -> "Use the Focus Timer \u23F1 for deep work sessions. Recurring tasks track 'Contribution Streaks' \uD83C\uDF31 over time."
                        else -> "Long press any task to edit or delete it. Check your productivity heatmap in the 'Stats' tab!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (step < 4) step++ else onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (step < 4) "Next" else "Let's Go!")
                }
                
                if (step > 1) {
                    TextButton(onClick = { step-- }) {
                        Text("Back", color = Color.Gray)
                    }
                }
            }
        }
    }
}


/**
 * T021 â€” Help overlay shown when the user taps the â„¹ Info icon in the TopAppBar.
 * Displays a concise reminder of key app interactions. Dismissible by tapping outside or the button.
 */
@Composable
fun HelpOverlay(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\u2139\uFE0F How to use Flow",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                val tips = listOf(
                    "\uD83D\uDC46 Tap a task card to advance its status: TODO \u2192 In Progress \u23F3 \u2192 Done \u2705",
                    "\u270F\uFE0F Long-press any card to edit or delete it.",
                    "\uD83C\uDF31 Recurring tasks track your daily completion streak.",
                    "\u23F1\uFE0F Use the Focus Timer to stay in the zone.",
                    "\uD83D\uDCCA Tap Analytics to view your heatmap and stats.",
                    "\uD83D\uDCCB Tap History to see all completed tasks.",
                    "\uD83C\uDFAF The dashboard colour: Green \u2265100% \u00B7 Yellow \u226550% \u00B7 Orange <50%."
                )

                tips.forEach { tip ->
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it!")
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: TaskEntity,
    urgency: UrgencyLevel = UrgencyLevel.NONE,
    streakCount: Int,
    onStatusChange: (com.flow.data.local.TaskStatus) -> Unit,
    onEdit: () -> Unit,
    onShowStreak: () -> Unit
) {
    val status = task.status
    val isCompleted = status == com.flow.data.local.TaskStatus.COMPLETED
    val isInProgress = status == com.flow.data.local.TaskStatus.IN_PROGRESS

    val cardColor = when (status) {
        com.flow.data.local.TaskStatus.COMPLETED   -> NeonGreen
        com.flow.data.local.TaskStatus.IN_PROGRESS -> TaskInProgress
        else                                        -> SurfaceDark
    }

    val animatedColor by animateColorAsState(
        targetValue = cardColor,
        label = "BoxColor"
    )

    // Status Logic
    val isOverdue = !isCompleted && task.dueDate != null && task.dueDate < System.currentTimeMillis()
    // T024: urgency border overrides default grey when not overdue / completed / in-progress
    val urgencyColor = when (urgency) {
        UrgencyLevel.GREEN  -> com.flow.ui.theme.NeonGreen.copy(alpha = 0.7f)
        UrgencyLevel.YELLOW -> TaskInProgress
        UrgencyLevel.ORANGE -> TaskOverdue
        UrgencyLevel.NONE   -> null
    }
    val borderColor = when {
        isOverdue                            -> TaskOverdue
        isInProgress                         -> TaskInProgress
        isCompleted                          -> NeonGreen
        urgencyColor != null                 -> urgencyColor
        else                                 -> Color.Gray.copy(alpha = 0.5f)
    }
    val contentColor = if (isCompleted) Color.Black else Color.White

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .combinedClickable(
                onClick = {
                    // Cycle: TODO \u2192 IN_PROGRESS \u2192 COMPLETED \u2192 TODO
                    val nextStatus = when (status) {
                        com.flow.data.local.TaskStatus.TODO -> com.flow.data.local.TaskStatus.IN_PROGRESS
                        com.flow.data.local.TaskStatus.IN_PROGRESS -> com.flow.data.local.TaskStatus.COMPLETED
                        com.flow.data.local.TaskStatus.COMPLETED -> com.flow.data.local.TaskStatus.TODO
                    }
                    onStatusChange(nextStatus)
                },
                onLongClick = { onEdit() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor
        ),
        border = if (isCompleted) null else BorderStroke(if (isOverdue) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
             Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                 // Status Icon
                 when (status) {
                     com.flow.data.local.TaskStatus.COMPLETED -> Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                     com.flow.data.local.TaskStatus.IN_PROGRESS -> Text("\u23F3", style = MaterialTheme.typography.bodyLarge)
                     else -> Box(modifier = Modifier.size(24.dp))
                 }
                 
                  if (task.isRecurring) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.clickable { onShowStreak() }
                      ) {
                          Text("\uD83C\uDF31", style = MaterialTheme.typography.bodyLarge)
                          if (streakCount > 0) {
                              Spacer(modifier = Modifier.width(4.dp))
                              Text(
                                  text = streakCount.toString(),
                                  style = MaterialTheme.typography.labelMedium,
                                  color = contentColor,
                                  fontWeight = FontWeight.Bold
                              )
                          }
                      }
                  }
              }
             
             Text(
                 text = task.title,
                 style = MaterialTheme.typography.titleMedium,
                 color = contentColor,
                 fontWeight = FontWeight.Bold,
                 maxLines = 3,
                 textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
             )
             
             // Explicit Target/Due Date
             if (task.dueDate != null) {
                 val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                 val dateStr = sdf.format(Date(task.dueDate))
                 Text(
                     text = "Target: $dateStr",
                     style = MaterialTheme.typography.labelSmall,
                     color = if (isCompleted) contentColor else Color.Gray,
                     fontWeight = FontWeight.Medium
                 )
             } else if (task.isRecurring) {
                 Text(
                     text = "Daily Target",
                     style = MaterialTheme.typography.labelSmall,
                     color = if (isCompleted) contentColor else Color.Gray
                 )
             }

             // T017/US3/FR-005: Start date and time always visible on task card
             Text(
                 text = "Start: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.startDate))}",
                 style = MaterialTheme.typography.labelSmall,
                 color = if (isCompleted) contentColor else Color.Gray
             )

             if (task.dueDate != null && !isCompleted) {
                 val diff = task.dueDate - System.currentTimeMillis()
                 val daysLeft = diff / (1000 * 3600 * 24)
                 val hoursLeft = (diff / (1000 * 3600)) % 24
                 
                 val label = when {
                     diff < 0 -> {
                         val daysOverdue = kotlin.math.abs(daysLeft)
                         if (daysOverdue > 0) "Overdue by ${daysOverdue}d" else "Overdue!"
                     }
                     daysLeft > 0 -> "${daysLeft}d ${hoursLeft}h left"
                     else -> "${hoursLeft}h left"
                 }
                 val labelColor = if (diff < 0) TaskOverdue else Color.Gray
                 
                 Text(
                     text = label,
                     style = MaterialTheme.typography.labelSmall,
                     color = if(isCompleted) contentColor else labelColor
                 )
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    scheduleMaskError: Boolean = false,
    onAdd: (String, Long, Long?, Boolean, Int?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    // T025/US4: default to 127 (all 7 days) so chips appear fully-selected when dialog opens
    var scheduleMask by remember { mutableStateOf<Int?>(127) }

    // T020/US3: capture dialog-open time once so date-picker changes don't re-sample it
    val dialogOpenTime = remember { System.currentTimeMillis() }
    var startDate by remember { mutableLongStateOf(dialogOpenTime) }
    var dueDate by remember { mutableStateOf<Long?>(defaultEndTime()) }
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showTargetTimePicker by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("New Task", style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen,
                        focusedBorderColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Start Date/Time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Start Date & Time", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(Date(startDate)), color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Date/Time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Target Date & Time (Optional)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTargetDatePicker = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(dueDate?.let { sdf.format(Date(it)) } ?: "Set Target Date", color = if (dueDate == null) Color.Gray else Color.White)
                        if (dueDate != null) {
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { dueDate = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = TaskOverdue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonGreen)
                    )
                    Text("Track Streak (Recurring)", color = Color.White)
                }

                // T025/US4: Show day-selector below the checkbox when isRecurring is checked
                if (isRecurring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ScheduleDaySelector(
                        mask        = scheduleMask ?: 127,
                        onMaskChange = { scheduleMask = it },
                        isError     = scheduleMaskError
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (text.isNotBlank()) {
                            onAdd(text, startDate, dueDate, isRecurring, if (isRecurring) scheduleMask else null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Task")
                }
            }
        }
    }

    // Picker Dialogs Logic
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        startDate = mergeDateTime(utcDateToLocalMidnight(it), dialogOpenTime)
                        showStartDatePicker = false
                        showStartTimePicker = true
                    }
                }) { Text("Next") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = startDate }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = startDate
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            startDate = cal.timeInMillis
                            showStartTimePicker = false
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate ?: dialogOpenTime)
        DatePickerDialog(
            onDismissRequest = { showTargetDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        dueDate = endTimeForDate(utcDateToLocalMidnight(it))
                        showTargetDatePicker = false
                        showTargetTimePicker = true
                    }
                }) { Text("Next") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTargetTimePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueDate ?: dialogOpenTime }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showTargetTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Target Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTargetTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = dueDate ?: dialogOpenTime
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            dueDate = cal.timeInMillis
                            showTargetTimePicker = false
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }
}

// ── T021/US3 — EditTaskDialog time-default helpers ─────────────────────────── //

/**
 * Returns [taskDueDate] if non-null; otherwise defaults to 11:59 PM today via
 * [com.flow.util.DateUtils.defaultEndTime]. Extracted as an `internal` function
 * so it can be unit-tested without the Compose runtime.
 */
internal fun resolveEditDialogDueDate(taskDueDate: Long?): Long? =
    taskDueDate ?: defaultEndTime()

/**
 * Returns [taskStartDate] if non-zero; otherwise returns the current clock time.
 * Handles legacy tasks whose `startDate` was stored as epoch zero or never set.
 */
internal fun resolveEditDialogStartDate(taskStartDate: Long): Long =
    if (taskStartDate == 0L) System.currentTimeMillis() else taskStartDate

/**
 * Returns [taskScheduleMask] if non-null; otherwise returns 127 (all days selected)
 * for backward compatibility with recurring tasks that predate the schedule feature
 * (FR-018).
 */
internal fun resolveInitialScheduleMask(taskScheduleMask: Int?): Int =
    taskScheduleMask ?: 127

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var isRecurring by remember { mutableStateOf(task.isRecurring) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    // T026/US4: initialise from stored mask; null (legacy) defaults to 127 via FR-018
    var scheduleMask by remember { mutableStateOf(resolveInitialScheduleMask(task.scheduleMask)) }

    val currentMillis = System.currentTimeMillis()
    // T021/US3: apply default-time helpers so legacy null/zero values show sensible defaults
    var startDate by remember { mutableLongStateOf(resolveEditDialogStartDate(task.startDate)) }
    var dueDate by remember { mutableStateOf<Long?>(resolveEditDialogDueDate(task.dueDate)) }
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showTargetTimePicker by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit Task", style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen,
                        focusedBorderColor = NeonGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Start Date/Time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Start Date & Time", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(Date(startDate)), color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Date/Time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Target Date & Time (Optional)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showTargetDatePicker = true }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(dueDate?.let { sdf.format(Date(it)) } ?: "Set Target Date", color = if (dueDate == null) Color.Gray else Color.White)
                        if (dueDate != null) {
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { dueDate = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = TaskOverdue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = CheckboxDefaults.colors(checkedColor = NeonGreen)
                    )
                    Text("Track Streak (Recurring)", color = Color.White)
                }

                // T026/US4: Show day-selector below the checkbox when isRecurring is checked
                if (isRecurring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ScheduleDaySelector(
                        mask         = scheduleMask,
                        onMaskChange = { scheduleMask = it },
                        isError      = false   // validation only in AddTaskDialog (T027)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TaskOverdue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                    
                    Button(
                        onClick = { 
                            if (title.isNotBlank()) {
                                onSave(task.copy(
                                    title = title,
                                    startDate = startDate,
                                    dueDate = dueDate,
                                    isRecurring = isRecurring,
                                    scheduleMask = if (isRecurring) scheduleMask else null
                                ))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        modifier = Modifier.weight(2f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Picker Dialogs Logic (Same as AddTaskDialog)
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Task", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${task.title}\"? This action cannot be undone.", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; onDelete() }) {
                    Text("Delete", color = TaskOverdue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = SurfaceDark
        )
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        startDate = mergeDateTime(utcDateToLocalMidnight(it), startDate)
                        showStartDatePicker = false
                        showStartTimePicker = true
                    }
                }) { Text("Next") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = startDate }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = startDate
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            startDate = cal.timeInMillis
                            showStartTimePicker = false
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }

    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate ?: currentMillis)
        DatePickerDialog(
            onDismissRequest = { showTargetDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        dueDate = mergeDateTime(utcDateToLocalMidnight(it), dueDate ?: defaultEndTime())
                        showTargetDatePicker = false
                        showTargetTimePicker = true
                    }
                }) { Text("Next") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTargetTimePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueDate ?: currentMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showTargetTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Target Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTargetTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = dueDate ?: currentMillis
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            dueDate = cal.timeInMillis
                            showTargetTimePicker = false
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusTimerDialog(onDismiss: () -> Unit) {
    var selectedMinutes by remember { mutableStateOf(25) }
    var customMinutes by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(selectedMinutes * 60) }
    var isFinished by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            isFinished = false
            while (timeLeft > 0 && isRunning) {
                kotlinx.coroutines.delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                isRunning = false
                isFinished = true
                // Play Sound
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Focus Timer", style = MaterialTheme.typography.headlineSmall, color = NeonGreen)
                Spacer(modifier = Modifier.height(16.dp))

                if (!isRunning && timeLeft == selectedMinutes * 60) {
                    Text("Select Duration", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Preset Buttons (rest of the code...)
                    // ... (hiding for brevity in target, but I will include it in replacement)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15).forEach { minutes ->
                            FilterChip(
                                selected = selectedMinutes == minutes,
                                onClick = { 
                                    selectedMinutes = minutes
                                    timeLeft = minutes * 60
                                },
                                label = { Text("${minutes}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreen,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(20, 25, 30).forEach { minutes ->
                            FilterChip(
                                selected = selectedMinutes == minutes,
                                onClick = { 
                                    selectedMinutes = minutes
                                    timeLeft = minutes * 60
                                },
                                label = { Text("${minutes}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreen,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() } && it.length <= 3) {
                                customMinutes = it
                                if (it.isNotBlank()) {
                                    selectedMinutes = it.toInt()
                                    timeLeft = selectedMinutes * 60
                                }
                            }
                        },
                        label = { Text("Custom (minutes)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonGreen,
                            focusedBorderColor = NeonGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isFinished) {
                            Text(
                                text = "Focus Session Complete!",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NeonGreen
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Take a break! \uD83D\uDD0B", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.displayLarge,
                                color = NeonGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!isRunning) {
                                Text("Paused", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                            } else {
                                Text("Focusing...", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { 
                        if (isRunning) {
                            isRunning = false
                        } else {
                            isRunning = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isFinished -> "Restart"
                            isRunning -> "Pause"
                            else -> "Start"
                        }
                    )
                }
                
                if (isRunning || timeLeft < selectedMinutes * 60) {
                   TextButton(onClick = { 
                       isRunning = false
                       timeLeft = selectedMinutes * 60
                       isFinished = false
                   }) {
                       Text("Reset", color = Color.Gray)
                   }
                }
            }
        }
    }
}

@Composable
fun TaskStreakDialog(
    task: TaskEntity,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val history by viewModel.getTaskHistory(task.id).collectAsState(initial = emptyList())
    val streak by viewModel.getRawTaskStreak(task.id).collectAsState(initial = 0)
    
    val historyMap = history.associate { it.date to it }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${task.title} Streak",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonGreen,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF31", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$streak Days",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Last 30 Days",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Simple 5x6 or 7x5 grid for last 30 days
                val calendar = Calendar.getInstance()
                // Move to today
                val today = getCalendarStartOfDay(System.currentTimeMillis())
                calendar.timeInMillis = today
                calendar.add(Calendar.DAY_OF_YEAR, -29) // Start from 30 days ago
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) { // 6 rows of 5 days
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(6) {
                                val time = getCalendarStartOfDay(calendar.timeInMillis)
                                val completed = historyMap.containsKey(time)
                                
                                val color = if (completed) NeonGreen else Color.DarkGray
                                
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(color, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = calendar.get(Calendar.DAY_OF_MONTH).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (completed) Color.Black else Color.Gray
                                    )
                                }
                                calendar.add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it!")
                }
            }
        }
    }
}

private fun getCalendarStartOfDay(timeMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ── T024/US4 — ScheduleDaySelector pure helpers ───────────────────────────── //

/**
 * Quick-select presets for the [ScheduleDaySelector].
 *
 * Bit layout (matches [com.flow.domain.streak.DayMask]):
 *   bit 0 = Monday … bit 6 = Sunday
 */
enum class SchedulePreset(val mask: Int) {
    ALL_DAYS(127),   // 0b1111111
    WEEKDAYS(31),    // 0b0011111 — Mon–Fri (bits 0–4)
    WEEKENDS(96)     // 0b1100000 — Sat+Sun (bits 5–6)
}

/**
 * Returns the canonical bitmask value for the given [preset].
 * Extracted as an `internal` function for JVM unit-test access.
 */
internal fun applySchedulePreset(preset: SchedulePreset): Int = preset.mask

/**
 * Toggles the [bit]-th day in [mask] via XOR.
 * Bit 0 = Monday … bit 6 = Sunday.
 */
internal fun toggleDayBit(mask: Int, bit: Int): Int = mask xor (1 shl bit)

/**
 * Returns true when [mask] encodes at least one selected day (or is null, which
 * means "every day" — FR-018).
 */
internal fun isScheduleMaskValid(mask: Int?): Boolean = mask == null || mask != 0

// ── T024 Step 2 — ScheduleDaySelector composable ─────────────────────────── //

private val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

/**
 * A row of preset quick-select buttons and a 7-chip day grid for picking a
 * weekly recurrence schedule.
 *
 * The composable is purely presentational — all logic is handled by [applySchedulePreset]
 * and [toggleDayBit]. It does not produce a valid-mask guard; the caller is
 * responsible for passing [isError] based on ViewModel validation (CO-001).
 *
 * @param mask        Current bitmask (bit 0 = Monday … bit 6 = Sunday). Null = every day.
 * @param onMaskChange Called with the updated mask whenever the user taps a preset or chip.
 * @param isError     When true, shows a red validation message below the chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleDaySelector(
    mask: Int,
    onMaskChange: (Int) -> Unit,
    isError: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Preset quick-select row ───────────────────────────────────────────
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SchedulePreset.entries.forEach { preset ->
                TextButton(onClick = { onMaskChange(applySchedulePreset(preset)) }) {
                    Text(
                        text  = when (preset) {
                            SchedulePreset.ALL_DAYS -> "All"
                            SchedulePreset.WEEKDAYS -> "Weekdays"
                            SchedulePreset.WEEKENDS -> "Weekends"
                        },
                        color = NeonGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Day chips ─────────────────────────────────────────────────────────
        FlowRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            dayLabels.forEachIndexed { bitIndex, label ->
                val selected = (mask shr bitIndex) and 1 == 1
                FilterChip(
                    selected = selected,
                    onClick  = { onMaskChange(toggleDayBit(mask, bitIndex)) },
                    label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor     = NeonGreen,
                        selectedLabelColor         = Color.Black,
                        containerColor             = SurfaceDark,
                        labelColor                 = Color.White
                    )
                )
            }
        }

        // ── Validation error ──────────────────────────────────────────────────
        if (isError) {
            Text(
                text  = "Select at least one day",
                color = TaskOverdue,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

