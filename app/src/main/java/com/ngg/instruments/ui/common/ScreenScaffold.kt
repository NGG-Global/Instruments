package com.ngg.instruments.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngg.instruments.ui.theme.Palette

/**
 * Shared chrome for the non-panel screens (settings, diagnostics, onboarding)
 * so they read as one product: Material top bar with back navigation, content
 * that scrolls under the system bars while clearing them via insets, and the
 * card/header/status patterns used by all three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to instrument panel",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner: PaddingValues ->
        // Padding applied inside the scroll modifier: content scrolls under
        // the bars but its resting position clears them.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

/** Card grouping used across settings, diagnostics and onboarding. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    statusColor: Color? = null,
    statusLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.weight(1f))
                if (statusColor != null && statusLabel != null) {
                    StatusBadge(statusColor, statusLabel)
                }
            }
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/**
 * Status shown with the same visual language as the panel's quality dots —
 * always paired with a text label so state is never colour-only.
 */
@Composable
fun StatusBadge(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Persistent safety statement footer. Deliberately not collapsible.
 */
@Composable
fun SafetyFooter(modifier: Modifier = Modifier) {
    val amber = Palette.qualityColor(com.ngg.instruments.flight.DataQuality.DEGRADED)
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        shape = RoundedCornerShape(8.dp),
        color = amber.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, amber.copy(alpha = 0.4f)),
    ) {
        Text(
            "For supplemental / experimental use only. Not a certified flight instrument and not a substitute for approved aircraft instrumentation.",
            style = MaterialTheme.typography.bodySmall,
            color = amber,
            modifier = Modifier.padding(12.dp),
        )
    }
}
