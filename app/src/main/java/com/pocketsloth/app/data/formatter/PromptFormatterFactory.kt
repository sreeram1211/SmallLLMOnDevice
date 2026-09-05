package com.pocketsloth.app.data.formatter

import com.pocketsloth.app.domain.formatter.PromptFormatter
import com.pocketsloth.app.domain.model.PromptFormat

object PromptFormatterFactory {
    fun create(format: PromptFormat): PromptFormatter = when (format) {
        PromptFormat.CHAT_ML -> ChatMlFormatter()
        PromptFormat.ALPACA -> AlpacaFormatter()
    }
}
