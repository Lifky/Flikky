package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Spacing

private val EmptyStateTextMaxWidth = 360.dp

@Composable
fun EmptyStateContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    illustration: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier.padding(Spacing.screenEdge),
        contentAlignment = Alignment.Center,
    ) {
        val viewportHeight = maxHeight
        // Anchor the title above the viewport midpoint and supporting copy below it.
        // Different copy lengths must not move the illustration/title between tabs.
        // A short viewport or large font can scroll instead of clipping either half.
        Layout(
            modifier = Modifier
                .widthIn(max = EmptyStateTextMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (illustration != null) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = Spacing.xxl)
                                .size(160.dp)
                                .testTag("empty-state-illustration"),
                            contentAlignment = Alignment.Center,
                        ) { illustration() }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.padding(top = Spacing.lg)) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                        )
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minHeight = 0)
            val heading = measurables[0].measure(childConstraints)
            val supporting = measurables[1].measure(childConstraints)
            val halfHeight = maxOf(viewportHeight.roundToPx() / 2, heading.height, supporting.height)
            layout(constraints.maxWidth, halfHeight * 2) {
                heading.placeRelative(0, halfHeight - heading.height)
                supporting.placeRelative(0, halfHeight)
            }
        }
    }
}
