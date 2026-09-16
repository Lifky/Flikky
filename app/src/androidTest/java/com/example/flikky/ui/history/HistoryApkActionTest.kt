package com.example.flikky.ui.history

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.R
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.theme.FlikkyTheme
import com.example.flikky.util.MimeGuess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryApkActionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var context: RecordingContext
    private var sessionId = 0L
    private lateinit var originalStyle: MessageActionStyle
    private val messageId = System.currentTimeMillis()

    @Before fun setUp(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        context = RecordingContext(app)
        originalStyle = ServiceLocator.settingsRepository.settings.first().messageActionStyle
        sessionId = ServiceLocator.database.sessionDao().insert(SessionEntity(
            startedAt = messageId, endedAt = messageId + 1, name = "APK history test",
        ))
        ServiceLocator.repository.appendMessage(sessionId, Message.File(
            id = messageId, origin = Origin.BROWSER, timestamp = messageId,
            fileId = "apk-history-test", name = "Demo.apk", sizeBytes = 8,
            mime = MimeGuess.APK_MIME, status = Message.File.Status.COMPLETED,
        ))
        ServiceLocator.fileStore.messageFile(sessionId, "apk-history-test").apply {
            parentFile?.mkdirs()
            writeText("test-apk")
        }
    }

    @After fun tearDown(): Unit = runBlocking {
        ServiceLocator.settingsRepository.setMessageActionStyle(originalStyle)
        ServiceLocator.database.sessionDao().getById(sessionId)?.let {
            ServiceLocator.database.sessionDao().delete(it)
        }
        ServiceLocator.fileStore.deleteSessionDir(sessionId)
    }

    private fun show(style: MessageActionStyle) {
        runBlocking { ServiceLocator.settingsRepository.setMessageActionStyle(style) }
        rule.setContent {
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(LocalContext provides context, LocalActivityResultRegistryOwner provides registryOwner) {
                FlikkyTheme(settings = com.example.flikky.data.settings.FlikkySettings()) {
                    HistoryScreen(sessionId, onBack = {})
                }
            }
        }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Demo.apk").fetchSemanticsNodes().isNotEmpty() }
        if (style == MessageActionStyle.FLOATING) rule.onNodeWithText("Demo.apk").performClick()
    }

    @Test fun inlineHistoryOffersInstallAndUsesTheStoredApk() {
        verifyInstall(MessageActionStyle.INLINE)
    }

    @Test fun floatingHistoryOffersInstallAndUsesTheStoredApk() {
        verifyInstall(MessageActionStyle.FLOATING)
    }

    private fun verifyInstall(style: MessageActionStyle) {
        show(style)
        val label = context.getString(R.string.files_action_install)
        rule.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        rule.runOnIdle {
            val intent = requireNotNull(context.startedIntent)
            assertTrue(intent.action == Intent.ACTION_VIEW || intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            if (intent.action == Intent.ACTION_VIEW) assertEquals(MimeGuess.APK_MIME, intent.type)
            else assertEquals("package:${context.packageName}", intent.dataString)
        }
        runBlocking { ServiceLocator.repository.markFileDeleted(messageId) }
        if (style == MessageActionStyle.FLOATING) rule.onNodeWithText("Demo.apk").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithContentDescription(label).assertCountEquals(0)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null
        override fun startActivity(intent: Intent) { startedIntent = intent }
    }
}
