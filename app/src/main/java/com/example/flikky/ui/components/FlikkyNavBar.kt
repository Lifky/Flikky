package com.example.flikky.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
                    contentDescription = stringResource(R.string.nav_transfer),
                )
            },
            label = { Text(stringResource(R.string.nav_transfer)) },
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
                        contentDescription = stringResource(R.string.nav_favorites),
                    )
                },
                label = { Text(stringResource(R.string.nav_favorites)) },
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
                    contentDescription = stringResource(R.string.nav_settings),
                )
            },
            label = { Text(stringResource(R.string.nav_settings)) },
            alwaysShowLabel = false,
        )
    }
}
