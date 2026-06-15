package com.example.flikky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.ui.components.FlikkyNavBar
import com.example.flikky.ui.exporting.ExportingScreen
import com.example.flikky.ui.history.HistoryScreen
import com.example.flikky.ui.home.HomeScreen
import com.example.flikky.ui.search.SearchScreen
import com.example.flikky.ui.serving.ServingScreen
import com.example.flikky.ui.settings.SettingsScreen
import com.example.flikky.ui.theme.FlikkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by ServiceLocator.settingsRepository.settings
                .collectAsState(initial = FlikkySettings())
            FlikkyTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val nav = rememberNavController()
                    val backStackEntry by nav.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val topLevel = currentRoute == "transfer" || currentRoute == "settings"

                    // 传输会话进行中（currentSessionId != null，与 HomeViewModel 同一信号）时锁定
                    // 底栏「设置」入口，避免会话期间误入设置改动配置。服务停止后自动解锁。
                    val sessionSnap by ServiceLocator.session.snapshot.collectAsState()
                    val servingActive = sessionSnap.currentSessionId != null

                    Scaffold(
                        bottomBar = {
                            if (topLevel) {
                                FlikkyNavBar(
                                    currentRoute = currentRoute,
                                    settingsEnabled = !servingActive,
                                ) { dest ->
                                    nav.navigate(dest) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    }
                                }
                            }
                        },
                    ) { innerPadding ->
                        NavHost(
                            navController = nav,
                            startDestination = "transfer",
                            modifier = Modifier.padding(innerPadding),
                        ) {
                            composable("transfer") {
                                HomeScreen(
                                    onOpenSession = { id -> nav.navigate("history/$id") },
                                    onStartService = { nav.navigate("serving") },
                                    onStartExport = { nav.navigate("exporting") },
                                    onOpenSearch = { nav.navigate("search") },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onExport = { nav.navigate("exporting") },
                                )
                            }
                            composable("serving") {
                                ServingScreen(onStopped = { nav.popBackStack("transfer", inclusive = false) })
                            }
                            composable("exporting") {
                                ExportingScreen(
                                    onBack = { nav.popBackStack("transfer", inclusive = false) },
                                )
                            }
                            composable("search") {
                                SearchScreen(
                                    onBack = { nav.popBackStack() },
                                    onOpenHit = { sessionId, messageId ->
                                        nav.navigate("history/$sessionId?highlight=$messageId")
                                    },
                                )
                            }
                            composable(
                                route = "history/{id}?highlight={messageId}",
                                arguments = listOf(
                                    navArgument("id") { type = NavType.LongType },
                                    navArgument("messageId") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    },
                                ),
                            ) { backStack ->
                                val id = backStack.arguments!!.getLong("id")
                                val highlight = backStack.arguments!!.getLong("messageId").takeIf { it > 0L }
                                HistoryScreen(
                                    sessionId = id,
                                    highlightMessageId = highlight,
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
