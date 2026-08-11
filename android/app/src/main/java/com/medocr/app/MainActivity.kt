package com.medocr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medocr.app.ui.main.MainViewModel
import com.medocr.app.ui.screens.HomeScreen
import com.medocr.app.ui.theme.MedOcrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            MedOcrTheme(themeMode = state.themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
