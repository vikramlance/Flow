package com.flow.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.repository.SettingsRepository
import com.flow.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _helpVisible = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getHomeScreenTasks(),
        repository.getTodayProgress(),
        settingsRepository.isFirstLaunch,
        _helpVisible
    ) { homeTasks, todayProgressState, isFirstLaunch, helpVisible ->
        val now = System.currentTimeMillis()
        HomeUiState(
            homeTasks          = homeTasks.map { HomeTaskItem(it, it.urgencyLevel(now)) },
            todayProgressState = todayProgressState,
            isFirstLaunch      = isFirstLaunch,
            isLoading          = false,
            showHelp           = helpVisible
        )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5000),
        initialValue  = HomeUiState()
    )

    init {
        checkAndPopulateDummyData()
        viewModelScope.launch {
            repository.refreshRecurringTasks()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setFirstLaunchCompleted()
        }
    }

    fun addTask(
        title: String,
        startDate: Long = System.currentTimeMillis(),
        dueDate: Long? = null,
        isRecurring: Boolean = false,
        scheduleMask: Int? = null
    ) {
        viewModelScope.launch {
            repository.addTask(title, startDate, dueDate, isRecurring, scheduleMask)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus) {
        viewModelScope.launch {
            repository.updateTaskStatus(task, newStatus)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun getRawTaskStreak(taskId: Long) = repository.getTaskStreak(taskId)

    fun getTaskHistory(taskId: Long) = repository.getTaskHistory(taskId)

    fun showHelp() { _helpVisible.value = true }

    fun hideHelp() { _helpVisible.value = false }

    private fun checkAndPopulateDummyData() {
        viewModelScope.launch {
            if (repository.getCompletedTaskCount().firstOrNull() == 0 &&
                repository.getAllTasks().firstOrNull()?.isEmpty() == true
            ) {
                listOf(
                    Triple("Morning Jog \uD83C\uDFC3\u200D\u2642\uFE0F", null, true),
                    Triple("Read a Book \uD83D\uDCD6", null, true),
                    Triple("Team Meeting \uD83D\uDCBC", System.currentTimeMillis() + 3_600_000L, false),
                    Triple("Buy Groceries \uD83D\uDED2", System.currentTimeMillis() + 7_200_000L, false),
                    Triple("Water Plants \uD83C\uDF3F", null, true)
                ).forEach { (title, due, recurring) ->
                    repository.addTask(title = title, dueDate = due, isRecurring = recurring)
                }
            }
        }
    }
}
