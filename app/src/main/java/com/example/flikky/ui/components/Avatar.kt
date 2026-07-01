package com.example.flikky.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.flikky.R

sealed interface AvatarContent {
    data class Icon(val name: String) : AvatarContent
    data class Char(val value: String) : AvatarContent
}

data class AvatarPreset(val key: String, val label: String)

object AvatarKey {
    const val DEFAULT_PHONE = "icon:smartphone"
    const val DEFAULT_PEER = "icon:desktop_windows"

    fun parse(raw: String?, fallback: String = DEFAULT_PHONE): AvatarContent {
        val key = raw?.trim().orEmpty()
        return when {
            key.startsWith("icon:") -> {
                val name = key.removePrefix("icon:").trim()
                if (name.isNotEmpty()) AvatarContent.Icon(name) else parse(fallback, DEFAULT_PHONE)
            }
            key.startsWith("char:") -> {
                val value = key.removePrefix("char:").trim()
                val first = value.firstOrNull()?.toString()
                if (first != null) AvatarContent.Char(first) else parse(fallback, DEFAULT_PHONE)
            }
            else -> parse(fallback.takeIf { it != key }, DEFAULT_PHONE)
        }
    }

    fun normalize(raw: String?, fallback: String = DEFAULT_PHONE): String = when (val content = parse(raw, fallback)) {
        is AvatarContent.Icon -> icon(content.name)
        is AvatarContent.Char -> char(content.value)
    }

    fun icon(name: String): String = "icon:${name.trim()}"

    fun char(text: String): String {
        val first = text.trim().firstOrNull()?.toString()
        return first?.let { "char:$it" } ?: DEFAULT_PHONE
    }

    fun fromLegacyIndex(index: Int): String = LEGACY_KEYS.getOrElse(index) { LEGACY_KEYS[0] }

    private val LEGACY_KEYS = listOf(
        icon("person"),
        icon("star"),
        icon("favorite"),
        icon("home"),
        icon("email"),
        icon("call"),
        icon("phone"),
        icon("shopping_cart"),
        icon("thumb_up"),
        icon("place"),
        icon("notifications"),
        icon("settings"),
    )
}

val PRESET_AVATARS: List<AvatarPreset> = listOf(
    AvatarPreset(AvatarKey.DEFAULT_PHONE, "Phone"),
    AvatarPreset(AvatarKey.DEFAULT_PEER, "Desktop"),
    AvatarPreset(AvatarKey.icon("person"), "Person"),
    AvatarPreset(AvatarKey.icon("star"), "Star"),
    AvatarPreset(AvatarKey.icon("face"), "Face"),
    AvatarPreset(AvatarKey.icon("palette"), "Palette"),
    AvatarPreset(AvatarKey.icon("image"), "Image"),
    AvatarPreset(AvatarKey.icon("settings"), "Settings"),
)

@Composable
fun Avatar(avatarId: Int, size: Dp, modifier: Modifier = Modifier) {
    Avatar(avatarKey = AvatarKey.fromLegacyIndex(avatarId), size = size, modifier = modifier)
}

@Composable
fun Avatar(avatarKey: String?, size: Dp, modifier: Modifier = Modifier) {
    val content = AvatarKey.parse(avatarKey, AvatarKey.DEFAULT_PHONE)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            is AvatarContent.Icon -> Icon(
                painter = painterResource(iconDrawable(content.name)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.6f),
            )
            is AvatarContent.Char -> Text(
                text = content.value,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.46f).sp,
                lineHeight = (size.value * 0.46f).sp,
            )
        }
    }
}

@DrawableRes
private fun iconDrawable(name: String): Int = when (name) {
    "smartphone" -> R.drawable.ic_smartphone
    "desktop_windows" -> R.drawable.ic_desktop_windows
    "person" -> R.drawable.ic_account_circle
    "settings" -> R.drawable.ic_settings
    "star" -> R.drawable.ic_star
    "face" -> R.drawable.ic_face
    "palette" -> R.drawable.ic_palette
    "image" -> R.drawable.ic_image
    "description" -> R.drawable.ic_description
    else -> R.drawable.ic_account_circle
}
