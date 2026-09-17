package com.example.flikky.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.R
import com.example.flikky.ui.favorites.EmptyFavorites
import com.example.flikky.ui.home.HomeEmptyState
import com.example.flikky.ui.theme.LocalMotionScale
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun homeAndFavoritesAlignInLightTheme() = assertPageAlignment(dark = false)

    @Test fun homeAndFavoritesAlignInDarkTheme() = assertPageAlignment(dark = true)

    private fun assertPageAlignment(dark: Boolean) {
        val home = mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val homeTitle = context.getString(R.string.app_name)
        val favoritesTitle = context.getString(R.string.favorites_title)
        val favoritesDescription = context.getString(R.string.favorites_empty)
        val homeSubtitle = context.getString(R.string.home_tagline)
        composeRule.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalMotionScale provides 0f) {
                    Surface(Modifier.fillMaxSize()) {
                        if (home.value) HomeEmptyState(Modifier.fillMaxSize())
                        else EmptyFavorites(favoritesDescription, Modifier.fillMaxSize())
                    }
                }
            }
        }
        val homeGraphic = composeRule.onNodeWithTag("empty-state-illustration").fetchSemanticsNode().boundsInRoot
        val homeHeading = composeRule.onNodeWithText(homeTitle).fetchSemanticsNode().boundsInRoot
        val homeSupporting = composeRule.onNodeWithText(homeSubtitle).fetchSemanticsNode().boundsInRoot
        saveSnapshot("empty-home-${if (dark) "dark" else "light"}")
        composeRule.runOnIdle { home.value = false }
        val favoriteGraphic = composeRule.onNodeWithTag("empty-state-illustration").fetchSemanticsNode().boundsInRoot
        val favoriteHeading = composeRule.onNodeWithText(favoritesTitle).fetchSemanticsNode().boundsInRoot
        val favoriteSupporting = composeRule.onNodeWithText(favoritesDescription).fetchSemanticsNode().boundsInRoot
        assertEquals(homeGraphic, favoriteGraphic)
        assertEquals(homeHeading, favoriteHeading)
        assertEquals(homeSupporting.top, favoriteSupporting.top, 0.5f)
        saveSnapshot("empty-favorites-${if (dark) "dark" else "light"}")
    }

    @Test
    fun largeTextInShortViewportRemainsReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.size(width = 320.dp, height = 280.dp)) {
                        EmptyStateContent(
                            title = "Flikky",
                            description = "No previous sessions.\nTap Start service to begin your first transfer.",
                            modifier = Modifier.fillMaxSize(),
                            illustration = { Box(Modifier.size(160.dp)) },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("No previous sessions.\nTap Start service to begin your first transfer.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Flikky").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun slowLogoAnimatesAndRespectsMotionOff() {
        composeRule.mainClock.autoAdvance = false
        val motionScale = mutableStateOf(1f)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMotionScale provides motionScale.value) {
                    Surface { FlikkyLogo() }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val first = logoBitmap()
        composeRule.mainClock.advanceTimeBy(1000)
        assertFalse("The slow background must wobble", first.sameAs(logoBitmap()))
        composeRule.runOnIdle { motionScale.value = 0f }
        composeRule.mainClock.advanceTimeByFrame()
        val stopped = logoBitmap()
        composeRule.mainClock.advanceTimeBy(2000)
        assertTrue("Motion-off must keep the logo still", stopped.sameAs(logoBitmap()))
    }

    private fun logoBitmap() = composeRule.onNodeWithTag("flikky-logo").captureToImage().asAndroidBitmap()

    private fun saveSnapshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun titleStaysInPlaceWhenSupportingTextChanges() {
        val home = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                EmptyStateContent(
                    title = "Title",
                    subtitle = if (home.value) "LAN file and message transfer" else null,
                    description = if (home.value) "No previous sessions.\nStart your first transfer." else "Add a favorite.",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val before = composeRule.onNodeWithText("Title").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { home.value = false }
        val after = composeRule.onNodeWithText("Title").fetchSemanticsNode().boundsInRoot
        assertEquals("Empty-state title must not move with supporting copy", before, after)
    }

    @Test
    fun rendersSharedTitleSubtitleAndSemanticDescription() {
        val description = "No previous sessions.\nStart your first transfer."

        composeRule.setContent {
            MaterialTheme {
                EmptyStateContent(
                    title = "Flikky",
                    subtitle = "LAN file and message transfer",
                    description = description,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithText("Flikky").assertIsDisplayed()
        composeRule.onNodeWithText("LAN file and message transfer").assertIsDisplayed()
        composeRule.onNodeWithText(description).assertIsDisplayed()
    }
}
