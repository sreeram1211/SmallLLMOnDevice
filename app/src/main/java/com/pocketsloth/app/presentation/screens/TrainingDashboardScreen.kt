package com.pocketsloth.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pocketsloth.app.presentation.components.LossCurveCanvas
import com.pocketsloth.app.presentation.viewmodel.ThermalState
import com.pocketsloth.app.presentation.viewmodel.TrainingDashboardViewModel
import com.pocketsloth.app.presentation.viewmodel.UiTrainingMetrics
import com.pocketsloth.app.ui.theme.BatteryGoodColor
import com.pocketsloth.app.ui.theme.ThermalSevereColor
import com.pocketsloth.app.ui.theme.ThermalWarnColor
import java.util.Locale

@Composable
fun TrainingDashboardScreen(modifier: Modifier = Modifier) {
    TrainingDashboardScreen(
        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDashboardScreen(
    viewModel: TrainingDashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val metrics by viewModel.metrics.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Training dashboard")
                        Text(
                            text = metrics.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (metrics.thermalState == ThermalState.Critical ||
                metrics.thermalState == ThermalState.Serious
            ) {
                SevereThermalBanner(state = metrics.thermalState)
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Loss curve", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (metrics.lossHistory.isEmpty()) {
                                "—"
                            } else {
                                String.format(Locale.US, "%.4f", metrics.loss)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LossCurveCanvas(lossPoints = metrics.lossHistory)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Step ${metrics.step} · Epoch ${metrics.epoch}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    title = "RAM",
                    value = formatRam(metrics),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    title = "Thermal",
                    value = metrics.thermalState.name,
                    accent = thermalColor(metrics.thermalState),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    title = "Battery",
                    value = "${metrics.batteryPercent}%",
                    accent = BatteryGoodColor,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    title = "ETA",
                    value = formatEta(metrics.etaSeconds),
                    modifier = Modifier.weight(1f),
                )
            }

            if (metrics.ramTotalMb > 0) {
                val progress = (metrics.ramUsedMb.toFloat() / metrics.ramTotalMb.toFloat())
                    .coerceIn(0f, 1f)
                Column {
                    Text(
                        "Memory pressure",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (metrics.isPaused) {
                    Button(
                        onClick = viewModel::resume,
                        modifier = Modifier.weight(1f),
                        enabled = metrics.isRunning || metrics.isPaused,
                    ) {
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::pause,
                        modifier = Modifier.weight(1f),
                        enabled = metrics.isRunning,
                    ) {
                        Text("Pause")
                    }
                }
                Button(
                    onClick = viewModel::stop,
                    modifier = Modifier.weight(1f),
                    enabled = metrics.isRunning || metrics.isPaused,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun SevereThermalBanner(state: ThermalState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (state == ThermalState.Critical) {
                    ThermalSevereColor.copy(alpha = 0.25f)
                } else {
                    ThermalWarnColor.copy(alpha = 0.22f)
                },
            )
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = if (state == ThermalState.Critical) ThermalSevereColor else ThermalWarnColor,
            )
            Column {
                Text(
                    text = if (state == ThermalState.Critical) {
                        "Severe thermal — training may throttle"
                    } else {
                        "Elevated thermal — monitor device heat"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pause training or cool the device to protect hardware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

private fun thermalColor(state: ThermalState): androidx.compose.ui.graphics.Color = when (state) {
    ThermalState.Nominal -> BatteryGoodColor
    ThermalState.Fair -> ThermalWarnColor
    ThermalState.Serious, ThermalState.Critical -> ThermalSevereColor
}

private fun formatRam(m: UiTrainingMetrics): String {
    if (m.ramTotalMb <= 0L) return "—"
    return "${m.ramUsedMb} / ${m.ramTotalMb} MB"
}

private fun formatEta(seconds: Long): String {
    if (seconds < 0) return "—"
    if (seconds == 0L) return "Done"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

