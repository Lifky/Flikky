package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MimeGuessTest {
    @Test fun `apk gets the android package mime`() { assertEquals(MimeGuess.APK_MIME, MimeGuess.fromName("Flikky_1.21.0.apk")) }
    @Test fun `apk extension match is case insensitive`() {
        assertEquals(MimeGuess.APK_MIME, MimeGuess.fromName("APP.APK")); assertEquals(MimeGuess.APK_MIME, MimeGuess.fromName("App.Apk"))
    }
    @Test fun `multi dot names use the last extension`() { assertEquals(MimeGuess.APK_MIME, MimeGuess.fromName("com.example.app.apk")) }
    @Test fun `platform answers are not overridden by the fallback table`() {
        assertEquals("image/jpeg", MimeGuess.fromName("a.jpg")); assertEquals("text/html", MimeGuess.fromName("a.html"))
    }
    @Test fun `unknown and extensionless names stay null`() {
        assertNull(MimeGuess.fromName("README")); assertNull(MimeGuess.fromName("apk")); assertNull(MimeGuess.fromName("archive.flikky-unknown")); assertNull(MimeGuess.fromName(""))
    }
    @Test fun `a bare dot suffix does not crash`() { assertNull(MimeGuess.fromName("weird.")); assertNull(MimeGuess.fromName(".")) }
}
