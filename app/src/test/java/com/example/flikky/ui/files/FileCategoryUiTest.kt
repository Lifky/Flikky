package com.example.flikky.ui.files

import com.example.flikky.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCategoryUiTest {
    @Test
    fun audioCategoryUsesAudioFileSymbol() {
        val resourceName = R.drawable::class.java.fields
            .single { it.getInt(null) == FileCategory.AUDIO.iconResource() }
            .name

        assertEquals("ic_audio_file", resourceName)
    }
}
