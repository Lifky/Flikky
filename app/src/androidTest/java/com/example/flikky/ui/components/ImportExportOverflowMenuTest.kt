package com.example.flikky.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportExportOverflowMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuShowsBothActionsAndDispatchesTheirCallbacks() {
        var imported = 0
        var exported = 0
        composeRule.setContent {
            MaterialTheme {
                ImportExportOverflowMenu(
                    importLabel = "导入会话",
                    exportLabel = "导出会话",
                    onImport = { imported++ },
                    onExport = { exported++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("导入会话").assertIsDisplayed().performClick()
        assertEquals(1, imported)

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("导出会话").assertIsDisplayed().performClick()
        assertEquals(1, exported)
    }
}
