package com.example.flikky.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
