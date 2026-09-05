package com.pocketsloth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.pocketsloth.app.di.LocalAppContainer
import com.pocketsloth.app.presentation.navigation.PocketSlothNavHost
import com.pocketsloth.app.ui.theme.PocketSlothTheme

/**
 * Hosts the Material3 NavHost for PocketSloth routes.
 * Provides [LocalAppContainer] so screens obtain ViewModels via [com.pocketsloth.app.di.AppViewModelFactory].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PocketSlothApp
        setContent {
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                PocketSlothTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        PocketSlothNavHost()
                    }
                }
            }
        }
    }
}
