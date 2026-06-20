package com.example.flikky.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Wide-screen content cap. Use with a parent Box that centers the content. */
const val MAX_CONTENT_WIDTH_DP = 600

fun Modifier.maxContentWidth(): Modifier = this.widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
