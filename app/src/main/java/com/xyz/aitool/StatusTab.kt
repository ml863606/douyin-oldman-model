package com.xyz.aitool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xyz.aitool.data.AlertAction

@Composable
fun StatusTab(
    accessibilityEnabled: Boolean,
    captureRunning: Boolean,
    rulesEnabled: Boolean,
    alertAction: AlertAction,
    selectedAppCount: Int,
    hitCount: Int,
    logCount: Int,
    onOpenAccessibility: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderPanel(
                accessibilityEnabled = accessibilityEnabled,
                captureRunning = captureRunning,
                rulesEnabled = rulesEnabled,
                alertAction = alertAction,
                selectedAppCount = selectedAppCount,
                hitCount = hitCount,
                logCount = logCount,
            )
        }
        item {
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                captureRunning = captureRunning,
                onOpenAccessibility = onOpenAccessibility,
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture,
                onOpenOnboarding = onOpenOnboarding,
            )
        }
    }
}
