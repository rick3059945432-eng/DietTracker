package com.diettracker.app.data.local

import com.diettracker.app.model.CustomFood
import kotlinx.coroutines.flow.Flow

interface CustomFoodDao {
    fun getAllCustomFoods(): Flow<List<CustomFood>>
    suspend fun insertCustomFood(food: CustomFood): Long
    suspend fun updateCustomFood(food: CustomFood)
    suspend fun deleteCustomFood(food: CustomFood)
    suspend fun getAllCustomFoodsSync(): List<CustomFood>
}
