package com.example.flikky.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class AvatarKeyTest {
    @Test fun parse_icon() {
        assertEquals(
            AvatarContent.Icon("smartphone"),
            AvatarKey.parse("icon:smartphone", AvatarKey.DEFAULT_PHONE),
        )
    }

    @Test fun parse_icon_keeps_material_icon_name() {
        assertEquals(
            AvatarContent.Icon("desktop_windows"),
            AvatarKey.parse("icon:desktop_windows", AvatarKey.DEFAULT_PEER),
        )
    }

    @Test fun parse_icon_fill_state() {
        assertEquals(
            AvatarContent.Icon("settings", filled = false),
            AvatarKey.parse("icon:settings:outline", AvatarKey.DEFAULT_PHONE),
        )
        assertEquals(
            AvatarContent.Icon("settings", filled = true),
            AvatarKey.parse("icon:settings:filled", AvatarKey.DEFAULT_PHONE),
        )
    }

    @Test fun parse_char_takes_first_character() {
        assertEquals(
            AvatarContent.Char("A"),
            AvatarKey.parse("char:Ab", AvatarKey.DEFAULT_PHONE),
        )
    }

    @Test fun parse_null_falls_back() {
        assertEquals(
            AvatarContent.Icon("desktop_windows"),
            AvatarKey.parse(null, AvatarKey.DEFAULT_PEER),
        )
    }

    @Test fun parse_garbage_falls_back() {
        assertEquals(
            AvatarContent.Icon("smartphone"),
            AvatarKey.parse("nonsense", AvatarKey.DEFAULT_PHONE),
        )
    }

    @Test fun parse_empty_char_falls_back() {
        assertEquals(
            AvatarContent.Icon("smartphone"),
            AvatarKey.parse("char:", AvatarKey.DEFAULT_PHONE),
        )
    }

    @Test fun legacy_index_maps() {
        assertEquals("icon:person", AvatarKey.fromLegacyIndex(0))
        assertEquals("icon:settings", AvatarKey.fromLegacyIndex(11))
        assertEquals("icon:person", AvatarKey.fromLegacyIndex(99))
    }

    @Test fun char_helper_takes_first_character() {
        assertEquals("char:A", AvatarKey.char("Abc"))
    }

    @Test fun char_helper_falls_back_for_blank() {
        assertEquals(AvatarKey.DEFAULT_PHONE, AvatarKey.char(" "))
    }

    @Test fun icon_helper_encodes_fill_state() {
        assertEquals("icon:settings:outline", AvatarKey.icon("settings", filled = false))
        assertEquals("icon:settings:filled", AvatarKey.icon("settings", filled = true))
    }

    @Test fun every_preset_icon_uses_supported_material_symbol_name() {
        val iconNames = PRESET_AVATARS
            .map { AvatarKey.parse(it.key, AvatarKey.DEFAULT_PHONE) }
            .filterIsInstance<AvatarContent.Icon>()
            .map { it.name }
            .distinct()

        iconNames.forEach { name ->
            org.junit.Assert.assertTrue(
                "$name should be rendered by the bundled Material Symbols font",
                AvatarKey.isSupportedIcon(name),
            )
        }
    }

    @Test fun bundled_material_symbols_font_exposes_official_variable_axes() {
        val axes = readFontVariationAxes(materialSymbolsFontFile())

        val officialAxes = setOf("FILL", "wght", "GRAD", "opsz")
        assertTrue(
            "Material Symbols font must expose official variable axes; actual axes=$axes",
            axes.containsAll(officialAxes),
        )
    }

    private fun materialSymbolsFontFile(): File {
        val candidates = listOf(
            File("src/main/res/font/material_symbols_outlined.ttf"),
            File("app/src/main/res/font/material_symbols_outlined.ttf"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("material_symbols_outlined.ttf not found from ${File(".").absolutePath}")
    }

    private fun readFontVariationAxes(file: File): Set<String> {
        val bytes = file.readBytes()
        fun u16(offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or
                (bytes[offset + 1].toInt() and 0xff)

        fun u32(offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)

        val tableCount = u16(4)
        val fvarOffset = (0 until tableCount)
            .firstNotNullOfOrNull { index ->
                val offset = 12 + index * 16
                val tag = String(bytes, offset, 4, StandardCharsets.US_ASCII)
                u32(offset + 8).takeIf { tag == "fvar" }
            }
            ?: return emptySet()

        val axesArrayOffset = u16(fvarOffset + 4)
        val axisCount = u16(fvarOffset + 8)
        val axisSize = u16(fvarOffset + 10)
        return (0 until axisCount)
            .map { index ->
                val offset = fvarOffset + axesArrayOffset + index * axisSize
                String(bytes, offset, 4, StandardCharsets.US_ASCII)
            }
            .toSet()
    }
}
