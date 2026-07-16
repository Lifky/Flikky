package com.example.flikky.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsExternalLinkTest {
    @Test
    fun openExternalLinkStartsActionViewForRepository() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

        assertEquals(EXPECTED_REPOSITORY_URL, OPEN_SOURCE_REPOSITORY_URL)
        val opened = openExternalLink(context, OPEN_SOURCE_REPOSITORY_URL)

        assertTrue(opened)
        assertEquals(Intent.ACTION_VIEW, context.startedIntent?.action)
        assertEquals(EXPECTED_REPOSITORY_URL, context.startedIntent?.dataString)
    }

    @Test
    fun openExternalLinkReturnsFalseWhenNoHandlerExists() {
        val context = RecordingContext(
            base = InstrumentationRegistry.getInstrumentation().targetContext,
            failToOpen = true,
        )

        val opened = openExternalLink(context, OPEN_SOURCE_REPOSITORY_URL)

        assertFalse(opened)
    }

    private class RecordingContext(
        base: Context,
        private val failToOpen: Boolean = false,
    ) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun startActivity(intent: Intent) {
            if (failToOpen) throw ActivityNotFoundException()
            startedIntent = intent
        }
    }

    private companion object {
        const val EXPECTED_REPOSITORY_URL = "https://github.com/Lifky/Flikky"
    }
}
