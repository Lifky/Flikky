package com.example.flikky.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.QrMatrix
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeSheet(url: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val matrix = remember(url) { QrMatrix.encode(url) }
    // Keep dark modules on a light surface for cameras, including AMOLED themes.
    val surface = MaterialTheme.colorScheme.surface
    val codeBackground = if (surface.luminance() >= 0.8f) surface else Color.White
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge).padding(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.connection_qr_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.connection_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (matrix != null) {
                Canvas(
                    Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
                        .testTag("qr-code")
                        .background(codeBackground, RoundedCornerShape(28.dp))
                        // Place the complete four-module quiet zone inside the
                        // rounded card; corners never cut into that clear square.
                        .padding(Spacing.md),
                ) {
                    val modules = matrix.size + QrMatrix.QUIET_ZONE * 2
                    val cell = floor(minOf(size.width, size.height) / modules)
                    val drawn = cell * modules
                    // Integer origins matter as much as integer cell sizes:
                    // half pixels introduce antialiased seams between modules.
                    val originX = floor((size.width - drawn) / 2f)
                    val originY = floor((size.height - drawn) / 2f)
                    for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                        if (matrix.isDark(x, y)) drawRect(
                            Color.Black,
                            Offset(originX + (x + QrMatrix.QUIET_ZONE) * cell, originY + (y + QrMatrix.QUIET_ZONE) * cell),
                            Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}
