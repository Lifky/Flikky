package com.example.flikky

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class MainActivityManifestTest {
    @Test
    fun `MainActivity handles locale config changes without recreation`() {
        val configChanges = mainActivity()
            .getAttributeNS(ANDROID_NS, "configChanges")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        assertTrue(
            "MainActivity must handle locale changes to avoid a black flash while switching app language.",
            "locale" in configChanges,
        )
        assertTrue(
            "MainActivity must handle layout direction changes together with locale changes.",
            "layoutDirection" in configChanges,
        )
        assertTrue(
            "MainActivity must also handle screenLayout: the first switch away from the system " +
                "language flips the layout-direction bit inside screenLayout, and any undeclared " +
                "config bit makes the system relaunch the activity (black flash).",
            "screenLayout" in configChanges,
        )
    }

    private fun mainActivity(): Element {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile())
        val activities = document.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val activity = activities.item(index) as Element
            val name = activity.getAttributeNS(ANDROID_NS, "name")
            if (name == ".MainActivity" || name == "com.example.flikky.MainActivity") {
                return activity
            }
        }
        error("MainActivity not found in AndroidManifest.xml")
    }

    private fun manifestFile(): File = listOf(
        File("src/main/AndroidManifest.xml"),
        File("app/src/main/AndroidManifest.xml"),
    ).firstOrNull { it.isFile }
        ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
