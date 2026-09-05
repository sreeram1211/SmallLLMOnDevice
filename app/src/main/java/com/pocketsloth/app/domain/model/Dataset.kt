package com.pocketsloth.app.domain.model

/**
 * Named collection of [InstructionExample]s ready for on-device fine-tuning.
 */
data class Dataset(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val exampleCount: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val sourceFormat: DatasetSourceFormat = DatasetSourceFormat.UNKNOWN,
) {
    init {
        require(name.isNotBlank()) { "dataset name must not be blank" }
        require(exampleCount >= 0) { "exampleCount must be >= 0" }
    }
}

enum class DatasetSourceFormat {
    JSONL,
    CSV,
    MANUAL,
    UNKNOWN,
}
