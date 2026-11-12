package com.diettracker.app.data.local

import com.diettracker.app.model.MealRecord
import com.diettracker.app.model.MealType
import kotlinx.coroutines.flow.Flow

interface MealDao {
    fun getMealsByDate(date: String): Flow<List<MealRecord>>
    suspend fun insertMeal(meal: MealRecord): Long
    suspend fun updateMeal(meal: MealRecord)
    suspend fun deleteMeal(meal: MealRecord)
    suspend fun getMealsByDateSync(date: String): List<MealRecord>
    suspend fun replaceMealsForDateAndType(date: String, mealType: MealType, sourceMeals: List<MealRecord>)
}
