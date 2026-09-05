package com.pocketsloth.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketsloth.app.domain.model.PromptFormat
import com.pocketsloth.app.domain.model.TrainingHyperparams
import com.pocketsloth.app.domain.model.TrainingRun
import com.pocketsloth.app.domain.model.TrainingRunStatus

@Entity(
    tableName = "training_runs",
    foreignKeys = [
        ForeignKey(
            entity = DatasetEntity::class,
            parentColumns = ["id"],
            childColumns = ["dataset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["dataset_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"]),
    ],
)
data class TrainingRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "dataset_id")
    val datasetId: Long,
    val status: TrainingRunStatus,
    @Embedded(prefix = "hp_")
    val hyperparams: TrainingHyperparams,
    @ColumnInfo(name = "prompt_format")
    val promptFormat: PromptFormat,
    @ColumnInfo(name = "model_path")
    val modelPath: String,
    @ColumnInfo(name = "adapter_output_path")
    val adapterOutputPath: String,
    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long? = null,
    @ColumnInfo(name = "finished_at")
    val finishedAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_loss")
    val lastLoss: Float? = null,
    @ColumnInfo(name = "last_epoch")
    val lastEpoch: Int = 0,
    @ColumnInfo(name = "last_step")
    val lastStep: Int = 0,
    @ColumnInfo(name = "total_steps")
    val totalSteps: Int = 0,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
)

fun TrainingRunEntity.toDomain(): TrainingRun = TrainingRun(
    id = id,
    datasetId = datasetId,
    status = status,
    hyperparams = hyperparams,
    promptFormat = promptFormat,
    modelPath = modelPath,
    adapterOutputPath = adapterOutputPath,
    startedAtEpochMs = startedAtEpochMs,
    finishedAtEpochMs = finishedAtEpochMs,
    lastLoss = lastLoss,
    lastEpoch = lastEpoch,
    lastStep = lastStep,
    totalSteps = totalSteps,
    errorMessage = errorMessage,
    createdAtEpochMs = createdAtEpochMs,
)

fun TrainingRun.toEntity(): TrainingRunEntity = TrainingRunEntity(
    id = id,
    datasetId = datasetId,
    status = status,
    hyperparams = hyperparams,
    promptFormat = promptFormat,
    modelPath = modelPath,
    adapterOutputPath = adapterOutputPath,
    startedAtEpochMs = startedAtEpochMs,
    finishedAtEpochMs = finishedAtEpochMs,
    lastLoss = lastLoss,
    lastEpoch = lastEpoch,
    lastStep = lastStep,
    totalSteps = totalSteps,
    errorMessage = errorMessage,
    createdAtEpochMs = createdAtEpochMs,
)
