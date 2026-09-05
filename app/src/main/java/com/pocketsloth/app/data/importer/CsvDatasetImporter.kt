package com.pocketsloth.app.data.importer

import com.pocketsloth.app.domain.model.InstructionExample
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Imports instruction datasets from CSV with a header row.
 *
 * Required columns (case-insensitive): instruction (or prompt/question),
 * output (or response/completion/answer). Optional: input (or context).
 *
 * Supports RFC4180-ish quoted fields with doubled quotes.
 */
class CsvDatasetImporter : DatasetImporter {
    override fun import(input: InputStream): List<InstructionExample> {
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            val headerLine = reader.readLine()
                ?: throw DatasetImportException("CSV is empty")
            val headers = parseCsvLine(headerLine).map { it.trim().lowercase() }
            if (headers.isEmpty()) {
                throw DatasetImportException("CSV header row is empty")
            }

            val instructionIdx = indexOf(headers, "instruction", "prompt", "question")
                ?: throw DatasetImportException(
                    "CSV missing instruction column (instruction|prompt|question)",
                )
            val outputIdx = indexOf(headers, "output", "response", "completion", "answer")
                ?: throw DatasetImportException(
                    "CSV missing output column (output|response|completion|answer)",
                )
            val inputIdx = indexOf(headers, "input", "context")

            val examples = ArrayList<InstructionExample>()
            var lineNo = 1
            while (true) {
                val line = reader.readLine() ?: break
                lineNo++
                if (line.isBlank()) continue
                val cols = parseCsvLine(line)
                val instruction = cols.getOrNull(instructionIdx)?.trim().orEmpty()
                val output = cols.getOrNull(outputIdx)?.trim().orEmpty()
                if (instruction.isEmpty() || output.isEmpty()) {
                    throw DatasetImportException(
                        "CSV row $lineNo missing instruction or output",
                    )
                }
                val inputField = inputIdx?.let { cols.getOrNull(it)?.trim() }
                    ?.takeIf { it.isNotEmpty() }
                examples.add(
                    InstructionExample(
                        instruction = instruction,
                        input = inputField,
                        output = output,
                    ),
                )
            }
            return examples
        }
    }

    private fun indexOf(headers: List<String>, vararg names: String): Int? {
        for (name in names) {
            val idx = headers.indexOf(name)
            if (idx >= 0) return idx
        }
        return null
    }

    /** Minimal CSV line parser with quote support. */
    internal fun parseCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                !inQuotes && c == '"' -> inQuotes = true
                !inQuotes && c == ',' -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
