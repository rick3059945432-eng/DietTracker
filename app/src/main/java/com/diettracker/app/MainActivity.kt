package com.diettracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.diettracker.app.data.local.DietDatabase
import com.diettracker.app.data.preferences.UserPreferencesManager
import com.diettracker.app.data.remote.OpenAIService
import com.diettracker.app.ui.screens.HomeScreen
import com.diettracker.app.ui.screens.SettingsScreen
import com.diettracker.app.ui.theme.DietTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = DietDatabase.getDatabase(this)
        val prefsManager = UserPreferencesManager(this)
        val openAIService = OpenAIService()

        setContent {
            val appearance by prefsManager.appearanceConfig.collectAsState()

            DietTrackerTheme(darkTheme = appearance.isDarkMode) {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            db = database,
                            prefsManager = prefsManager,
                            openAIService = openAIService,
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            prefsManager = prefsManager,
                            openAIService = openAIService,
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
