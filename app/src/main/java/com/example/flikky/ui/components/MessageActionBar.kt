package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import com.example.flikky.ui.theme.LocalMotionScale
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
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
    val motionScale = LocalMotionScale.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.effects()),
        exit = fadeOut(Motion.effectsFast()) + scaleOut(Motion.spatialFast(), targetScale = 0.9f),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            actions.forEachIndexed { i, a ->
                var shown by remember(i, a.label) { mutableStateOf(false) }
                // 错峰入场：主元素先动、次元素逐项延后一步（受全局速度档统辖，关闭=0 → 同时出现）。
                LaunchedEffect(i) { delay((i * Motion.StaggerStepMillis * motionScale).toLong()); shown = true }
                AnimatedVisibility(
                    visible = shown,
                    enter = scaleIn(Motion.spatial()) + fadeIn(Motion.effects()),
                ) {
                    CircleActionButton(a.icon, a.label, a.danger, a.onClick)
                }
            }
        }
    }
}
