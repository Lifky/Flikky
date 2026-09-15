package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendInstalledAppTest {
    @Test fun `apk sending reuses the shared stored file entry point`() { val b=block(); assertTrue(b.contains("offerStoredFile(")); assertFalse(b.contains("copyTo(")||b.contains("outputStream()")) }
    @Test fun `the display name comes from the shared naming rule`() { assertTrue(block().contains("apkFileName(")) }
    @Test fun `the mime comes from the shared constant`() { assertTrue(block().contains("MimeGuess.APK_MIME")) }
    @Test fun `the attach sheet receives real app data not placeholders`() { val b=code("ServingChatTab.kt").substringAfter("AttachBottomSheet(").take(900); assertFalse(b.contains("installedApps = emptyList()")); assertTrue(b.contains("installedApps =")); assertTrue(b.contains("sendInstalledApp")) }
    private fun block()=code("ServingViewModel.kt").substringAfter("fun sendInstalledApp").take(1200)
    private fun code(n:String):String { val f=File("src/main/java/com/example/flikky/ui/serving/$n").takeIf{it.isFile}?:File("app/src/main/java/com/example/flikky/ui/serving/$n"); assertTrue(f.isFile); return f.readText().replace(Regex("""/\*[\s\S]*?\*/"""),"").replace(Regex("""//[^\r\n]*"""),"") }
}
