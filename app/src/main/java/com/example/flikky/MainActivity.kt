package com.example.flikky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.ui.components.FlikkyNavBar
import com.example.flikky.ui.components.flikkyNavTransitions
import com.example.flikky.ui.exporting.ExportingScreen
import com.example.flikky.ui.favorites.FavoritesScreen
import com.example.flikky.ui.history.HistoryScreen
import com.example.flikky.ui.home.HomeScreen
import com.example.flikky.ui.serving.ServingScreen
import com.example.flikky.ui.settings.SettingsScreen
import com.example.flikky.ui.theme.FlikkyTheme
import com.example.flikky.ui.theme.Motion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 去掉系统导航栏的对比度浮层（三键导航/手势线），让 App 自己的底栏/内容颜色铺到屏幕最底，
        // 系统栏背景与 App 一致，不再割裂。配合 Theme 里的 isAppearanceLightNavigationBars 保证图标可读。
        window.isNavigationBarContrastEnforced = false

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
                    val topLevel = currentRoute == "transfer" ||
                        (settings.favoriteBetaEnabled && currentRoute == "favorites") ||
                        currentRoute == "settings"

                    // 传输会话进行中（currentSessionId != null，与 HomeViewModel 同一信号）时锁定
                    // 底栏「设置」入口，避免会话期间误入设置改动配置。服务停止后自动解锁。
                    val sessionSnap by ServiceLocator.session.snapshot.collectAsState()
                    val servingActive = sessionSnap.currentSessionId != null

                    var homeSelecting by remember { mutableStateOf(false) }
                    // 主页搜索展开时也隐藏底栏，让搜索铺满全屏。
                    var homeSearchExpanded by remember { mutableStateOf(false) }
                    var favoritesSelecting by remember { mutableStateOf(false) }
                    var favoritesSearchExpanded by remember { mutableStateOf(false) }

                    Scaffold(
                        bottomBar = {
                            // 底栏显隐用滑动+淡入而非硬切。slide 走 effects（无过冲）：底栏锚在屏幕底缘，
                            // 带回弹的 spatial 会过冲令底栏短暂上抬、露出底缝。
                            AnimatedVisibility(
                                visible = topLevel &&
                                    !homeSelecting &&
                                    !homeSearchExpanded &&
                                    !favoritesSelecting &&
                                    !favoritesSearchExpanded,
                                enter = slideInVertically(Motion.effects()) { it } + fadeIn(Motion.effects()),
                                exit = slideOutVertically(Motion.effectsFast()) { it } + fadeOut(Motion.effectsFast()),
                            ) {
                                FlikkyNavBar(
                                    currentRoute = currentRoute,
                                    settingsEnabled = !servingActive,
                                    favoritesEnabled = settings.favoriteBetaEnabled,
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
                        // 逐目的地施加 padding（而非给整个 NavHost），让主页能 escape 顶部 status bar inset：
                        // - 主页：交给 SearchBar 自己处理顶部 inset（折叠时落在状态栏下、展开时铺到状态栏下方）；
                        //   只补底部 inset；搜索展开时连底部也不留 → 真全屏铺满。
                        // - 其余页面：拿到与之前完全一致的 innerPadding（零回归）。
                        val navTransitions = flikkyNavTransitions()
                        NavHost(
                            navController = nav,
                            startDestination = "transfer",
                            enterTransition = navTransitions.enter,
                            exitTransition = navTransitions.exit,
                            popEnterTransition = navTransitions.popEnter,
                            popExitTransition = navTransitions.popExit,
                        ) {
                            composable("transfer") {
                                val homePadding = if (homeSearchExpanded) PaddingValues(0.dp)
                                    else PaddingValues(bottom = innerPadding.calculateBottomPadding())
                                Box(Modifier.padding(homePadding)) {
                                    HomeScreen(
                                        onOpenSession = { id -> nav.navigate("history/$id") },
                                        onStartService = { nav.navigate("serving") },
                                        onStartExport = { nav.navigate("exporting") },
                                        onSelectingChange = { homeSelecting = it },
                                        onSearchExpandedChange = { homeSearchExpanded = it },
                                        onOpenSearchHit = { sessionId, messageId ->
                                            nav.navigate("history/$sessionId?highlight=$messageId")
                                        },
                                    )
                                }
                            }
                            composable("settings") {
                                // 顶部 inset 交给 SettingsScreen 的 LargeTopAppBar 自己消费（标题栏铺到状态栏下方），这里只补底部。
                                Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                    SettingsScreen(
                                        onExport = { nav.navigate("exporting") },
                                    )
                                }
                            }
                            composable("favorites") {
                                Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                    if (settings.favoriteBetaEnabled) {
                                        FavoritesScreen(
                                            onSelectingChange = { favoritesSelecting = it },
                                            onSearchExpandedChange = { favoritesSearchExpanded = it },
                                        )
                                    } else {
                                        LaunchedEffect(Unit) {
                                            nav.navigate("transfer") {
                                                launchSingleTop = true
                                                popUpTo("transfer") { inclusive = false }
                                            }
                                        }
                                    }
                                }
                            }
                            composable("serving") {
                                Box(Modifier.padding(innerPadding)) {
                                    ServingScreen(onStopped = { nav.popBackStack("transfer", inclusive = false) })
                                }
                            }
                            composable("exporting") {
                                Box(Modifier.padding(innerPadding)) {
                                    ExportingScreen(
                                        onBack = { nav.popBackStack("transfer", inclusive = false) },
                                    )
                                }
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
                                Box(Modifier.padding(innerPadding)) {
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
}
