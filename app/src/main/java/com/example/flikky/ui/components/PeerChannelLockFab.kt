package com.example.flikky.ui.components

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flikky.R

/** Single source of truth for the lock FAB size used by peer-visible channels. */
internal val ChannelLockFabSize: Dp = 56.dp

/** Toggles one peer-visible channel without changing the app's system permission. */
@Composable
fun PeerChannelLockFab(
    peerEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    descriptionOn: String,
    descriptionOff: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { onToggle(!peerEnabled) },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(
                if (peerEnabled) R.drawable.ic_lock_open_right else R.drawable.ic_lock,
            ),
            contentDescription = if (peerEnabled) descriptionOn else descriptionOff,
        )
    }
}
