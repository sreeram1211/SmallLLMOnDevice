package com.pocketsloth.app.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pocketsloth.app.presentation.viewmodel.TrainingConfigEvent
import com.pocketsloth.app.presentation.viewmodel.TrainingConfigViewModel
import kotlin.math.roundToInt

@Composable
fun TrainingConfigScreen(modifier: Modifier = Modifier) {
    val container = com.pocketsloth.app.di.LocalAppContainer.current
    val factory = androidx.compose.runtime.remember(container) {
        com.pocketsloth.app.di.AppViewModelFactory(container)
    }
    TrainingConfigScreen(
        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory),
        onTrainingStarted = {},
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainingConfigScreen(
    viewModel: TrainingConfigViewModel,
    onTrainingStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val cfg = state.config

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TrainingConfigEvent.NavigateToDashboard -> onTrainingStarted()
            }
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.setModelPath(uri.toString())
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Training config") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Base model", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = cfg.modelPath,
                onValueChange = viewModel::setModelPath,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model path") },
                placeholder = { Text("/sdcard/models/phi-3-mini.gguf") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            modelPicker.launch(arrayOf("*/*"))
                        },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Pick model file")
                    }
                },
            )

            Text("LoRA rank (r)", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(4, 8, 16).forEach { rank ->
                    FilterChip(
                        selected = cfg.loraRank == rank,
                        onClick = { viewModel.setLoraRank(rank) },
                        label = { Text("r = $rank") },
                    )
                }
            }

            OutlinedTextField(
                value = cfg.loraAlpha.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let(viewModel::setLoraAlpha)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LoRA alpha") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Text("Batch size", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2).forEach { batch ->
                    FilterChip(
                        selected = cfg.batchSize == batch,
                        onClick = { viewModel.setBatchSize(batch) },
                        label = { Text("$batch") },
                    )
                }
            }

            OutlinedTextField(
                value = cfg.gradAccumulation.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let(viewModel::setGradAccumulation)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gradient accumulation steps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Text(
                text = "Context length: ${cfg.contextLength}",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = cfg.contextLength.toFloat(),
                onValueChange = { viewModel.setContextLength(it.roundToInt()) },
                valueRange = 256f..512f,
                steps = 7, // 256, 288, … 512-ish; UI still snaps via coerce
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("256", style = MaterialTheme.typography.labelSmall)
                Text("512", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedTextField(
                value = cfg.learningRate.toString(),
                onValueChange = { raw ->
                    raw.toFloatOrNull()?.let(viewModel::setLearningRate)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AdamW learning rate") },
                placeholder = { Text("2e-4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = { Text("Typical range 1e-5 … 5e-4") },
            )

            OutlinedTextField(
                value = cfg.epochs.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let(viewModel::setEpochs)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Epochs") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            state.validationError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::startTraining,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isStarting,
            ) {
                if (state.isStarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Start Training")
                }
            }

            Text(
                text = "Starts FineTuningService → NativeLoRAEngine",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
