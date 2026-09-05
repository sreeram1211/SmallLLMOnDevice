package com.pocketsloth.app.data.formatter

import com.pocketsloth.app.domain.formatter.PromptFormatter
import com.pocketsloth.app.domain.model.InstructionExample
import com.pocketsloth.app.domain.model.PromptFormat

/**
 * ChatML prompt formatter:
 *
 * ```
 * <|im_start|>user
 * {instruction}\n{input?}
 * <|im_end|>
 * <|im_start|>assistant
 * {output}
 * <|im_end|>
 * ```
 */
class ChatMlFormatter : PromptFormatter {
    override val format: PromptFormat = PromptFormat.CHAT_ML

    override fun format(example: InstructionExample): String {
        val userContent = buildString {
            append(example.instruction.trim())
            if (example.hasInput) {
                append('\n')
                append(example.input!!.trim())
            }
        }
        return buildString {
            append("<|im_start|>user\n")
            append(userContent)
            append("\n<|im_end|>\n")
            append("<|im_start|>assistant\n")
            append(example.output.trim())
            append("\n<|im_end|>")
        }
    }
}
