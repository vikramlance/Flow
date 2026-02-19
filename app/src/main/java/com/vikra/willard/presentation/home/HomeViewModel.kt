package com.vikra.willard.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikra.willard.data.local.TaskEntity
import com.vikra.willard.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsManager: com.vikra.willard.data.local.SettingsManager
) : ViewModel() {

    val isFirstLaunch = settingsManager.isFirstLaunch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsManager.setFirstLaunchCompleted()
        }
    }

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val history = repository.getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkAndPopulateDummyData()
        viewModelScope.launch {
            repository.refreshRecurringTasks()
        }
    }

    fun addTask(title: String, startDate: Long = System.currentTimeMillis(), dueDate: Long? = null, isRecurring: Boolean = false) {
        viewModelScope.launch {
            repository.addTask(title, startDate, dueDate, isRecurring)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun updateTaskStatus(task: TaskEntity, newStatus: com.vikra.willard.data.local.TaskStatus) {
        viewModelScope.launch {
            repository.updateTaskStatus(task, newStatus)
        }
    }
    
    fun deleteTask(task: TaskEntity) {
         viewModelScope.launch {
            repository.deleteTask(task)
        }
    }
    
    fun getTaskStreak(taskId: Long): StateFlow<Int> {
        // Just a simple bridge. UI should collect this.
        // Convert Flow to StateFlow if needed or just return Flow. 
        // Composable can allow collecting Flow. Let's return Flow to avoid too many StateFlows in VM.
        // But the previous implementation returned Flow.
        // Limitation: ViewModel usually exposes StateFlow/LiveData. 
        // Let's defer this to UI. For now, exposing Flow is fine.
        return kotlinx.coroutines.flow.MutableStateFlow(0) // Placeholder
    }
    
    // Better: Helper query
    fun getRawTaskStreak(taskId: Long) = repository.getTaskStreak(taskId)
    
    fun getTaskHistory(taskId: Long) = repository.getTaskHistory(taskId)
    
    private fun checkAndPopulateDummyData() {
        viewModelScope.launch {
            // Check if DB is empty
            if (repository.getCompletedTaskCount().firstOrNull() == 0 && 
                repository.getAllTasks().firstOrNull()?.isEmpty() == true) {
                    
                val tasks = listOf(
                    Triple("Morning Jog \uD83C\uDFC3\u200D\u2642\uFE0F", null, true),
                    Triple("Read a Book \uD83D\uDCD6", null, true),
                    Triple("Team Meeting \uD83D\uDCBC", System.currentTimeMillis() + 3600000, false),
                    Triple("Buy Groceries \uD83D\uDED2", System.currentTimeMillis() + 7200000, false),
                    Triple("Water Plants \uD83C\uDF3F", null, true)
                )
                
                tasks.forEach {
                    repository.addTask(title = it.first, dueDate = it.second, isRecurring = it.third)
                }
            }
        }
    }
}
