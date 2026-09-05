package com.pocketsloth.app.domain.model

/**
 * A single supervised fine-tuning example in instruction–input–output form.
 */
data class InstructionExample(
    val id: Long = 0L,
    val datasetId: Long = 0L,
    val instruction: String,
    val input: String? = null,
    val output: String,
) {
    init {
        require(instruction.isNotBlank()) { "instruction must not be blank" }
        require(output.isNotBlank()) { "output must not be blank" }
    }

    /** True when an optional context/input field is present. */
    val hasInput: Boolean get() = !input.isNullOrBlank()
}
