package com.pocketsloth.app.data.local.db

import androidx.room.TypeConverter
import com.pocketsloth.app.domain.model.DatasetSourceFormat
import com.pocketsloth.app.domain.model.PromptFormat
import com.pocketsloth.app.domain.model.TrainingRunStatus

/**
 * Room [TypeConverter]s for domain enums stored as stable string names.
 */
class Converters {
    @TypeConverter
    fun fromDatasetSourceFormat(value: DatasetSourceFormat): String = value.name

    @TypeConverter
    fun toDatasetSourceFormat(value: String): DatasetSourceFormat =
        runCatching { DatasetSourceFormat.valueOf(value) }
            .getOrDefault(DatasetSourceFormat.UNKNOWN)

    @TypeConverter
    fun fromTrainingRunStatus(value: TrainingRunStatus): String = value.name

    @TypeConverter
    fun toTrainingRunStatus(value: String): TrainingRunStatus =
        runCatching { TrainingRunStatus.valueOf(value) }
            .getOrDefault(TrainingRunStatus.PENDING)

    @TypeConverter
    fun fromPromptFormat(value: PromptFormat): String = value.name

    @TypeConverter
    fun toPromptFormat(value: String): PromptFormat =
        runCatching { PromptFormat.valueOf(value) }
            .getOrDefault(PromptFormat.CHAT_ML)
}
