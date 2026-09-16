package com.example.flikky.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrCodeRenderingTest {
    @get:Rule val rule = createComposeRule()
    private val url = "http://192.168.1.7:8080"

    private fun render(surface: Color): android.graphics.Bitmap {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = surface)) {
                QrCodeSheet(url = url, onDismiss = {})
            }
        }
        rule.waitForIdle()
        return rule.onNodeWithTag("qr-code").captureToImage().asAndroidBitmap().also { bitmap ->
            val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: return@also
            val file = java.io.File(directory,
                if (surface == Color(0xFFFFF8FA)) "qr-light.png" else "qr-dark.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun assertDecodes(bitmap: android.graphics.Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        assertEquals(url, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
    }

    @Test fun lightSurfaceBlendsIntoSheetAndStillDecodes() {
        val surface = Color(0xFFFFF8FA)
        val bitmap = render(surface)
        assertEquals(surface.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 10))
        assertDecodes(bitmap)
    }

    @Test fun darkSurfaceKeepsLightQuietZoneWithSoftCornersAndStillDecodes() {
        val surface = Color(0xFF181218)
        val bitmap = render(surface)
        assertEquals(Color.White.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 10))
        assertEquals(surface.toArgb(), bitmap.getPixel(bitmap.width / 60, bitmap.height / 60))
        assertDecodes(bitmap)
    }
}
