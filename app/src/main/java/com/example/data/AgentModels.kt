package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_tasks")
data class AgentTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val status: String, // "PENDING", "PLANNING", "EXECUTING", "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis(),
    val finalSummary: String = ""
)

@Entity(tableName = "agent_subtasks")
data class AgentSubTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentTaskId: Int,
    val title: String,
    val thoughtProcess: String,
    val toolUsed: String, // "NONE", "SEARCH", "MEMORY", "CALCULATOR"
    val toolInput: String,
    val toolOutput: String,
    val status: String, // "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_memories")
data class AgentMemory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyName: String,
    val factValue: String,
    val category: String, // "PREFERENCE", "PROFILE", "SYSTEM"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_messages")
data class AgentChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parentTaskId: Int? = null, // Links to a specific task, or null for general chat
    val sender: String, // "USER", "AGENT", "SYSTEM"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
