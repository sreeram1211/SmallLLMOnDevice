package com.pocketsloth.app.presentation.navigation

object Routes {
    const val HOME = "home"
    const val DATASETS = "datasets"
    const val CONFIG = "config"
    const val TRAINING = "training"
    const val CHAT = "chat"
}

enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    Home(Routes.HOME, "Home"),
    Datasets(Routes.DATASETS, "Datasets"),
    Config(Routes.CONFIG, "Config"),
    Training(Routes.TRAINING, "Training"),
    Chat(Routes.CHAT, "Chat"),
}
