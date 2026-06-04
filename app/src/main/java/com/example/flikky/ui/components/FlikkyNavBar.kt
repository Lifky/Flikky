package com.example.flikky.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.example.flikky.R

@Composable
fun FlikkyNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "transfer",
            onClick = { onNavigate("transfer") },
            icon = { Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = "传输") },
            label = { Text("传输") },
            alwaysShowLabel = false,
        )
        NavigationBarItem(
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
            label = { Text("设置") },
            alwaysShowLabel = false,
        )
    }
}
