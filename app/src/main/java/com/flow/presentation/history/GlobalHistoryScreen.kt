package com.flow.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.ui.theme.NeonGreen
import com.flow.ui.theme.SurfaceDark
import com.flow.ui.theme.TaskInProgress
import com.flow.util.mergeDateTime
import com.flow.util.resolveEditTaskSheetDueDate
import com.flow.util.utcDateToLocalMidnight
import com.flow.util.defaultEndTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.window.Dialog

/**
 * T013 — Global History Screen (US2 P2).
 *
 * Shows all completed tasks grouped or listed chronologically.
 * Two data sources: recurring-task log entries + non-recurring completed tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalHistoryScreen(
    onBack: () -> Unit,
    viewModel: GlobalHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val displayItems = remember(state) { viewModel.filteredItems(state) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonGreen
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonGreen)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Controls row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View-mode toggle
                FilterChip(
                    selected = state.viewMode == HistoryViewMode.DATE_GROUPED,
                    onClick = { viewModel.setViewMode(HistoryViewMode.DATE_GROUPED) },
                    label = { Text("Grouped") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.viewMode == HistoryViewMode.CHRONOLOGICAL,
                    onClick = { viewModel.setViewMode(HistoryViewMode.CHRONOLOGICAL) },
                    label = { Text("Timeline") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                // Filter-mode toggle
                FilterChip(
                    selected = state.filterMode == HistoryFilterMode.COMPLETED_ON,
                    onClick = { viewModel.setFilterMode(HistoryFilterMode.COMPLETED_ON) },
                    label = { Text("Done") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TaskInProgress,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = state.filterMode == HistoryFilterMode.TARGET_DATE,
                    onClick = { viewModel.setFilterMode(HistoryFilterMode.TARGET_DATE) },
                    label = { Text("Target") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TaskInProgress,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            // ── Date strip ───────────────────────────────────────────────────
            if (state.datesWithData.isNotEmpty()) {
                val sortedDates = remember(state.datesWithData) {
                    state.datesWithData.sortedDescending()
                }
                val dateLabel = SimpleDateFormat("MMM d", Locale.getDefault())

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    item {
                        val isAll = state.selectedDateMidnight == null && !state.showAll
                        FilterChip(
                            selected = isAll,
                            onClick = { viewModel.selectDate(null) },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                    items(sortedDates) { midnight ->
                        FilterChip(
                            selected = state.selectedDateMidnight == midnight,
                            onClick = {
                                viewModel.selectDate(
                                    if (state.selectedDateMidnight == midnight) null else midnight
                                )
                            },
                            label = {
                                Text(dateLabel.format(Date(midnight)))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }

            // ── Show-All chip ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FilterChip(
                    selected = state.showAll,
                    onClick = { viewModel.toggleShowAll() },
                    label = { Text("Show All (flat)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
            }

            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

            // ── Main list ────────────────────────────────────────────────────
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No completed tasks yet.\nMark some tasks as done to see them here!",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (state.viewMode == HistoryViewMode.DATE_GROUPED) {
                DateGroupedList(displayItems, onLongClick = { item ->
                    if (item.isRecurring) {
                        viewModel.openActionSheet(item)
                    } else {
                        viewModel.openEditTask(item.taskId)
                    }
                })
            } else {
                ChronologicalList(displayItems, onLongClick = { item ->
                    if (item.isRecurring) {
                        viewModel.openActionSheet(item)
                    } else {
                        viewModel.openEditTask(item.taskId)
                    }
                })
            }
        }

        // T036: Edit-log dialog
        val editingLog = state.editingLog
        if (editingLog != null) {
            HistoryEditDialog(
                log = editingLog,
                editError = state.editError,
                onDismiss = { viewModel.dismissEditLog() },
                onSave = { updatedLog -> viewModel.updateLogEntry(updatedLog) }
            )
        }

        // T023/FR-005: Action sheet for recurring long-press
        if (state.showActionSheet) {
            val target = state.actionSheetTarget
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissActionSheet() },
                containerColor = SurfaceDark
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "Edit history entry",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Edit this day's log", color = Color.White) },
                        supportingContent = { Text("Change the completion date of this entry", color = Color.Gray) },
                        modifier = Modifier.clickable {
                            if (target != null) {
                                target.logId?.let { lid ->
                                    viewModel.openEditLog(
                                        TaskCompletionLog(
                                            id = lid,
                                            taskId = target.taskId,
                                            date = target.completedDayMidnight,
                                            isCompleted = true,
                                            timestamp = target.completedAtMs
                                        )
                                    )
                                }
                            }
                            viewModel.dismissActionSheet()
                        },
                        colors = ListItemDefaults.colors(containerColor = SurfaceDark)
                    )
                    ListItem(
                        headlineContent = { Text("Edit task", color = Color.White) },
                        supportingContent = { Text("Change title, dates, or status of the task", color = Color.Gray) },
                        modifier = Modifier.clickable {
                            if (target != null) {
                                viewModel.openEditTask(target.taskId)
                            }
                            viewModel.dismissActionSheet()
                        },
                        colors = ListItemDefaults.colors(containerColor = SurfaceDark)
                    )
                }
            }
        }

        // T024/FR-006: Task edit sheet
        val editingTask = state.editingTask
        if (editingTask != null) {
            TaskEditSheet(
                task = editingTask,
                onSave = { updatedTask -> viewModel.saveEditTask(updatedTask) },
                onDismiss = { viewModel.dismissEditTask() }
            )
        }
    }
}

// ── Date-grouped list ────────────────────────────────────────────────────────

@Composable
private fun DateGroupedList(items: List<HistoryItem>, onLongClick: (HistoryItem) -> Unit = {}) {
    val dateHeaderFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    val groupedItems = remember(items) {
        items.groupBy { it.completedDayMidnight }
            .entries.sortedByDescending { it.key }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        groupedItems.forEach { (midnight, dayItems) ->
            // Date header
            item(key = "date_$midnight") {
                Text(
                    text = dateHeaderFmt.format(Date(midnight)),
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            // Items for this date
            items(dayItems, key = { "${it.taskId}_${it.completedAtMs}" }) { item ->
                HistoryRow(item = item, onLongClick = { onLongClick(item) })
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.Gray.copy(alpha = 0.2f)
                )
            }
        }
    }
}

// ── Chronological (flat) list ────────────────────────────────────────────────

@Composable
private fun ChronologicalList(items: List<HistoryItem>, onLongClick: (HistoryItem) -> Unit = {}) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(items, key = { "${it.taskId}_${it.completedAtMs}" }) { item ->
            HistoryRow(item = item, showDate = true, onLongClick = { onLongClick(item) })
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Gray.copy(alpha = 0.2f)
            )
        }
    }
}

// ── Single history row ──────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(item: HistoryItem, showDate: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongClick?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Recurring badge
        if (item.isRecurring) {
            Text(
                "🌱",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Text(
                "✅",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.taskTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            if (showDate) {
                Text(
                    text = dateFmt.format(Date(item.completedAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = timeFmt.format(Date(item.completedAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        // Target date badge (if present)
        item.targetDate?.let { target ->
            val targetFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            Surface(
                color = NeonGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "🎯 ${targetFmt.format(Date(target))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── T036: History edit dialog ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryEditDialog(
    log: TaskCompletionLog,
    editError: String?,
    onDismiss: () -> Unit,
    onSave: (TaskCompletionLog) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = log.date,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedMs = datePickerState.selectedDateMillis
                if (selectedMs != null) {
                    onSave(log.copy(date = selectedMs))
                }
            }) { Text("Save", color = NeonGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        colors = DatePickerDefaults.colors(containerColor = SurfaceDark)
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceDark,
                titleContentColor = Color.White,
                headlineContentColor = NeonGreen,
                weekdayContentColor = Color.Gray,
                dayContentColor = Color.White,
                selectedDayContainerColor = NeonGreen,
                selectedDayContentColor = Color.Black,
                todayContentColor = NeonGreen,
                todayDateBorderColor = NeonGreen
            )
        )
        if (editError != null) {
            Text(
                text = editError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    }
}

// ── T021/FR-005: Task edit bottom sheet ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditSheet(
    task: TaskEntity,
    onSave: (TaskEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title    by remember { mutableStateOf(task.title) }
    var startDateMs by remember { mutableStateOf(task.startDate) }
    var dueDateMs   by remember(task) { mutableStateOf(task.dueDate) }
    var status   by remember { mutableStateOf(task.status) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker   by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showDueTimePicker   by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Task", style = MaterialTheme.typography.titleMedium, color = NeonGreen, fontWeight = FontWeight.Bold)

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Start date
            val startFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            OutlinedButton(
                onClick = { showStartDatePicker = true },
                border = BorderStroke(1.dp, Color.Gray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start: ${startFmt.format(Date(startDateMs))}", color = Color.White)
            }

            // Due date
            OutlinedButton(
                onClick = { showDueDatePicker = true },
                border = BorderStroke(1.dp, Color.Gray),
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = dueDateMs?.let { "Due: ${startFmt.format(Date(it))}" } ?: "Due: not set"
                Text(label, color = Color.White)
            }

            // Status chips
            Text("Status", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED).forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = s },
                        label = { Text(s.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            // Save / Cancel
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.Gray)) {
                    Text("Cancel", color = Color.Gray)
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(task.copy(title = title, startDate = startDateMs, dueDate = dueDateMs, status = status))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }

    // Start date picker — chains to start-time picker on confirm
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMs)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        startDateMs = mergeDateTime(utcDateToLocalMidnight(it), startDateMs)
                        showStartDatePicker = false
                        showStartTimePicker = true
                    }
                }) { Text("Next", color = NeonGreen) }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel", color = Color.Gray) } },
            colors = DatePickerDefaults.colors(containerColor = SurfaceDark)
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(
                containerColor = SurfaceDark, titleContentColor = Color.White,
                headlineContentColor = NeonGreen, weekdayContentColor = Color.Gray,
                dayContentColor = Color.White, selectedDayContainerColor = NeonGreen,
                selectedDayContentColor = Color.Black, todayContentColor = NeonGreen,
                todayDateBorderColor = NeonGreen
            ))
        }
    }

    // Start-time picker
    if (showStartTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMs }
        val timePickerState = rememberTimePickerState(
            initialHour   = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Start Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel", color = Color.Gray) }
                        TextButton(onClick = {
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = startDateMs
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            startDateMs = updated.timeInMillis
                            showStartTimePicker = false
                        }) { Text("Confirm", color = NeonGreen) }
                    }
                }
            }
        }
    }

    // Due date picker — chains to due-time picker on confirm
    if (showDueDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dueDateMs = resolveEditTaskSheetDueDate(it, dueDateMs)
                        showDueDatePicker = false
                        showDueTimePicker = true
                    }
                }) { Text("Next", color = NeonGreen) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { dueDateMs = null; showDueDatePicker = false }) { Text("Clear", color = Color.Gray) }
                    TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel", color = Color.Gray) }
                }
            },
            colors = DatePickerDefaults.colors(containerColor = SurfaceDark)
        ) {
            DatePicker(state = pickerState, colors = DatePickerDefaults.colors(
                containerColor = SurfaceDark, titleContentColor = Color.White,
                headlineContentColor = NeonGreen, weekdayContentColor = Color.Gray,
                dayContentColor = Color.White, selectedDayContainerColor = NeonGreen,
                selectedDayContentColor = Color.Black, todayContentColor = NeonGreen,
                todayDateBorderColor = NeonGreen
            ))
        }
    }

    // Due-time picker
    if (showDueTimePicker) {
        val initMs = dueDateMs ?: defaultEndTime()
        val cal = Calendar.getInstance().apply { timeInMillis = initMs }
        val timePickerState = rememberTimePickerState(
            initialHour   = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showDueTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Target Time", color = NeonGreen, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDueTimePicker = false }) { Text("Cancel", color = Color.Gray) }
                        TextButton(onClick = {
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = dueDateMs ?: defaultEndTime()
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE,       timePickerState.minute)
                                set(Calendar.SECOND,       0)
                                set(Calendar.MILLISECOND,  0)
                            }
                            dueDateMs = updated.timeInMillis
                            showDueTimePicker = false
                        }) { Text("Confirm", color = NeonGreen) }
                    }
                }
            }
        }
    }
}
