package com.example.flikky.ui.serving

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.session.PeerDevice
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicesSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rowsShowNameStatusAndTheDisconnectActionReachesTheCaller() {
        val disconnected = mutableListOf<String>()
        val devices = listOf(
            PeerDevice(deviceId = "a", name = "张三的笔记本", defaultLabel = "Chrome · Windows", avatarKey = "icon:star",
                connectedAt = 1L, connections = 1, visible = true),
            PeerDevice(deviceId = "b", defaultLabel = "Safari · macOS", avatarKey = "icon:face",
                connectedAt = 2L, visible = true),
        )
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Surface {
                    DevicesSheet(
                        devices = devices,
                        onRename = {},
                        onChangeAvatar = {},
                        onDisconnect = { disconnected += it.deviceId },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("张三的笔记本").assertExists()
        compose.onNodeWithText("Chrome · Windows · 在线").assertExists()
        compose.onNodeWithText("Safari").assertExists()
        compose.onNodeWithText("Safari · macOS · 离线").assertExists()
        // 在线的「张三的笔记本」排第一行，取第一个 ⋮。
        compose.onAllNodesWithContentDescription("更多操作")[0].performClick()
        compose.onNodeWithText("断开").performClick()
        assertEquals(listOf("a"), disconnected)
    }
}
