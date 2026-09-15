package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFileNameTest {
    @Test fun `ordinary chinese app uses name underscore version`() { assertEquals("微信_8.0.61.apk", apkFileName("微信", "8.0.61", 1, "x")) }
    @Test fun `spaces in the label become underscores`() { assertEquals("Solid_Explorer_2.8.44.apk", apkFileName("Solid Explorer", "2.8.44", 1, "x")) }
    @Test fun `runs of whitespace collapse into one underscore`() { assertEquals("My_App_1.0.apk", apkFileName("My    App", "1.0", 1, "x")) }
    @Test fun `path separators and reserved characters are sanitised`() {
        val n = apkFileName("""a/b\c:d*e?f"g<h>i|j""", "1.0", 1, "x")
        "/\\:*?\"<>|".forEach { assertFalse(n.contains(it)) }; assertTrue(n.endsWith("_1.0.apk"))
    }
    @Test fun `control characters are sanitised`() { val n = apkFileName("a\nb\tc", "1", 1, "x"); assertFalse(n.contains('\n')); assertFalse(n.contains('\t')) }
    @Test fun `version name is sanitised too`() { assertEquals("App_1.0_beta.apk", apkFileName("App", "1.0/beta", 1, "x")) }
    @Test fun `blank version name falls back to version code`() {
        assertEquals("App_12100.apk", apkFileName("App", null, 12100, "x")); assertEquals("App_12100.apk", apkFileName("App", " ", 12100, "x"))
    }
    @Test fun `label that sanitises to nothing falls back to the package name`() {
        assertEquals("com.example.x_1.0.apk", apkFileName("///", "1.0", 1, "com.example.x")); assertEquals("com.example.x_1.0.apk", apkFileName(" ", "1.0", 1, "com.example.x"))
    }
    @Test fun `leading and trailing dots and underscores are trimmed`() { assertEquals("App_1.0.apk", apkFileName("..App._", "1.0", 1, "x")) }
    @Test fun `emoji in the label survives`() { assertEquals("天气🌤_1.0.apk", apkFileName("天气🌤", "1.0", 1, "x")) }
    @Test fun `very long labels are truncated by codepoint never mid character`() { assertValid(apkFileName("中".repeat(200), "1.0", 1, "x")) }
    @Test fun `long labels with emoji truncate at the surrogate pair boundary`() { assertValid(apkFileName("🌤".repeat(100), "1.0", 1, "x")) }
    private fun assertValid(n: String) { assertTrue(n.toByteArray().size <= 120); assertEquals(n, String(n.toByteArray())); assertFalse(n.contains('�')) }
}
