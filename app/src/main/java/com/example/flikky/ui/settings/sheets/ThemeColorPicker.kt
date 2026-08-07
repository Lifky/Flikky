package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.ThemeHsv
import com.example.flikky.util.themeHsvToSeed
import kotlin.math.roundToInt

private val HueColors = listOf(0f, 60f, 120f, 180f, 240f, 300f, 0f).map { hue ->
    Color(themeHsvToSeed(ThemeHsv(hue, 1f, 1f)).toInt())
}

@Composable
internal fun ThemeColorPicker(
    hsv: ThemeHsv,
    onHsvChange: (ThemeHsv) -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = stringResource(
        R.string.theme_custom_details,
        hsv.hue.roundToInt(),
        (hsv.saturation * 100f).roundToInt(),
        (hsv.value * 100f).roundToInt(),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SaturationValuePalette(
            hsv = hsv,
            onHsvChange = onHsvChange,
            contentDescription = stringResource(R.string.theme_custom_palette),
            stateDescription = details,
        )
        HueSlider(
            hsv = hsv,
            onHsvChange = onHsvChange,
            contentDescription = stringResource(R.string.theme_custom_hue),
            stateDescription = details,
        )
        Text(
            text = details,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaturationValuePalette(
    hsv: ThemeHsv,
    onHsvChange: (ThemeHsv) -> Unit,
    contentDescription: String,
    stateDescription: String,
) {
    var paletteSize by remember { mutableStateOf(IntSize.Zero) }
    val hueColor = remember(hsv.hue) {
        Color(themeHsvToSeed(ThemeHsv(hsv.hue, 1f, 1f)).toInt())
    }

    fun updateFrom(position: Offset) {
        if (paletteSize.width == 0 || paletteSize.height == 0) return
        onHsvChange(
            hsv.copy(
                saturation = (position.x / paletteSize.width).coerceIn(0f, 1f),
                value = (1f - position.y / paletteSize.height).coerceIn(0f, 1f),
            ),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .onSizeChanged { paletteSize = it }
            .pointerInput(paletteSize, hsv.hue) {
                detectDragGestures(
                    onDragStart = ::updateFrom,
                    onDrag = { change, _ ->
                        change.consume()
                        updateFrom(change.position)
                    },
                )
            }
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
        )
        val cursor = Offset(
            x = hsv.saturation.coerceIn(0f, 1f) * size.width,
            y = (1f - hsv.value.coerceIn(0f, 1f)) * size.height,
        )
        drawCircle(Color.Black.copy(alpha = 0.7f), 10.dp.toPx(), cursor, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 8.dp.toPx(), cursor, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun HueSlider(
    hsv: ThemeHsv,
    onHsvChange: (ThemeHsv) -> Unit,
    contentDescription: String,
    stateDescription: String,
) {
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateFrom(position: Offset) {
        if (sliderSize.width == 0) return
        val hue = (position.x / sliderSize.width).coerceIn(0f, 1f) * 359.999f
        onHsvChange(hsv.copy(hue = hue))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { sliderSize = it }
            .pointerInput(sliderSize, hsv.saturation, hsv.value) {
                detectDragGestures(
                    onDragStart = ::updateFrom,
                    onDrag = { change, _ ->
                        change.consume()
                        updateFrom(change.position)
                    },
                )
            }
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(HueColors),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
        )
        val cursor = Offset(
            x = (hsv.hue.coerceIn(0f, 359.999f) / 359.999f) * size.width,
            y = size.height / 2f,
        )
        drawCircle(Color.Black.copy(alpha = 0.7f), 10.dp.toPx(), cursor, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 8.dp.toPx(), cursor, style = Stroke(3.dp.toPx()))
    }
}
