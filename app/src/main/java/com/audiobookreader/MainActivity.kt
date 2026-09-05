package com.audiobookreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.audiobookreader.playback.PlaybackService
import com.audiobookreader.ui.theme.BookReaderTheme

class MainActivity : ComponentActivity() {
    private val readerViewModel by lazy { ReaderViewModel(applicationContext) }
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
        updateBatteryOptimizationPrompt()
        setContent {
            BookReaderTheme {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            showBatteryOptimizationPrompt = false
            return
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(requestIntent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    companion object {
        private const val PREFS_NAME = "bookreader_preferences"
        private const val PROMPT_SHOWN_KEY = "battery_optimization_prompt_shown"
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PlaybackService.ACTION_PROGRESS)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(progressReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(progressReceiver, filter)
    }

    override fun onStop() {
        unregisterReceiver(progressReceiver)
        super.onStop()
    }
}
