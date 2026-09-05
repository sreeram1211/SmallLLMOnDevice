package com.pocketsloth.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatCompareLayout {
    Segmented,
    SideBySide,
}

data class ChatPlaygroundUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sideBySideBase: List<ChatMessage> = emptyList(),
    val sideBySideAdapter: List<ChatMessage> = emptyList(),
    val input: String = "",
    val mode: ChatModelMode = ChatModelMode.FineTunedAdapter,
    val layout: ChatCompareLayout = ChatCompareLayout.Segmented,
    val isGenerating: Boolean = false,
    val exportPath: String? = null,
    val snackbar: String? = null,
)

/**
 * Chat playground with streaming tokens and Base vs Adapter A/B.
 *
 * TODO: inject NativeLoRAEngine (com.pocketsloth.app.native.NativeLoRAEngine)
 *       for real token streaming / adapter export.
 */
class ChatPlaygroundViewModel : ViewModel() {

    // TODO: inject NativeLoRAEngine (com.pocketsloth.app.native.NativeLoRAEngine)
    private val inference: ChatInferenceGateway? = null


    private val _uiState = MutableStateFlow(ChatPlaygroundUiState())
    val uiState: StateFlow<ChatPlaygroundUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    fun setInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun setMode(mode: ChatModelMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setLayout(layout: ChatCompareLayout) {
        _uiState.update { it.copy(layout = layout) }
    }

    fun send() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isGenerating) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            content = prompt,
        )
        _uiState.update {
            it.copy(
                input = "",
                isGenerating = true,
                messages = it.messages + userMsg,
            )
        }

        if (_uiState.value.layout == ChatCompareLayout.SideBySide) {
            streamSideBySide(prompt)
        } else {
            streamSingle(prompt, _uiState.value.mode)
        }
    }

    private fun streamSingle(prompt: String, mode: ChatModelMode) {
        streamJob?.cancel()
        val assistantId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(
                    id = assistantId,
                    role = ChatRole.Assistant,
                    content = "",
                    isStreaming = true,
                    modelMode = mode,
                ),
            )
        }
        streamJob = viewModelScope.launch {
            val useAdapter = mode == ChatModelMode.FineTunedAdapter
            try {
                if (inference != null) {
                    inference.streamCompletion(prompt, useAdapter).collect { token ->
                        appendToken(assistantId, token, single = true)
                    }
                } else {
                    simulateStream(prompt, useAdapter).forEach { token ->
                        appendToken(assistantId, token, single = true)
                        delay(28)
                    }
                }
            } finally {
                finalizeMessage(assistantId, single = true)
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun streamSideBySide(prompt: String) {
        streamJob?.cancel()
        val baseId = UUID.randomUUID().toString()
        val adapterId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            content = prompt,
        )
        _uiState.update {
            it.copy(
                sideBySideBase = it.sideBySideBase + userMsg + ChatMessage(
                    id = baseId,
                    role = ChatRole.Assistant,
                    content = "",
                    isStreaming = true,
                    modelMode = ChatModelMode.Base,
                ),
                sideBySideAdapter = it.sideBySideAdapter + userMsg.copy(id = UUID.randomUUID().toString()) +
                    ChatMessage(
                        id = adapterId,
                        role = ChatRole.Assistant,
                        content = "",
                        isStreaming = true,
                        modelMode = ChatModelMode.FineTunedAdapter,
                    ),
            )
        }
        streamJob = viewModelScope.launch {
            try {
                val baseTokens = simulateStream(prompt, useAdapter = false)
                val adapterTokens = simulateStream(prompt, useAdapter = true)
                val max = maxOf(baseTokens.size, adapterTokens.size)
                for (i in 0 until max) {
                    if (i < baseTokens.size) appendToken(baseId, baseTokens[i], single = false, base = true)
                    if (i < adapterTokens.size) appendToken(adapterId, adapterTokens[i], single = false, base = false)
                    delay(28)
                }
            } finally {
                finalizeMessage(baseId, single = false, base = true)
                finalizeMessage(adapterId, single = false, base = false)
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun appendToken(
        messageId: String,
        token: String,
        single: Boolean,
        base: Boolean = true,
    ) {
        _uiState.update { state ->
            if (single) {
                state.copy(
                    messages = state.messages.map { m ->
                        if (m.id == messageId) m.copy(content = m.content + token) else m
                    },
                )
            } else if (base) {
                state.copy(
                    sideBySideBase = state.sideBySideBase.map { m ->
                        if (m.id == messageId) m.copy(content = m.content + token) else m
                    },
                )
            } else {
                state.copy(
                    sideBySideAdapter = state.sideBySideAdapter.map { m ->
                        if (m.id == messageId) m.copy(content = m.content + token) else m
                    },
                )
            }
        }
    }

    private fun finalizeMessage(messageId: String, single: Boolean, base: Boolean = true) {
        _uiState.update { state ->
            fun mark(list: List<ChatMessage>) = list.map {
                if (it.id == messageId) it.copy(isStreaming = false) else it
            }
            when {
                single -> state.copy(messages = mark(state.messages))
                base -> state.copy(sideBySideBase = mark(state.sideBySideBase))
                else -> state.copy(sideBySideAdapter = mark(state.sideBySideAdapter))
            }
        }
    }

    private suspend fun simulateStream(prompt: String, useAdapter: Boolean): List<String> {
        // TODO: NativeLoRAEngine.generateStreaming(prompt, adapter=useAdapter)
        val prefix = if (useAdapter) {
            "Adapter: "
        } else {
            "Base: "
        }
        val body = if (useAdapter) {
            "Fine-tuned reply for «${prompt.take(48)}» — concise and on-domain."
        } else {
            "Generic base-model reply regarding «${prompt.take(48)}»."
        }
        val full = prefix + body + if (Random.nextBoolean()) " ✓" else "."
        return full.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
    }

    fun exportAdapter() {
        viewModelScope.launch {
            val path = inference?.exportAdapter()
                ?: "/data/data/com.pocketsloth.app/files/adapters/lora_adapter.safetensors"
            // TODO: call NativeLoRAEngine.exportAdapter() / FineTuningService.export()
            _uiState.update {
                it.copy(
                    exportPath = path,
                    snackbar = "Adapter ready to share: $path",
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }

    fun clearChat() {
        streamJob?.cancel()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                sideBySideBase = emptyList(),
                sideBySideAdapter = emptyList(),
                isGenerating = false,
            )
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
