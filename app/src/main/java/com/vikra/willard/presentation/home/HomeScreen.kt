package com.vikra.willard.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
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
import com.vikra.willard.data.local.TaskEntity
import com.vikra.willard.ui.theme.NeonGreen
import com.vikra.willard.ui.theme.SurfaceDark
import com.vikra.willard.ui.theme.TaskInProgress
import com.vikra.willard.ui.theme.TaskOverdue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToTaskHistory: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val history by viewModel.history.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var streakTask by remember { mutableStateOf<TaskEntity?>(null) }
    
    // Calculate Daily Stats
    val todayStats = history.firstOrNull() // Assumes sorted DESC
    val completed = todayStats?.tasksCompletedCount ?: 0
    val total = todayStats?.tasksTotalCount ?: 0
    val percentage = if (total > 0) (completed.toFloat() / total) else 0f
    
    val streakColor = when {
        percentage >= 1f -> NeonGreen
        percentage >= 0.5f -> TaskInProgress // Yellow
        else -> TaskOverdue // Orange
    }

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
            // Dashboard Header
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
                        Text("Daily Progress", style = MaterialTheme.typography.labelLarge, color = Color.White)
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

            // Grid Tasks
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks, key = { it.id }) { task ->
                    val streakCount by viewModel.getRawTaskStreak(task.id).collectAsState(initial = 0)
                    TaskItem(
                        task = task,
                        streakCount = streakCount,
                        onStatusChange = { newStatus ->
                            viewModel.updateTaskStatus(task, newStatus)
                        },
                        onEdit = { editingTask = task },
                        onShowStreak = { onNavigateToTaskHistory(task.id) }
                    )
                }
            }
        }
        
        if (showAddDialog) {
            AddTaskDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, startDate, dueDate, isRecurring ->
                    viewModel.addTask(title, startDate, dueDate, isRecurring)
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
            FocusTimerDialog(onDismiss = { showTimerDialog = false })
        }

        val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
        if (isFirstLaunch) {
            OnboardingDialog(
                onDismiss = { viewModel.completeOnboarding() }
            )
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: TaskEntity,
    streakCount: Int,
    onStatusChange: (com.vikra.willard.data.local.TaskStatus) -> Unit,
    onEdit: () -> Unit,
    onShowStreak: () -> Unit
) {
    val status = task.status
    val isCompleted = status == com.vikra.willard.data.local.TaskStatus.COMPLETED
    val isInProgress = status == com.vikra.willard.data.local.TaskStatus.IN_PROGRESS
    
    val cardColor = when (status) {
        com.vikra.willard.data.local.TaskStatus.COMPLETED -> NeonGreen
        com.vikra.willard.data.local.TaskStatus.IN_PROGRESS -> TaskInProgress
        else -> SurfaceDark
    }
    
    val animatedColor by animateColorAsState(
        targetValue = cardColor,
        label = "BoxColor"
    )
    
    // Status Logic
    val isOverdue = !isCompleted && task.dueDate != null && task.dueDate < System.currentTimeMillis()
    val borderColor = when {
        isOverdue -> TaskOverdue
        isInProgress -> TaskInProgress
        isCompleted -> NeonGreen
        else -> Color.Gray.copy(alpha=0.5f)
    }
    val contentColor = if (isCompleted) Color.Black else Color.White

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .combinedClickable(
                onClick = {
                    // Cycle: TODO → IN_PROGRESS → COMPLETED → TODO
                    val nextStatus = when (status) {
                        com.vikra.willard.data.local.TaskStatus.TODO -> com.vikra.willard.data.local.TaskStatus.IN_PROGRESS
                        com.vikra.willard.data.local.TaskStatus.IN_PROGRESS -> com.vikra.willard.data.local.TaskStatus.COMPLETED
                        com.vikra.willard.data.local.TaskStatus.COMPLETED -> com.vikra.willard.data.local.TaskStatus.TODO
                    }
                    onStatusChange(nextStatus)
                },
                onLongClick = { onEdit() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor
        ),
        border = if (isCompleted) null else BorderStroke(1.dp, borderColor)
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
                     com.vikra.willard.data.local.TaskStatus.COMPLETED -> Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                     com.vikra.willard.data.local.TaskStatus.IN_PROGRESS -> Text("⏳", style = MaterialTheme.typography.bodyLarge)
                     else -> Box(modifier = Modifier.size(24.dp))
                 }
                 
                  if (task.isRecurring) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.clickable { onShowStreak() }
                      ) {
                          Text("🌱", style = MaterialTheme.typography.bodyLarge)
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
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, Long, Long?, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    
    val currentMillis = System.currentTimeMillis()
    var startDate by remember { mutableLongStateOf(currentMillis) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (text.isNotBlank()) {
                            onAdd(text, startDate, dueDate, isRecurring)
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
                        startDate = it 
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
                                set(Calendar.MINUTE, timePickerState.minute)
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
                        dueDate = it 
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
                                set(Calendar.MINUTE, timePickerState.minute)
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
    
    val currentMillis = System.currentTimeMillis()
    var startDate by remember { mutableLongStateOf(task.startDate) }
    var dueDate by remember { mutableStateOf<Long?>(task.dueDate) }
    
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
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
                                    isRecurring = isRecurring
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
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { 
                        startDate = it 
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
                                set(Calendar.MINUTE, timePickerState.minute)
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
                        dueDate = it 
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
                                set(Calendar.MINUTE, timePickerState.minute)
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
                            Text("Take a break! 🔋", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
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
                    Text("🌱", style = MaterialTheme.typography.displaySmall)
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
