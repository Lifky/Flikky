package com.example.flikky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

data class AvatarSpec(val icon: ImageVector, val bg: Color)

/**
 * 12 preset avatars. All icons from material-icons-core (filled style).
 * Warm-leaning background palette — uses deep/saturated colors for contrast with white icons.
 */
val PRESET_AVATARS: List<AvatarSpec> = listOf(
    AvatarSpec(Icons.Default.Person,       Color(0xFFFF7043)), // deep orange
    AvatarSpec(Icons.Default.Star,         Color(0xFFF4B400)), // amber
    AvatarSpec(Icons.Default.Favorite,     Color(0xFFE91E63)), // pink
    AvatarSpec(Icons.Default.Home,         Color(0xFF43A047)), // green
    AvatarSpec(Icons.Default.Email,        Color(0xFF1E88E5)), // blue
    AvatarSpec(Icons.Default.Call,         Color(0xFF00ACC1)), // cyan
    AvatarSpec(Icons.Default.Phone,        Color(0xFF7E57C2)), // purple
    AvatarSpec(Icons.Default.ShoppingCart, Color(0xFFEF6C00)), // orange
    AvatarSpec(Icons.Default.ThumbUp,      Color(0xFF039BE5)), // light blue
    AvatarSpec(Icons.Default.Place,        Color(0xFFD81B60)), // raspberry
    AvatarSpec(Icons.Default.Notifications,Color(0xFF6D4C41)), // brown
    AvatarSpec(Icons.Default.Settings,     Color(0xFF546E7A)), // blue-grey
)

@Composable
fun Avatar(avatarId: Int, size: Dp, modifier: Modifier = Modifier) {
    val spec = PRESET_AVATARS.getOrElse(avatarId) { PRESET_AVATARS[0] }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(spec.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
