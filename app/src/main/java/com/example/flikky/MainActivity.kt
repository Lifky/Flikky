package com.example.flikky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.flikky.ui.history.HistoryScreen
import com.example.flikky.ui.home.HomeScreen
import com.example.flikky.ui.serving.ServingScreen
import com.example.flikky.ui.theme.FlikkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FlikkyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenSession = { id -> nav.navigate("history/$id") },
                                onStartService = { nav.navigate("serving") },
                            )
                        }
                        composable("serving") {
                            ServingScreen(onStopped = { nav.popBackStack("home", inclusive = false) })
                        }
                        composable(
                            route = "history/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { backStack ->
                            val id = backStack.arguments!!.getLong("id")
                            HistoryScreen(
                                sessionId = id,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
