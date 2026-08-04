package com.example.flikky.ui.files

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreateDocumentDynamicMimeTest {
    @Test
    fun `createIntent uses row mime and filename`() {
        val intent = CreateDocumentDynamicMime().createIntent(
            ApplicationProvider.getApplicationContext(),
            "image/png" to "photo.png",
        )

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals("photo.png", intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
