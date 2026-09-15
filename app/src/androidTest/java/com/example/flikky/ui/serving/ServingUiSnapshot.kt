package com.example.flikky.ui.serving

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saved by the connected-test runner alongside its test reports. */
internal fun captureServingUi(name: String) {
    val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
    val output = File(directory, "$name.png")
    output.parentFile?.mkdirs()
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    checkNotNull(bitmap)
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
