package com.example.flikky.ui.serving

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServingFloatingActionsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun selectedMessageSettlesAndCopyDismissesTheToolbar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = mutableStateOf<Long?>(null)
        val settings = FlikkySettings(messageActionStyle = MessageActionStyle.FLOATING)
        rule.setContent {
            FlikkyTheme(settings = settings) {
                ServingChatTab(
                    viewModel = viewModel(),
                    ui = ServingUiState(messages = listOf(Message.Text(1, Origin.BROWSER, 1, "Copy this"))),
                    settings = settings, progressMap = emptyMap(), peerAvatarId = 0,
                    peerAvatarKey = "icon:desktop_windows", actionTarget = target.value,
                    onActionTargetChange = { target.value = it },
                    snackbarHostState = remember { SnackbarHostState() }, scope = rememberCoroutineScope(),
                    existingFiles = emptyList(), onSendExistingFile = {},
                )
            }
        }
        rule.onNodeWithText("Copy this").performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.serving_copy))
            .assertIsDisplayed().performClick()
        rule.runOnIdle { assertNull(target.value) }
    }
}
