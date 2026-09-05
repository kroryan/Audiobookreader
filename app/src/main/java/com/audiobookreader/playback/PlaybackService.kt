package com.audiobookreader.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.audiobookreader.data.ProgressRepository
import com.audiobookreader.data.ReadingProgress

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var progressRepository: ProgressRepository
    private val handler = Handler(Looper.getMainLooper())
    private var bookId: String? = null
    private var itemCount: Int = 0
    private val progressTask = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, AUTO_SAVE_INTERVAL_MS)
        }
    }
    private val uiProgressTask = object : Runnable {
        override fun run() {
            publishProgress()
            handler.postDelayed(this, UI_PROGRESS_INTERVAL_MS)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) saveProgress()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveProgress()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).build()
        progressRepository = ProgressRepository(this)
        handler.post(progressTask)
        handler.post(uiProgressTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
            val paths = intent.getStringArrayExtra(EXTRA_PATHS).orEmpty()
            val startAt = intent.getIntExtra(EXTRA_START, 0)
            bookId = intent.getStringExtra(EXTRA_BOOK_ID)
            itemCount = intent.getIntExtra(EXTRA_ITEM_COUNT, paths.size)
            val startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
            if (paths.isNotEmpty()) {
                player.setMediaItems(paths.map { MediaItem.fromUri(Uri.fromFile(java.io.File(it))) }, startAt, startPosition)
                player.prepare()
                player.play()
            }
            }
            ACTION_APPEND -> {
                val paths = intent.getStringArrayExtra(EXTRA_PATHS).orEmpty()
                val appendBookId = intent.getStringExtra(EXTRA_BOOK_ID)
                if (appendBookId == bookId && paths.isNotEmpty()) {
                    player.addMediaItems(paths.map { MediaItem.fromUri(Uri.fromFile(java.io.File(it))) })
                    if (player.playbackState == Player.STATE_ENDED) player.play()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        saveProgress()
        handler.removeCallbacks(progressTask)
        handler.removeCallbacks(uiProgressTask)
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveProgress()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val AUTO_SAVE_INTERVAL_MS = 20_000L
        private const val UI_PROGRESS_INTERVAL_MS = 1_000L
        const val ACTION_PROGRESS = "com.audiobookreader.action.PROGRESS"
        private const val ACTION_PLAY = "com.audiobookreader.action.PLAY_BOOK"
        private const val ACTION_APPEND = "com.audiobookreader.action.APPEND_BOOK_AUDIO"
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_START = "start"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_ITEM_COUNT = "itemCount"

        fun play(context: Context, files: List<String>, bookId: String, startAt: Int = 0, positionMs: Long = 0L, itemCount: Int = files.size) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_PATHS, files.toTypedArray())
                .putExtra(EXTRA_START, startAt)
                .putExtra(EXTRA_POSITION, positionMs)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_ITEM_COUNT, itemCount)
            ContextCompat.startForegroundService(context, intent)
        }

        fun append(context: Context, files: List<String>, bookId: String) {
            if (files.isEmpty()) return
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_APPEND)
                .putExtra(EXTRA_PATHS, files.toTypedArray())
                .putExtra(EXTRA_BOOK_ID, bookId)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun saveProgress() {
        val id = bookId ?: return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0L)
        progressRepository.save(ReadingProgress(id, index, position, itemCount))
        publishProgress()
    }

    private fun publishProgress() {
        val id = bookId ?: return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName)
            .putExtra(EXTRA_BOOK_ID, id)
            .putExtra(EXTRA_START, index)
            .putExtra(EXTRA_POSITION, player.currentPosition.coerceAtLeast(0L))
            .putExtra(EXTRA_ITEM_COUNT, itemCount))
    }

}
