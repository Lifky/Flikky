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

@RunWith(AndroidJUnit4::class)
class ConnectionInfoCardPinUsedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aUsedPinNamesItsDeviceInsteadOfShowingStaleDigits() {
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                Surface {
                    ConnectionInfoCard(
                        address = connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Disabled),
                        pin = "123456",
                        pinUsedBy = "张三的笔记本",
                    )
                }
            }
        }
        compose.onNodeWithTag("pin-used-by").assertExists()
        compose.onNodeWithText("已被「张三的笔记本」使用").assertExists()
        compose.onNodeWithText("123456").assertDoesNotExist()
    }
}
