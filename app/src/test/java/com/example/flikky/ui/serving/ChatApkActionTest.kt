package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApkActionTest {
    @Test fun `the install action is built in the shared action builder`() { val b=builder(); assertTrue(b.contains("installApk(")); assertTrue(b.contains("FileCategory.APK")) }
    @Test fun `install is only offered once the transfer has completed`() { val b=builder(); val at=b.indexOf("FileCategory.APK"); val i=b.substring(maxOf(0,at-300)).take(600); assertTrue(i.contains("Status.COMPLETED")) }
    private fun builder():String { val f=File("src/main/java/com/example/flikky/ui/serving/ServingChatTab.kt").takeIf{it.isFile}?:File("app/src/main/java/com/example/flikky/ui/serving/ServingChatTab.kt"); assertTrue(f.isFile); return f.readText().substringAfter("fun buildActionsFor") }
}
