package com.audiobookreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiobookreader.playback.PlaybackService
import com.audiobookreader.ui.theme.BookReaderTheme

class MainActivity : ComponentActivity() {
    private val readerViewModel by viewModels<ReaderViewModel> { ReaderViewModel.factory(applicationContext) }
    private var showBatteryOptimizationPrompt by mutableStateOf(false)
    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PlaybackService.ACTION_PROGRESS) return
            readerViewModel.updatePlaybackProgress(
                intent.getStringExtra("bookId").orEmpty(),
                intent.getIntExtra("start", 0),
                intent.getLongExtra("position", 0L),
                intent.getIntExtra("itemCount", 1),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateBatteryOptimizationPrompt()
        setContent {
            val state by readerViewModel.state.collectAsStateWithLifecycle()
            BookReaderTheme(themeMode = state.themeMode) {
                AudiobookReaderApp(
                    viewModel = readerViewModel,
                    showBatteryOptimizationPrompt = showBatteryOptimizationPrompt,
                    onRequestBatteryOptimization = { openBatteryOptimizationSettings(markPromptSeen = true) },
                    onDismissBatteryOptimization = { markBatteryPromptSeen() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptimizationPrompt()
    }

    private fun updateBatteryOptimizationPrompt() {
        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        val wasShown = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PROMPT_SHOWN_KEY, false)
        showBatteryOptimizationPrompt = !isIgnoring && !wasShown
    }

    private fun markBatteryPromptSeen() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PROMPT_SHOWN_KEY, true)
            .apply()
        showBatteryOptimizationPrompt = false
    }

    private fun openBatteryOptimizationSettings(markPromptSeen: Boolean) {
        if (markPromptSeen) markBatteryPromptSeen()
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    companion object {
        private const val PREFS_NAME = "bookreader_preferences"
        private const val PROMPT_SHOWN_KEY = "battery_optimization_prompt_shown"
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PlaybackService.ACTION_PROGRESS)
        androidx.core.content.ContextCompat.registerReceiver(
            this, progressReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(progressReceiver)
        super.onStop()
    }
}
