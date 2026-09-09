package com.example.flikky.server.dto

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingVisualDtoTest {
    @Test
    fun defaultsPreserveThePreSettingBrowserAppearance() {
        assertEquals(
            LeadingVisualDto(
                shape = "cookie9Sided",
                colorMode = "THEME",
                colors = emptyMap(),
            ),
            LeadingVisualDto(),
        )
    }

    @Test
    fun nestedTypeColorMapRoundTripsThroughWireJson() {
        val expected = LeadingVisualDto(
            shape = "flower",
            colorMode = "HARMONIZED",
            colors = mapOf(
                "image" to listOf("#102030", "#F0F1F2"),
                "archive" to listOf("#A0B0C0", "#010203"),
            ),
        )
        val encoded = WireJson.encodeToString(LeadingVisualDto.serializer(), expected)
        val decoded = WireJson.decodeFromString(LeadingVisualDto.serializer(), encoded)

        assertEquals(expected, decoded)
    }

    @Test
    fun colorsStayNestedAndExtensibleInsteadOfFlattenedIntoDtoFields() {
        val source = File("src/main/java/com/example/flikky/server/dto/Dtos.kt")
            .readText()
            .replace(Regex("""(?s)/\*.*?\*/"""), "")
            .replace(Regex("""(?m)//.*$"""), "")
            .replace(Regex("""(?m)^\s*import\s+.*$"""), "")
        val declaration = source
            .substringAfter("data class LeadingVisualDto(", missingDelimiterValue = "")
            .substringBefore("\n)")

        assertTrue(declaration.contains("val colors: Map<String, List<String>>"))
        listOf("imageContainer", "videoContainer", "audioContainer", "documentContainer")
            .forEach { assertFalse("Flattened field $it makes adding a type a DTO change", declaration.contains(it)) }
    }

    @Test
    fun peerInfoKeepsLeadingVisualBackwardCompatibleByDefault() {
        val peer = PeerInfoDto(
            deviceName = "Phone",
            phoneAvatarId = 0,
            backgroundMode = "DEFAULT",
            recallEnabled = true,
        )
        assertEquals(LeadingVisualDto(), peer.leadingVisual)
    }
}
