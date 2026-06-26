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
        NavigationBarItem(
            selected = currentRoute == "transfer",
            onClick = { onNavigate("transfer") },
            icon = { Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = "传输") },
            label = { Text("传输") },
            alwaysShowLabel = false,
        )
        if (favoritesEnabled) {
            NavigationBarItem(
                selected = currentRoute == "favorites",
                onClick = { onNavigate("favorites") },
                icon = { Icon(painterResource(R.drawable.ic_star), contentDescription = "收藏") },
                label = { Text("收藏") },
                alwaysShowLabel = false,
            )
        }
        NavigationBarItem(
            selected = currentRoute == "settings",
            enabled = settingsEnabled,
            onClick = { onNavigate("settings") },
            icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = "设置") },
            label = { Text("设置") },
            alwaysShowLabel = false,
        )
    }
}
