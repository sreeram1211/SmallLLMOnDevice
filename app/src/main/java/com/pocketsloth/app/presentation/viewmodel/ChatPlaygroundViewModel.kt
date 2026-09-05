package com.pocketsloth.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsloth.app.domain.model.TrainingRunStatus
import com.pocketsloth.app.domain.repository.TrainingRunRepository
import com.pocketsloth.app.native.NativeLoRAEngine
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    val adapterPath: String? = null,
    val snackbar: String? = null,
)

/**
 * Chat playground with streaming tokens and Base vs Adapter A/B.
 *
 * Inference is still stubbed (thin [ChatInferenceGateway]); adapter path is loaded
 * from the latest completed [com.pocketsloth.app.domain.model.TrainingRun].
 */
class ChatPlaygroundViewModel(
    private val appContext: Context,
    private val trainingRunRepository: TrainingRunRepository,
    private val loraEngine: NativeLoRAEngine,
) : ViewModel() {

    private val inference: ChatInferenceGateway = StubChatInferenceGateway(
        appContext = appContext,
        trainingRunRepository = trainingRunRepository,
        loraEngine = loraEngine,
    )

    private val _uiState = MutableStateFlow(ChatPlaygroundUiState())
    val uiState: StateFlow<ChatPlaygroundUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            refreshAdapterPath()
        }
    }

    private suspend fun refreshAdapterPath() {
        val path = inference.shareAdapterPath()
        _uiState.update { it.copy(adapterPath = path, exportPath = path) }
    }

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
                inference.streamCompletion(prompt, useAdapter).collect { token ->
                    appendToken(assistantId, token, single = true)
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
                launch {
                    inference.streamCompletion(prompt, useAdapter = false).collect { token ->
                        appendToken(baseId, token, single = false, base = true)
                    }
                }.join()
                // Stream adapter after base for simpler sequencing (both still stubbed)
                inference.streamCompletion(prompt, useAdapter = true).collect { token ->
                    appendToken(adapterId, token, single = false, base = false)
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

    fun exportAdapter() {
        viewModelScope.launch {
            val path = inference.exportAdapter()
            _uiState.update {
                it.copy(
                    exportPath = path,
                    adapterPath = path,
                    snackbar = if (path != null) {
                        "Adapter ready to share: $path"
                    } else {
                        "No completed training run adapter found yet."
                    },
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

/**
 * Stub inference gateway: simulated token stream until native generate lands.
 * Adapter path is resolved from the latest completed TrainingRun.
 */
private class StubChatInferenceGateway(
    private val appContext: Context,
    private val trainingRunRepository: TrainingRunRepository,
    private val loraEngine: NativeLoRAEngine,
) : ChatInferenceGateway {

    override fun streamCompletion(prompt: String, useAdapter: Boolean): Flow<String> = flow {
        val prefix = if (useAdapter) "Adapter: " else "Base: "
        val stubNote = if (loraEngine.isStubMode()) " [native stub]" else ""
        val body = if (useAdapter) {
            "Fine-tuned reply for «${prompt.take(48)}» — concise and on-domain.$stubNote"
        } else {
            "Generic base-model reply regarding «${prompt.take(48)}».$stubNote"
        }
        val full = prefix + body + if (Random.nextBoolean()) " ✓" else "."
        full.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }.forEach { token ->
            emit(token)
            delay(28)
        }
    }

    override suspend fun exportAdapter(): String? = resolveAdapterPath()

    override suspend fun shareAdapterPath(): String? = resolveAdapterPath()

    private suspend fun resolveAdapterPath(): String? {
        val runs = trainingRunRepository.observeRuns().first()
        val completed = runs.firstOrNull { it.status == TrainingRunStatus.COMPLETED }
            ?: runs.firstOrNull { it.adapterOutputPath.isNotBlank() }
        if (completed != null) return completed.adapterOutputPath
        val dir = java.io.File(appContext.filesDir, "adapters")
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.isFile && it.name.contains("lora") }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
    }
}
