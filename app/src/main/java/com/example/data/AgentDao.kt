package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    // --- Tasks ---
    @Query("SELECT * FROM agent_tasks ORDER BY timestamp DESC")
    fun getAllTasks(): Flow<List<AgentTask>>

    @Query("SELECT * FROM agent_tasks WHERE id = :taskId LIMIT 1")
    fun getTaskById(taskId: Int): Flow<AgentTask?>

    @Query("SELECT * FROM agent_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskByIdSync(taskId: Int): AgentTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AgentTask): Long

    @Update
    suspend fun updateTask(task: AgentTask)

    @Delete
    suspend fun deleteTask(task: AgentTask)

    @Query("DELETE FROM agent_tasks")
    suspend fun clearAllTasks()

    // --- SubTasks ---
    @Query("SELECT * FROM agent_subtasks WHERE parentTaskId = :taskId ORDER BY timestamp ASC")
    fun getSubTasksForTask(taskId: Int): Flow<List<AgentSubTask>>

    @Query("SELECT * FROM agent_subtasks WHERE parentTaskId = :taskId ORDER BY timestamp ASC")
    suspend fun getSubTasksForTaskSync(taskId: Int): List<AgentSubTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: AgentSubTask): Long

    @Update
    suspend fun updateSubTask(subTask: AgentSubTask)

    @Query("DELETE FROM agent_subtasks WHERE parentTaskId = :taskId")
    suspend fun deleteSubTasksForTask(taskId: Int)

    // --- Memories ---
    @Query("SELECT * FROM agent_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<AgentMemory>>

    @Query("SELECT * FROM agent_memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesSync(): List<AgentMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemory): Long

    @Query("DELETE FROM agent_memories WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: Int)

    @Query("DELETE FROM agent_memories")
    suspend fun clearAllMemories()

    // --- Chat Messages ---
    @Query("SELECT * FROM agent_messages WHERE parentTaskId IS NULL ORDER BY timestamp ASC")
    fun getGeneralChatMessages(): Flow<List<AgentChatMessage>>

    @Query("SELECT * FROM agent_messages WHERE parentTaskId = :taskId ORDER BY timestamp ASC")
    fun getTaskChatMessages(taskId: Int): Flow<List<AgentChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AgentChatMessage): Long

    @Query("DELETE FROM agent_messages")
    suspend fun clearAllMessages()
}
