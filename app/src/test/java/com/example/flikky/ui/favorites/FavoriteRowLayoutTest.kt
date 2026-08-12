package com.example.flikky.ui.favorites

import androidx.compose.ui.Alignment
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteRowLayoutTest {
    @Test
    fun leadingContentStaysCenteredWhenHeadlineWraps() {
        assertSame(Alignment.CenterVertically, favoriteRowVerticalAlignment())
    }
}
