package com.pocketsloth.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pocketsloth.app.presentation.viewmodel.ChatPlaygroundViewModel
import com.pocketsloth.app.presentation.viewmodel.DatasetViewModel
import com.pocketsloth.app.presentation.viewmodel.TrainingConfigViewModel
import com.pocketsloth.app.presentation.viewmodel.TrainingDashboardViewModel

/**
 * Creates presentation ViewModels with real Room / DataStore / FineTuningService deps
 * from [AppContainer].
 */
class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(DatasetViewModel::class.java) ->
                DatasetViewModel(
                    datasetRepository = container.datasetRepository,
                    appContext = container.appContext,
                    jsonlImporter = container.jsonlImporter,
                    csvImporter = container.csvImporter,
                )
            modelClass.isAssignableFrom(TrainingConfigViewModel::class.java) ->
                TrainingConfigViewModel(
                    appContext = container.appContext,
                    hyperparamsRepository = container.hyperparamsRepository,
                    datasetRepository = container.datasetRepository,
                    trainingRunRepository = container.trainingRunRepository,
                )
            modelClass.isAssignableFrom(TrainingDashboardViewModel::class.java) ->
                TrainingDashboardViewModel(
                    appContext = container.appContext,
                )
            modelClass.isAssignableFrom(ChatPlaygroundViewModel::class.java) ->
                ChatPlaygroundViewModel(
                    appContext = container.appContext,
                    trainingRunRepository = container.trainingRunRepository,
                    loraEngine = container.loraEngine,
                )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
