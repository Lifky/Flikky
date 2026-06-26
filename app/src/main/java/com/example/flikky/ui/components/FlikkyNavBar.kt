package com.example.flikky.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.flikky.R

@Composable
fun FlikkyNavBar(
    currentRoute: String?,
    settingsEnabled: Boolean = true,
    favoritesEnabled: Boolean = false,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        val transferSelected = currentRoute == "transfer"
        NavigationBarItem(
            selected = transferSelected,
            onClick = { onNavigate("transfer") },
            icon = {
                Icon(
                    painterResource(if (transferSelected) R.drawable.ic_send else R.drawable.ic_send_outline),
                    contentDescription = "传输",
                )
            },
            label = { Text("传输") },
            alwaysShowLabel = false,
        )

        if (favoritesEnabled) {
            val favoritesSelected = currentRoute == "favorites"
            NavigationBarItem(
                selected = favoritesSelected,
                onClick = { onNavigate("favorites") },
                icon = {
                    Icon(
                        painterResource(if (favoritesSelected) R.drawable.ic_star else R.drawable.ic_star_border),
                        contentDescription = "收藏",
                    )
                },
                label = { Text("收藏") },
                alwaysShowLabel = false,
            )
        }

        val settingsSelected = currentRoute == "settings"
        NavigationBarItem(
            selected = settingsSelected,
            enabled = settingsEnabled,
            onClick = { onNavigate("settings") },
            icon = {
                Icon(
                    painterResource(if (settingsSelected) R.drawable.ic_settings else R.drawable.ic_settings_outline),
                    contentDescription = "设置",
                )
            },
            label = { Text("设置") },
            alwaysShowLabel = false,
        )
    }
}
