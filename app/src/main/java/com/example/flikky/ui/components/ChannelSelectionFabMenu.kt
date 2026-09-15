package com.example.flikky.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing

internal val ChannelSelectionFabSize =
    ToggleFloatingActionButtonDefaults.containerSizeMedium()(0f)

/** Shared fixed-center geometry for a channel lock and its selection actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelSelectionFabMenu(
    visible: Boolean,
    channelLock: @Composable (Modifier) -> Unit,
    menuItems: @Composable FloatingActionButtonMenuScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    if (!visible && expanded) expanded = false

    val lockShift by animateDpAsState(
        targetValue = if (visible) {
            ChannelSelectionFabSize / 2 + ChannelLockFabSize / 2 + Spacing.md
        } else {
            0.dp
        },
        animationSpec = Motion.spatial(),
        label = "ChannelLockHorizontalShift",
    )
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            Box(Modifier.size(ChannelSelectionFabSize), contentAlignment = Alignment.Center) {
                channelLock(Modifier.offset(x = -lockShift).size(ChannelLockFabSize))
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(Motion.spatial()) + fadeIn(Motion.effects()),
                    exit = scaleOut(Motion.spatialFastNoBounce()) + fadeOut(Motion.effectsFast()),
                ) {
                    ToggleFloatingActionButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it },
                        contentAlignment = Alignment.Center,
                        containerSize = ToggleFloatingActionButtonDefaults.containerSizeMedium(),
                        containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadiusMedium(),
                    ) {
                        val closing = checkedProgress > 0.5f
                        Icon(
                            painter = painterResource(
                                if (closing) R.drawable.ic_close else R.drawable.ic_arrow_upward,
                            ),
                            contentDescription = stringResource(
                                if (closing) R.string.serving_storage_actions_close
                                else R.string.serving_storage_actions,
                            ),
                            modifier = Modifier.animateIcon(
                                checkedProgress = { checkedProgress },
                                size = ToggleFloatingActionButtonDefaults.iconSizeMedium(),
                            ),
                        )
                    }
                }
            }
        },
    ) {
        menuItems()
    }
}
