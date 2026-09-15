package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachSheetSourcesTest {
    @Test fun `the sheet offers three sources in the agreed order`() { val s=code("AttachBottomSheet.kt"); val p=listOf("R.string.attach_title","R.string.apps_title","R.string.files_quick_title").map(s::indexOf); assertTrue(p.all{it>=0}); assertEquals(p.sorted(),p) }
    @Test fun `the app picker is reached from the sheet not reimplemented in it`() { assertTrue(code("AttachBottomSheet.kt").contains("AppPickerContent(")) }
    @Test fun `split apps are warned about before sending`() { val p=code("AppPickerContent.kt"); assertTrue(p.contains("R.string.apps_split_warning_title")); assertTrue(p.contains("splitCount")) }
    @Test fun `the picker filters through the shared policy`() { assertTrue(code("AppPickerContent.kt").contains("AppListPolicy.shape(")) }
    @Test fun `icons are resolved per row never for the whole list at once`() { assertTrue(Regex("""remember\(\s*\w*\.?packageName\s*\)""").containsMatchIn(code("AppPickerContent.kt"))) }
    private fun code(name:String):String { val f=File("src/main/java/com/example/flikky/ui/serving/$name").takeIf{it.isFile}?:File("app/src/main/java/com/example/flikky/ui/serving/$name"); assertTrue(f.isFile); return f.readText().replace(Regex("""/\*[\s\S]*?\*/"""),"").replace(Regex("""//[^\r\n]*"""),"") }
}
