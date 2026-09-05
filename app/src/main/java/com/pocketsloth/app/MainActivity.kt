package com.pocketsloth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketsloth.app.presentation.navigation.PocketSlothNavHost
import com.pocketsloth.app.ui.theme.PocketSlothTheme

/**
 * Hosts the Material3 NavHost for PocketSloth routes.
 * Navigation + bottom bar live in [com.pocketsloth.app.presentation.navigation.PocketSlothNavHost].
 * Screen bodies live in [com.pocketsloth.app.presentation.screens].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketSlothTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PocketSlothNavHost()
                }
            }
        }
    }
}
