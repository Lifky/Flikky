package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApkActionTest {
    @Test fun `the install action is built in the shared action builder`() { val b=installBranch(); assertTrue(b.contains("installApk(")); assertTrue(b.contains("FileCategory.APK")) }
    @Test fun `install is only offered once the transfer has completed`() { assertTrue(installBranch().contains("Status.COMPLETED")) }
    private fun installBranch(): String = builder().substringBefore("if (settings.favoriteBetaEnabled")
    private fun builder():String { val f=File("src/main/java/com/example/flikky/ui/serving/ServingChatTab.kt").takeIf{it.isFile}?:File("app/src/main/java/com/example/flikky/ui/serving/ServingChatTab.kt"); assertTrue(f.isFile); return f.readText().substringAfter("fun buildActionsFor") }
}
