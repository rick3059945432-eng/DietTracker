package com.diettracker.app.data.local

import com.diettracker.app.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatDao {
    fun getMessagesByDate(date: String): Flow<List<ChatMessage>>
    suspend fun getMessagesByDateSync(date: String): List<ChatMessage>
    suspend fun insertMessage(message: ChatMessage): Long
    suspend fun clearMessagesForDate(date: String)
}
