package com.example.flikky.ui.serving

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.ui.theme.FlikkyTheme
import com.example.flikky.util.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPickerPermissionTest {
    @get:Rule val rule = createComposeRule()
    private val context = RecordingContext(ApplicationProvider.getApplicationContext())
    private val settingsLabel get() = context.getString(R.string.apps_access_settings)
    private fun app(pkg: String, label: String) = AppEntry(pkg, label, "1", 1, "/base.apk", 0, 10, false)

    @Test fun onlySelfOffersSettingsAndRefreshedAppsRemoveTheHint() {
        val apps = mutableStateOf(listOf(app(context.packageName, "Flikky")))
        show { apps.value }
        rule.onNodeWithText(settingsLabel).assertIsDisplayed().performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.intents.single().action)
        assertEquals("package:${context.packageName}", context.intents.single().dataString)
        rule.runOnIdle { apps.value = apps.value + app("test.reader", "Reader") }
        rule.onNodeWithText("Reader").assertIsDisplayed()
        rule.onNodeWithText(settingsLabel).assertDoesNotExist()
    }

    @Test fun missingSearchStillOffersSettingsWithoutClaimingPermissionWasDenied() {
        show { listOf(app(context.packageName, "Flikky"), app("test.reader", "Reader")) }
        rule.onNode(hasSetTextAction()).performTextInput("missing")
        rule.onNodeWithText(settingsLabel).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.apps_access_title)).assertIsDisplayed()
    }

    @Test fun unavailableAppDetailsFallsBackToSystemAppSettings() {
        context.rejectDetails = true
        show { emptyList() }
        rule.onNodeWithText(settingsLabel).performClick()
        assertEquals(listOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), context.intents.map { it.action })
    }

    private fun show(apps: () -> List<AppEntry>) {
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                FlikkyTheme(settings = FlikkySettings()) { AppPickerContent(apps(), false, {}) }
            }
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        var rejectDetails = false
        override fun startActivity(intent: Intent) {
            intents += intent
            if (rejectDetails && intent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) throw ActivityNotFoundException()
        }
    }
}
