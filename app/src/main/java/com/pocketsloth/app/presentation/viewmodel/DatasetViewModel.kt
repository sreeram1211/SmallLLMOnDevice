package com.pocketsloth.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DatasetUiState(
    val examples: List<UiInstructionExample> = emptyList(),
    val editing: UiInstructionExample? = null,
    val isEditorOpen: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val filterQuery: String = "",
) {
    val filtered: List<UiInstructionExample>
        get() {
            val q = filterQuery.trim().lowercase()
            if (q.isEmpty()) return examples
            return examples.filter {
                it.instruction.lowercase().contains(q) ||
                    it.input.lowercase().contains(q) ||
                    it.output.lowercase().contains(q)
            }
        }
}

/**
 * Dataset screen VM.
 * TODO: inject DatasetRepositoryGateway from com.pocketsloth.app.data / domain
 */
class DatasetViewModel : ViewModel() {

    // TODO: inject DatasetRepositoryGateway from com.pocketsloth.app.data / domain via DI
    private val repository: DatasetRepositoryGateway = InMemoryDatasetRepository()


    private val _uiState = MutableStateFlow(DatasetUiState(isLoading = true))
    val uiState: StateFlow<DatasetUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = repository.listExamples()
            _uiState.update {
                it.copy(examples = list, isLoading = false, message = null)
            }
        }
    }

    fun setFilter(query: String) {
        _uiState.update { it.copy(filterQuery = query) }
    }

    fun openCreate() {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editing = UiInstructionExample(
                    id = UUID.randomUUID().toString(),
                    instruction = "",
                    input = "",
                    output = "",
                ),
            )
        }
    }

    fun openEdit(example: UiInstructionExample) {
        _uiState.update { it.copy(isEditorOpen = true, editing = example) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditorOpen = false, editing = null) }
    }

    fun updateDraft(
        instruction: String? = null,
        input: String? = null,
        output: String? = null,
    ) {
        _uiState.update { state ->
            val draft = state.editing ?: return@update state
            state.copy(
                editing = draft.copy(
                    instruction = instruction ?: draft.instruction,
                    input = input ?: draft.input,
                    output = output ?: draft.output,
                ),
            )
        }
    }

    fun saveDraft() {
        val draft = _uiState.value.editing ?: return
        if (draft.instruction.isBlank() || draft.output.isBlank()) {
            _uiState.update { it.copy(message = "Instruction and Output are required.") }
            return
        }
        viewModelScope.launch {
            repository.upsert(draft)
            closeEditor()
            refresh()
            _uiState.update { it.copy(message = "Example saved.") }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
            _uiState.update { it.copy(message = "Example deleted.") }
        }
    }

    /** Hook for SAF / file picker; wires to data layer when present. */
    fun importFromUri(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val count = repository.importFromUri(uri)
            refresh()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = if (count > 0) {
                        "Imported $count examples."
                    } else {
                        // TODO: wire com.pocketsloth.app.data import pipeline
                        "Import hooked — data layer not connected yet ($uri)."
                    },
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
