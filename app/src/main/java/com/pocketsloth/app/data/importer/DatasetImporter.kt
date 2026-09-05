package com.pocketsloth.app.data.importer

import com.pocketsloth.app.domain.model.InstructionExample
import java.io.InputStream

/**
 * Parses a dataset stream into [InstructionExample] rows (datasetId left unset until insert).
 */
interface DatasetImporter {
    fun import(input: InputStream): List<InstructionExample>
}
