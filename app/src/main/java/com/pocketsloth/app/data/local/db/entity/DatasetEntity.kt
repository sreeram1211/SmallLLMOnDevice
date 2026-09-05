package com.pocketsloth.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketsloth.app.domain.model.Dataset
import com.pocketsloth.app.domain.model.DatasetSourceFormat

@Entity(
    tableName = "datasets",
    indices = [Index(value = ["name"])],
)
data class DatasetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "source_format")
    val sourceFormat: DatasetSourceFormat,
)

fun DatasetEntity.toDomain(exampleCount: Int = 0): Dataset = Dataset(
    id = id,
    name = name,
    description = description,
    exampleCount = exampleCount,
    createdAtEpochMs = createdAtEpochMs,
    sourceFormat = sourceFormat,
)

fun Dataset.toEntity(): DatasetEntity = DatasetEntity(
    id = id,
    name = name,
    description = description,
    createdAtEpochMs = createdAtEpochMs,
    sourceFormat = sourceFormat,
)
