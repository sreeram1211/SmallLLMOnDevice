package com.pocketsloth.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketsloth.app.domain.model.InstructionExample

@Entity(
    tableName = "examples",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["dataset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dataset_id"])],
)
data class ExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "dataset_id")
    val datasetId: Long,
    val instruction: String,
    val input: String? = null,
    val output: String,
)

fun ExampleEntity.toDomain(): InstructionExample = InstructionExample(
    id = id,
    datasetId = datasetId,
    instruction = instruction,
    input = input,
    output = output,
)

fun InstructionExample.toEntity(datasetId: Long = this.datasetId): ExampleEntity = ExampleEntity(
    id = id,
    datasetId = datasetId,
    instruction = instruction,
    input = input,
    output = output,
)
