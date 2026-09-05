package com.pocketsloth.app.di

import androidx.compose.runtime.staticCompositionLocalOf

/** CompositionLocal for [AppContainer] so NavHost / screens can build ViewModels. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap content with CompositionLocalProvider")
}
