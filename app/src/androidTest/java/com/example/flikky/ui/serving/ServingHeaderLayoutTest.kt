package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.ui.components.ConversationHeader
import com.example.flikky.ui.components.AvatarKey
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServingHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactHeaderKeepsThreeRoundActionsAndVisibilityReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = mutableStateOf(360.dp)
        val subtitle = mutableStateOf("可见：文件、收藏")
        val calls = mutableListOf<String>()
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Surface {
                    Box(Modifier.systemBarsPadding()) {
                        Column(Modifier.width(width.value).testTag("header")) {
                            ConversationHeader(
                                peerAvatarId = 0,
                                peerAvatarKey = AvatarKey.DEFAULT_PEER,
                                peerName = "对端设备",
                                subtitle = subtitle.value,
                                onAvatarClick = {},
                                onPermissionsClick = { calls += "permissions" },
                                onSettingsClick = { calls += "settings" },
                                onStopClick = { calls += "stop" },
                            )
                        }
                    }
                }
            }
        }
        val actions = listOf(R.string.peer_permissions_entry, R.string.serving_quick_settings,
            R.string.serving_stop_service).map { compose.onNodeWithContentDescription(context.getString(it)) }
        val bounds = actions.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        val density = context.resources.displayMetrics.density
        bounds.forEach { rect ->
            assertEquals("Round button hit region", rect.width, rect.height, 1f)
            assertEquals(bounds.first().center.y, rect.center.y, 1f)
        }
        actions.forEach { it.assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp) }
        assertTrue(bounds[0].right <= bounds[1].left)
        assertTrue(bounds[1].right <= bounds[2].left)
        val headerHeight = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Normal header should fit 64dp", headerHeight <= 64 * density + 1)
        compose.onNodeWithText("已连接").assertDoesNotExist()
        compose.onNodeWithText(subtitle.value).assertIsDisplayed()
        captureServingUi("serving-header-360dp")
        actions.forEach { it.performClick() }
        assertEquals(listOf("permissions", "settings", "stop"), calls)

        compose.runOnIdle {
            width.value = 320.dp
            subtitle.value = "Visible: Files, Favorites"
        }
        compose.onNodeWithText(subtitle.value).assertIsDisplayed()
        actions.forEach { it.assertIsDisplayed() }
        val text = compose.onNodeWithText(subtitle.value).fetchSemanticsNode().boundsInRoot
        val firstAction = actions.first().fetchSemanticsNode().boundsInRoot
        assertTrue("Visibility must not overlap the actions", text.right <= firstAction.left)
        captureServingUi("serving-header-320dp")
    }
}
