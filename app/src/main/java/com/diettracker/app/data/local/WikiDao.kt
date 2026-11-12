package com.diettracker.app.data.local

import com.diettracker.app.model.WikiArticle
import kotlinx.coroutines.flow.Flow

interface WikiDao {
    fun getAllArticles(): Flow<List<WikiArticle>>
    suspend fun getAllArticlesSync(): List<WikiArticle>
    suspend fun insertArticle(article: WikiArticle): Long
    suspend fun updateArticle(article: WikiArticle)
    suspend fun deleteArticle(article: WikiArticle)
}
