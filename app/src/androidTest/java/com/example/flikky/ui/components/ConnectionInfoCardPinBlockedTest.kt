package com.example.flikky.ui.components

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.session.connectionAddresses
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v1.24（B59#5）：输错 5 次后 PIN 停用，卡片不再显示一串用不了的数字，而是说清楚怎么恢复。 */
@RunWith(AndroidJUnit4::class)
class ConnectionInfoCardPinBlockedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aBlockedPinIsNotShownAndTheCardSaysHowToRecover() {
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Surface {
                    ConnectionInfoCard(
                        address = connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Disabled),
                        pin = "123456",
                        pinUsedBy = "张三的笔记本",
                        pinBlocked = true,
                    )
                }
            }
        }
        compose.onNodeWithTag("pin-blocked").assertExists()
        compose.onNodeWithText("123456").assertDoesNotExist()
        compose.onNodeWithTag("pin-used-by").assertDoesNotExist()
    }
}
