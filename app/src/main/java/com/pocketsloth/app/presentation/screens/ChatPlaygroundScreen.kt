package com.pocketsloth.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pocketsloth.app.presentation.viewmodel.ChatCompareLayout
import com.pocketsloth.app.presentation.viewmodel.ChatMessage
import com.pocketsloth.app.presentation.viewmodel.ChatModelMode
import com.pocketsloth.app.presentation.viewmodel.ChatPlaygroundViewModel
import com.pocketsloth.app.presentation.viewmodel.ChatRole
import com.pocketsloth.app.ui.theme.AdapterAccent
import com.pocketsloth.app.ui.theme.AssistantBubbleColor
import com.pocketsloth.app.ui.theme.BaseModelAccent
import com.pocketsloth.app.ui.theme.UserBubbleColor

@Composable
fun ChatPlaygroundScreen(modifier: Modifier = Modifier) {
    val container = com.pocketsloth.app.di.LocalAppContainer.current
    val factory = androidx.compose.runtime.remember(container) {
        com.pocketsloth.app.di.AppViewModelFactory(container)
    }
    ChatPlaygroundScreen(
        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPlaygroundScreen(
    viewModel: ChatPlaygroundViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        val msg = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Chat playground") },
                actions = {
                    IconButton(onClick = viewModel::clearChat) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear chat")
                    }
                    IconButton(onClick = viewModel::exportAdapter) {
                        Icon(Icons.Filled.Share, contentDescription = "Export / share adapter")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Compare layout", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.layout == ChatCompareLayout.Segmented,
                        onClick = { viewModel.setLayout(ChatCompareLayout.Segmented) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("Segmented")
                    }
                    SegmentedButton(
                        selected = state.layout == ChatCompareLayout.SideBySide,
                        onClick = { viewModel.setLayout(ChatCompareLayout.SideBySide) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Side-by-side")
                    }
                }

                if (state.layout == ChatCompareLayout.Segmented) {
                    Text("Model", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == ChatModelMode.Base,
                            onClick = { viewModel.setMode(ChatModelMode.Base) },
                            label = { Text("Base") },
                        )
                        FilterChip(
                            selected = state.mode == ChatModelMode.FineTunedAdapter,
                            onClick = { viewModel.setMode(ChatModelMode.FineTunedAdapter) },
                            label = { Text("Fine-Tuned Adapter") },
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::exportAdapter,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Export / share adapter")
                }
            }

            when (state.layout) {
                ChatCompareLayout.Segmented -> {
                    ChatMessageList(
                        messages = state.messages,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
                ChatCompareLayout.SideBySide -> {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            SideLabel("Base", BaseModelAccent)
                            ChatMessageList(
                                messages = state.sideBySideBase,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            SideLabel("Fine-Tuned", AdapterAccent)
                            ChatMessageList(
                                messages = state.sideBySideAdapter,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message PocketSloth…") },
                    maxLines = 4,
                    enabled = !state.isGenerating,
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.input.isNotBlank() && !state.isGenerating,
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (state.input.isNotBlank() && !state.isGenerating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SideLabel(text: String, accent: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatBubble(message = message, compact = compact)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    compact: Boolean,
) {
    val isUser = message.role == ChatRole.User
    val bubbleColor = when {
        isUser -> UserBubbleColor
        message.modelMode == ChatModelMode.Base -> AssistantBubbleColor.copy(alpha = 0.9f)
        message.modelMode == ChatModelMode.FineTunedAdapter -> AssistantBubbleColor
        else -> AssistantBubbleColor
    }
    val accent = when (message.modelMode) {
        ChatModelMode.Base -> BaseModelAccent
        ChatModelMode.FineTunedAdapter -> AdapterAccent
        null -> Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = if (compact) 220.dp else 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (!isUser && message.modelMode != null && !compact) {
                Text(
                    text = when (message.modelMode) {
                        ChatModelMode.Base -> "Base"
                        ChatModelMode.FineTunedAdapter -> "Adapter"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        ),
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.content + if (message.isStreaming) "▍" else "",
                    style = if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
