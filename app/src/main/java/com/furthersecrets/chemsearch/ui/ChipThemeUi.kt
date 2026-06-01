package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun chemFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.24f),
    labelColor = MaterialTheme.colorScheme.onSurface.copy(0.76f),
    iconColor = MaterialTheme.colorScheme.primary,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(0.16f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
fun chemAssistChipColors() = AssistChipDefaults.assistChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.18f),
    labelColor = MaterialTheme.colorScheme.onSurface.copy(0.82f),
    leadingIconContentColor = MaterialTheme.colorScheme.primary,
    trailingIconContentColor = MaterialTheme.colorScheme.primary
)

@Composable
fun chemAssistChipBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.26f))
