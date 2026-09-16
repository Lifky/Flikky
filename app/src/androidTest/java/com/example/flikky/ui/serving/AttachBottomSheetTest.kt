package com.example.flikky.ui.serving

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.ui.theme.FlikkyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachBottomSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appsReloadOnTabEntryAndReturnFromSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var scans = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FlikkyTheme(settings = FlikkySettings()) {
                    AttachBottomSheet(
                        existingFiles = emptyList(), installedApps = emptyList(), appsLoading = false,
                        onSendExistingFile = {}, onSendApp = {}, onRefreshApps = { scans++ },
                        onPickFile = {}, onPickImage = {}, onDismiss = {},
                    )
                }
            }
        }
        compose.runOnIdle { assertEquals(0, scans) }
        compose.onNodeWithText(context.getString(R.string.apps_title)).performClick()
        compose.runOnIdle { assertEquals(1, scans) }
        captureServingUi("attach-apps-permission")
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(2, scans) }
        compose.onNodeWithText(context.getString(R.string.attach_title)).performClick()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(2, scans) }
    }

    @Test
    fun attachmentTabsKeepPickersSearchStateAndSendExistingFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int) = compose.onNodeWithText(context.getString(id))
        val rows = listOf(
            FileOverviewRow(1, 1, "Session", null, "app", "report", "Report.pdf", 1024, "application/pdf", 10),
            FileOverviewRow(2, 1, "Session", null, "app", "notes", "Notes.txt", 512, "text/plain", 20),
        )
        val sent = mutableListOf<Long>()
        var filePicks = 0
        var imagePicks = 0
        compose.setContent {
            FlikkyTheme(settings = FlikkySettings()) {
                AttachBottomSheet(
                    existingFiles = rows,
                    installedApps = emptyList(),
                    appsLoading = false,
                    onSendExistingFile = { sent += it.messageId },
                    onSendApp = {},
                    onRefreshApps = {},
                    onPickFile = { filePicks++ },
                    onPickImage = { imagePicks++ },
                    onDismiss = {},
                )
            }
        }
        text(R.string.attach_title).assertIsSelected()
        text(R.string.attach_file).performClick()
        text(R.string.attach_image).performClick()
        assertEquals(1, filePicks)
        assertEquals(1, imagePicks)
        captureServingUi("attach-add-tab")

        text(R.string.files_quick_title).performClick().assertIsSelected()
        compose.onNodeWithText("Report.pdf").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Report")
        compose.onNodeWithText("Notes.txt").assertDoesNotExist()
        text(R.string.attach_title).performClick()
        text(R.string.files_quick_title).performClick()
        compose.onNodeWithText("Notes.txt").assertDoesNotExist()
        compose.onNodeWithText("Report.pdf").performClick()
        assertEquals(listOf(1L), sent)
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Notes.txt").assertIsDisplayed()
        captureServingUi("attach-existing-tab")
    }
}
