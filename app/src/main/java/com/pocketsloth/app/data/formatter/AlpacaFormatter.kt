package com.pocketsloth.app.data.formatter

import com.pocketsloth.app.domain.formatter.PromptFormatter
import com.pocketsloth.app.domain.model.InstructionExample
import com.pocketsloth.app.domain.model.PromptFormat

/**
 * Stanford Alpaca-style prompt formatter.
 */
class AlpacaFormatter : PromptFormatter {
    override val format: PromptFormat = PromptFormat.ALPACA

    override fun format(example: InstructionExample): String {
        return if (example.hasInput) {
            buildString {
                append("Below is an instruction that describes a task, ")
                append("paired with an input that provides further context. ")
                append("Write a response that appropriately completes the request.\n\n")
                append("### Instruction:\n")
                append(example.instruction.trim())
                append("\n\n### Input:\n")
                append(example.input!!.trim())
                append("\n\n### Response:\n")
                append(example.output.trim())
            }
        } else {
            buildString {
                append("Below is an instruction that describes a task. ")
                append("Write a response that appropriately completes the request.\n\n")
                append("### Instruction:\n")
                append(example.instruction.trim())
                append("\n\n### Response:\n")
                append(example.output.trim())
            }
        }
    }
}
