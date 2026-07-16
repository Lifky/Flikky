package com.example.flikky.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStateContentTest {
    @get:Rule
    val composeRule = createComposeRule()

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
