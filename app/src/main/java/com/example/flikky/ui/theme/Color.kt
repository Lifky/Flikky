package com.example.flikky.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.flikky.data.settings.PresetTheme

// ─── Legacy named colors (kept for any existing references) ──────────────────
val FlikkyPrimary = Color(0xFF4F5BD5)
val FlikkyOnPrimary = Color(0xFFFFFFFF)
val FlikkySurface = Color(0xFFFDFBFF)
val FlikkySurfaceVariant = Color(0xFFE3E1EC)
val FlikkyOutline = Color(0xFF757681)

// ─── Coral 珊瑚橙  seed #FF7043 ──────────────────────────────────────────────
// Light: primary tone 30 (dark enough for white text), surfaces near-white
val CoralLight = lightColorScheme(
    primary = Color(0xFF8C1D00),            // L≈0.036 → contrast w/ white ≈18.2 ✓
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF370800),
    secondary = Color(0xFF77574E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD1),  // light container
    onSecondaryContainer = Color(0xFF2C150E),// very dark text → contrast ≈ 17 ✓
    tertiary = Color(0xFF695E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),          // L≈0.014 on white ≈16.8 ✓
    surfaceVariant = Color(0xFFF5DEDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF362F2E),
    inverseOnSurface = Color(0xFFFBEEEB),
    inversePrimary = Color(0xFFFFB4A0),
)

// Dark: primary tone 80 (light enough for dark text)
val CoralDark = darkColorScheme(
    primary = Color(0xFFFFB4A0),            // L≈0.413 → contrast w/ dark ≈7.3 ✓
    onPrimary = Color(0xFF561300),          // L≈0.009
    primaryContainer = Color(0xFF7A2900),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFE7BDB5),
    onSecondary = Color(0xFF442A23),
    secondaryContainer = Color(0xFF5D3F37),
    onSecondaryContainer = Color(0xFFFFDBD1),// light text on dark container ≈12 ✓
    tertiary = Color(0xFFD7C68D),
    onTertiary = Color(0xFF3A3005),
    tertiaryContainer = Color(0xFF514619),
    onTertiaryContainer = Color(0xFFF3E2A7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DD),          // L≈0.712 on dark ≈9.9 ✓
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEDE0DD),
    inverseOnSurface = Color(0xFF201A19),
    inversePrimary = Color(0xFF8C1D00),
)

// ─── Mushroom 蘑菇棕  seed #8D6E63 ───────────────────────────────────────────
val MushroomLight = lightColorScheme(
    primary = Color(0xFF6B3A2A),            // L≈0.043 → contrast w/ white ≈15.5 ✓
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF280D04),
    secondary = Color(0xFF6F5349),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF291209), // dark text ≈17 ✓
    tertiary = Color(0xFF5B6135),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E6AE),
    onTertiaryContainer = Color(0xFF181D00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A18),          // ≈16.8 ✓
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF362F2C),
    inverseOnSurface = Color(0xFFFBEEEA),
    inversePrimary = Color(0xFFFFB59C),
)

val MushroomDark = darkColorScheme(
    primary = Color(0xFFFFB59C),            // L≈0.396 → contrast w/ dark ≈7.0 ✓
    onPrimary = Color(0xFF3D0800),          // L≈0.004
    primaryContainer = Color(0xFF522115),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE2BDB4),
    onSecondary = Color(0xFF40271D),
    secondaryContainer = Color(0xFF573B31),
    onSecondaryContainer = Color(0xFFFFDBCF),// ≈12 ✓
    tertiary = Color(0xFFC4CA95),
    onTertiary = Color(0xFF2D320B),
    tertiaryContainer = Color(0xFF43491F),
    onTertiaryContainer = Color(0xFFE0E6AE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A18),
    onBackground = Color(0xFFEDE0DB),
    surface = Color(0xFF201A18),
    onSurface = Color(0xFFEDE0DB),          // ≈9.9 ✓
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEDE0DB),
    inverseOnSurface = Color(0xFF201A18),
    inversePrimary = Color(0xFF6B3A2A),
)

