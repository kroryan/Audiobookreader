package com.audiobookreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.audiobookreader.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private val readerViewModel by lazy { ReaderViewModel(applicationContext) }
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
        setContent {
            AudiobookReaderApp(readerViewModel)
        }
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
