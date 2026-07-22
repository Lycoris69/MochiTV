package com.lycoris.mochitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lycoris.mochitv.presentation.navigation.AppNavGraph
import com.lycoris.mochitv.presentation.theme.MochiTVTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Compose owns the entire UI surface;
 * this activity is just the window container.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draws content behind system bars — Compose scaffolds handle insets.
        enableEdgeToEdge()
        setContent {
            MochiTVTheme {
                AppNavGraph()
            }
        }
    }
}
