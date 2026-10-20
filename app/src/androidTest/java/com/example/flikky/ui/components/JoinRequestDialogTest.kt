package com.example.flikky.ui.components

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.server.JoinRequests
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** D82：手机弹窗显示与请求方页面相同的比对码，用户核对一致再允许。 */
@RunWith(AndroidJUnit4::class)
class JoinRequestDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theDialogShowsTheCompareCode() {
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Surface {
                    JoinRequestDialog(
                        request = JoinRequests.View("r1", "Chrome · Windows", 0L, code = "4821"),
                        onAllow = {},
                        onDeny = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("join-code").assertExists()
        compose.onNodeWithText("4821").assertExists()
    }
}
