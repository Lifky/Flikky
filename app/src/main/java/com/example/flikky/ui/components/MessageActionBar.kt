package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class MessageAction(
    val icon: Painter,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun MessageActionBar(
    visible: Boolean,
    actions: List<MessageAction>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, a ->
                var shown by remember(i, a.label) { mutableStateOf(false) }
                LaunchedEffect(i) { delay(i * 40L); shown = true }
                AnimatedVisibility(
                    visible = shown,
                    enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + fadeIn(),
                ) {
                    CircleActionButton(a.icon, a.label, a.danger, a.onClick)
                }
            }
        }
    }
}
