package com.pocketsloth.app.domain.formatter

import com.pocketsloth.app.domain.model.InstructionExample
import com.pocketsloth.app.domain.model.PromptFormat

/**
 * Maps [InstructionExample] instances to prompt strings consumed by the native trainer.
 */
interface PromptFormatter {
    val format: PromptFormat
    fun format(example: InstructionExample): String
    fun formatAll(examples: Iterable<InstructionExample>): List<String> =
        examples.map(::format)
}
