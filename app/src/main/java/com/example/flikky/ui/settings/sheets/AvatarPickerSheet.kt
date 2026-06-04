package com.example.flikky.ui.settings.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.PRESET_AVATARS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    currentId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "选择头像",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            items(PRESET_AVATARS.size) { id ->
                val isSelected = id == currentId
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(64.dp)
                        .then(
                            if (isSelected)
                                Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape,
                                )
                            else Modifier
                        )
                        .clip(CircleShape)
                        .clickable { onSelect(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(avatarId = id, size = 56.dp)
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
