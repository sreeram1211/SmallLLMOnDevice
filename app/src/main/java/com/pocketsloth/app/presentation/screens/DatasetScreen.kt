package com.pocketsloth.app.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pocketsloth.app.presentation.viewmodel.DatasetViewModel
import com.pocketsloth.app.presentation.viewmodel.UiInstructionExample

@Composable
fun DatasetScreen(modifier: Modifier = Modifier) {
    val container = com.pocketsloth.app.di.LocalAppContainer.current
    val factory = androidx.compose.runtime.remember(container) {
        com.pocketsloth.app.di.AppViewModelFactory(container)
    }
    DatasetScreen(
        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(
    viewModel: DatasetViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Hook into data layer via ViewModel (com.pocketsloth.app.data.*)
            viewModel.importFromUri(uri.toString())
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Datasets") },
                actions = {
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/*",
                                    "application/x-ndjson",
                                    "*/*",
                                ),
                            )
                        },
                    ) {
                        Text("Import")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreate) {
                Icon(Icons.Filled.Add, contentDescription = "Add example")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.filterQuery,
                onValueChange = viewModel::setFilter,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filterQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setFilter("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${state.filtered.size} examples",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading && state.examples.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.filtered.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No examples yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create Instruction / Input / Output pairs or import JSONL.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.filtered, key = { it.id }) { example ->
                            ExampleCard(
                                example = example,
                                onEdit = { viewModel.openEdit(example) },
                                onDelete = { viewModel.delete(example.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isEditorOpen && state.editing != null) {
        ExampleEditorDialog(
            example = state.editing!!,
            onInstructionChange = { viewModel.updateDraft(instruction = it) },
            onInputChange = { viewModel.updateDraft(input = it) },
            onOutputChange = { viewModel.updateDraft(output = it) },
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveDraft,
        )
    }
}

@Composable
private fun ExampleCard(
    example: UiInstructionExample,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = example.instruction,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (example.input.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Input: ${example.input}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Output: ${example.output}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExampleEditorDialog(
    example: UiInstructionExample,
    onInstructionChange: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onOutputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (example.instruction.isBlank() && example.output.isBlank()) "New example" else "Edit example")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = example.instruction,
                    onValueChange = onInstructionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instruction") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = example.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Input (optional)") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = example.output,
                    onValueChange = onOutputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Output") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
