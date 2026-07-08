package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AgentRepository

    // Base databases / state
    val allTasks: StateFlow<List<AgentTask>>
    val allMemories: StateFlow<List<AgentMemory>>
    val generalChatMessages: StateFlow<List<AgentChatMessage>>
    val isApiKeyConfigured: Boolean

    // Selected Task Detail States
    private val _selectedTaskId = MutableStateFlow<Int?>(null)
    val selectedTaskId: StateFlow<Int?> = _selectedTaskId.asStateFlow()

    val selectedTask: StateFlow<AgentTask?> = _selectedTaskId
        .flatMapLatest { id ->
            if (id != null) repository.getTaskById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSubtasks: StateFlow<List<AgentSubTask>> = _selectedTaskId
        .flatMapLatest { id ->
            if (id != null) repository.getSubTasksForTask(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedTaskChat: StateFlow<List<AgentChatMessage>> = _selectedTaskId
        .flatMapLatest { id ->
            if (id != null) repository.getTaskChatMessages(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks which tasks are currently running/executing
    private val _executingTaskIds = MutableStateFlow<Set<Int>>(emptySet())
    val executingTaskIds: StateFlow<Set<Int>> = _executingTaskIds.asStateFlow()

    init {
        val database = AgentDatabase.getDatabase(application)
        val dao = database.agentDao()
        repository = AgentRepository(dao, application)

        allTasks = repository.allTasks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allMemories = repository.allMemories
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        generalChatMessages = repository.generalChatMessages
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        isApiKeyConfigured = repository.isApiKeyConfigured()
    }

    // --- Chat Actions ---
    fun sendGeneralMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendGeneralMessage(text)
        }
    }

    // --- Task Actions ---
    fun createTaskAndStartLoop(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val taskId = repository.createTask(title)
            _selectedTaskId.value = taskId
            executeTaskLoop(taskId)
        }
    }

    fun retryTask(taskId: Int) {
        viewModelScope.launch {
            executeTaskLoop(taskId)
        }
    }

    private fun executeTaskLoop(taskId: Int) {
        if (_executingTaskIds.value.contains(taskId)) return
        _executingTaskIds.value = _executingTaskIds.value + taskId
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.planAndExecuteTask(taskId)
            } finally {
                _executingTaskIds.value = _executingTaskIds.value - taskId
            }
        }
    }

    fun selectTask(taskId: Int?) {
        _selectedTaskId.value = taskId
    }

    fun deleteTask(task: AgentTask) {
        viewModelScope.launch {
            if (_selectedTaskId.value == task.id) {
                _selectedTaskId.value = null
            }
            repository.deleteTask(task)
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            _selectedTaskId.value = null
            repository.clearAllTasks()
        }
    }

    // --- Memory Actions ---
    fun addMemory(key: String, value: String, category: String = "PREFERENCE") {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            repository.addMemory(key, value, category)
        }
    }

    fun deleteMemory(memoryId: Int) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }
}
