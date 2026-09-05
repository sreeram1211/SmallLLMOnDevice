package com.pocketsloth.app.data.importer

import com.pocketsloth.app.domain.model.InstructionExample
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Imports instruction datasets from JSON Lines.
 *
 * Accepted per-line keys (first match wins):
 * - instruction / prompt / question
 * - input / context (optional)
 * - output / response / completion / answer
 *
 * Also supports OpenAI-ish `{ "messages": [ {role, content}, ... ] }` turns,
 * taking the last user message as instruction and last assistant as output.
 */
class JsonlDatasetImporter : DatasetImporter {
    override fun import(input: InputStream): List<InstructionExample> {
        val examples = ArrayList<InstructionExample>()
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).useLines { lines ->
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEachIndexed
                try {
                    val obj = JSONObject(line)
                    parseObject(obj)?.let { examples.add(it) }
                } catch (e: Exception) {
                    throw DatasetImportException(
                        "Invalid JSONL at line ${index + 1}: ${e.message}",
                        e,
                    )
                }
            }
        }
        return examples
    }

    private fun parseObject(obj: JSONObject): InstructionExample? {
        if (obj.has("messages")) {
            return parseMessages(obj.getJSONArray("messages"))
        }

        val instruction = firstString(obj, "instruction", "prompt", "question")
            ?: return null
        val output = firstString(obj, "output", "response", "completion", "answer")
            ?: return null
        val inputField = firstString(obj, "input", "context")
        return InstructionExample(
            instruction = instruction,
            input = inputField?.takeIf { it.isNotBlank() },
            output = output,
        )
    }

    private fun parseMessages(messages: org.json.JSONArray): InstructionExample? {
        var lastUser: String? = null
        var lastAssistant: String? = null
        var system: String? = null
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role").lowercase()
            val content = m.optString("content").trim()
            if (content.isEmpty()) continue
            when (role) {
                "system" -> system = content
                "user" -> lastUser = content
                "assistant" -> lastAssistant = content
            }
        }
        val instruction = when {
            !system.isNullOrBlank() && !lastUser.isNullOrBlank() ->
                "$system\n\n$lastUser"
            !lastUser.isNullOrBlank() -> lastUser
            else -> return null
        }
        val output = lastAssistant ?: return null
        return InstructionExample(instruction = instruction, input = null, output = output)
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key) ?: continue
            val text = value.toString().trim()
            if (text.isNotEmpty() && text != "null") return text
        }
        return null
    }
}

class DatasetImportException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