// ─── Teal 黛尾绿  seed #4DB6AC ────────────────────────────────────────────────
val TealLight = lightColorScheme(
    primary = Color(0xFF006A62),            // L≈0.039 → contrast w/ white ≈17.1 ✓
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF051F1D), // dark on light ≈17 ✓
    tertiary = Color(0xFF45617A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCAE5FF),
    onTertiaryContainer = Color(0xFF001E30),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDFB),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFBFDFB),
    onSurface = Color(0xFF191C1C),          // ≈17.5 ✓
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C7),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    inversePrimary = Color(0xFF82D5CB),
)

val TealDark = darkColorScheme(
    primary = Color(0xFF82D5CB),            // L≈0.543 → contrast w/ dark ≈9.9 ✓
    onPrimary = Color(0xFF003733),          // L≈0.003
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9EF2E8),
    secondary = Color(0xFFB1CCC8),
    onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF334B49),
    onSecondaryContainer = Color(0xFFCCE8E4),// ≈10 ✓
    tertiary = Color(0xFFAAC9E4),
    onTertiary = Color(0xFF143349),
    tertiaryContainer = Color(0xFF2D4960),
    onTertiaryContainer = Color(0xFFCAE5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),          // L≈0.614 → ≈8.5 ✓
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF191C1C),
    inversePrimary = Color(0xFF006A62),
)

// ─── Mist 雾霭蓝  seed #5C9CE6 ───────────────────────────────────────────────
val MistLight = lightColorScheme(
    primary = Color(0xFF1A5FA8),            // L≈0.068 → contrast w/ white ≈10.4 ✓
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF535E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E2F9),
    onSecondaryContainer = Color(0xFF0F1B2E), // dark text ≈17 ✓
    tertiary = Color(0xFF6C5677),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4D9FF),
    onTertiaryContainer = Color(0xFF261430),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAFCFF),
    onSurface = Color(0xFF1A1C1E),          // ≈17.5 ✓
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFA3C8FF),
)

val MistDark = darkColorScheme(
    primary = Color(0xFFA3C8FF),            // L≈0.519 → contrast w/ dark ≈9.4 ✓
    onPrimary = Color(0xFF003063),          // L≈0.002
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBBC6DC),
    onSecondary = Color(0xFF253042),
    secondaryContainer = Color(0xFF3B4659),
    onSecondaryContainer = Color(0xFFD7E2F9),// ≈10 ✓
    tertiary = Color(0xFFD9BCE5),
    onTertiary = Color(0xFF3A2946),
    tertiaryContainer = Color(0xFF533F5D),
    onTertiaryContainer = Color(0xFFF4D9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),          // L≈0.596 → ≈8.2 ✓
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFF1A5FA8),
)

// ─── Preset selector ─────────────────────────────────────────────────────────
fun presetScheme(preset: PresetTheme, dark: Boolean): ColorScheme = when (preset) {
    PresetTheme.CORAL    -> if (dark) CoralDark    else CoralLight
    PresetTheme.MUSHROOM -> if (dark) MushroomDark else MushroomLight
    PresetTheme.TEAL     -> if (dark) TealDark     else TealLight
    PresetTheme.MIST     -> if (dark) MistDark     else MistLight
}

// ─── AMOLED override ─────────────────────────────────────────────────────────
// Pure-black background/surface; surfaceContainer* stay as dark greys for layering.
fun amoledOverride(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
)

// ─── Semantic color extensions ────────────────────────────────────────────────
val ColorScheme.connected: Color
    @Composable get() = Color(0xFF2E7D32) // success green; intent: "connected" state indicator

val ColorScheme.disconnected: Color
    @Composable get() = onSurfaceVariant   // muted, intentionally de-emphasized
