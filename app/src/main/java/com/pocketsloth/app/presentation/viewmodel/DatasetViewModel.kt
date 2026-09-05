package com.pocketsloth.app.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsloth.app.data.importer.CsvDatasetImporter
import com.pocketsloth.app.data.importer.DatasetImportException
import com.pocketsloth.app.data.importer.JsonlDatasetImporter
import com.pocketsloth.app.domain.model.DatasetSourceFormat
import com.pocketsloth.app.domain.model.InstructionExample
import com.pocketsloth.app.domain.repository.DatasetRepository
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DatasetUiState(
    val examples: List<UiInstructionExample> = emptyList(),
    val editing: UiInstructionExample? = null,
    val isEditorOpen: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val filterQuery: String = "",
    val activeDatasetId: Long? = null,
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
 * Dataset screen VM backed by Room [DatasetRepository] + JSONL/CSV importers.
 */
class DatasetViewModel(
    private val datasetRepository: DatasetRepository,
    private val appContext: Context,
    private val jsonlImporter: JsonlDatasetImporter,
    private val csvImporter: CsvDatasetImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DatasetUiState(isLoading = true))
    val uiState: StateFlow<DatasetUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val datasetId = ensureDefaultDataset()
            _uiState.update { it.copy(activeDatasetId = datasetId) }
            observeExamples(datasetId)
        }
    }

    private suspend fun ensureDefaultDataset(): Long {
        val existing = datasetRepository.observeDatasets().first()
        val found = existing.firstOrNull()
        if (found != null) return found.id
        return datasetRepository.createDataset(
            name = "Default",
            description = "Primary on-device fine-tuning dataset",
            sourceFormat = DatasetSourceFormat.MANUAL,
        )
    }

    private fun observeExamples(datasetId: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            datasetRepository.observeExamples(datasetId).collect { list ->
                _uiState.update {
                    it.copy(
                        examples = list.map { ex -> ex.toUi() },
                        isLoading = false,
                        activeDatasetId = datasetId,
                    )
                }
            }
        }
    }

    fun refresh() {
        val id = _uiState.value.activeDatasetId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = datasetRepository.getExamples(id)
            _uiState.update {
                it.copy(examples = list.map { ex -> ex.toUi() }, isLoading = false)
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
                    id = "new-${UUID.randomUUID()}",
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
        val datasetId = _uiState.value.activeDatasetId ?: return
        if (draft.instruction.isBlank() || draft.output.isBlank()) {
            _uiState.update { it.copy(message = "Instruction and Output are required.") }
            return
        }
        viewModelScope.launch {
            val existingId =
                if (draft.id.startsWith("new-")) {
                    0L
                } else {
                    draft.id.toLongOrNull() ?: 0L
                }
            datasetRepository.upsertExample(
                datasetId = datasetId,
                example = InstructionExample(
                    id = existingId,
                    datasetId = datasetId,
                    instruction = draft.instruction.trim(),
                    input = draft.input.trim().takeIf { it.isNotEmpty() },
                    output = draft.output.trim(),
                ),
            )
            closeEditor()
            _uiState.update { it.copy(message = "Example saved.") }
        }
    }

    fun delete(id: String) {
        val exampleId = id.toLongOrNull() ?: return
        viewModelScope.launch {
            datasetRepository.deleteExample(exampleId)
            _uiState.update { it.copy(message = "Example deleted.") }
        }
    }

    /** SAF / file picker hook — routes to JSONL or CSV importer by extension / MIME. */
    fun importFromUri(uriString: String) {
        val datasetId = _uiState.value.activeDatasetId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val uri = Uri.parse(uriString)
                val name = uri.lastPathSegment?.lowercase().orEmpty()
                val mime = appContext.contentResolver.getType(uri)?.lowercase().orEmpty()
                val stream = appContext.contentResolver.openInputStream(uri)
                    ?: error("Unable to open $uriString")
                val isCsv = name.endsWith(".csv") || mime.contains("csv")
                val examples = stream.use { input ->
                    if (isCsv) csvImporter.import(input) else jsonlImporter.import(input)
                }
                val format = if (isCsv) DatasetSourceFormat.CSV else DatasetSourceFormat.JSONL
                val count = datasetRepository.addExamples(datasetId, examples)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Imported $count examples ($format).",
                    )
                }
            } catch (e: DatasetImportException) {
                _uiState.update {
                    it.copy(isLoading = false, message = "Import failed: ${e.message}")
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Import failed: ${t.message ?: t::class.java.simpleName}",
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

private fun InstructionExample.toUi() = UiInstructionExample(
    id = id.toString(),
    instruction = instruction,
    input = input.orEmpty(),
    output = output,
)
