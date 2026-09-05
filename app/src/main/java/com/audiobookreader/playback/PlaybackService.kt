package com.audiobookreader.playback

import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.content.pm.ServiceInfo
import com.audiobookreader.R
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
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(launchActivityIntent())
            .setCustomLayout(listOf(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                    .setIconResId(R.drawable.ic_skip_back)
                    .setDisplayName("Back 15 seconds")
                    .build(),
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                    .setIconResId(R.drawable.ic_skip_forward)
                    .setDisplayName("Forward 30 seconds")
                    .build(),
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_STOP)
                    .setIconResId(R.drawable.ic_stop)
                    .setDisplayName("Stop")
                    .build(),
            ))
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_bookreader) }
        )
        createPlaybackNotification()
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
            ACTION_STOP -> {
                saveProgress()
                player.stop()
                stopSelf()
            }
            ACTION_SEEK_BACK -> player.seekBack()
            ACTION_SEEK_FORWARD -> player.seekForward()
            ACTION_SEEK_TO -> {
                val index = intent.getIntExtra(EXTRA_SEEK_INDEX, -1)
                val position = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L).coerceAtLeast(0L)
                if (index in 0 until player.mediaItemCount) {
                    player.seekTo(index, position)
                    player.play()
                    publishProgress(index, position)
                } else if (index == player.currentMediaItemIndex && index >= 0) {
                    player.seekTo(position)
                    player.play()
                    publishProgress(index, position)
                }
            }
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_RESET -> {
                val resetBookId = intent.getStringExtra(EXTRA_BOOK_ID)
                val resetItemCount = intent.getIntExtra(EXTRA_ITEM_COUNT, 1).coerceAtLeast(1)
                val wasPlayingBook = resetBookId != null && resetBookId == bookId
                if (wasPlayingBook) {
                    // Move the player first so the shutdown save cannot restore the old position.
                    player.seekTo(0, 0L)
                    player.stop()
                }
                if (!resetBookId.isNullOrBlank()) {
                    progressRepository.save(ReadingProgress(resetBookId, 0, 0L, resetItemCount))
                    if (wasPlayingBook) {
                        bookId = resetBookId
                        itemCount = resetItemCount
                        publishProgress(0, 0L)
                    }
                }
                if (wasPlayingBook || bookId == null) stopSelf()
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
        private const val ACTION_STOP = "com.audiobookreader.action.STOP_BOOK"
        private const val ACTION_RESET = "com.audiobookreader.action.RESET_BOOK"
        private const val ACTION_SEEK_BACK = "com.audiobookreader.action.SEEK_BACK"
        private const val ACTION_SEEK_FORWARD = "com.audiobookreader.action.SEEK_FORWARD"
        private const val ACTION_SEEK_TO = "com.audiobookreader.action.SEEK_TO"
        private const val ACTION_TOGGLE = "com.audiobookreader.action.TOGGLE_PLAYBACK"
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_START = "start"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_ITEM_COUNT = "itemCount"
        private const val EXTRA_SEEK_INDEX = "seekIndex"
        private const val EXTRA_SEEK_POSITION = "seekPosition"
        private const val CHANNEL_ID = "bookreader-playback"
        private const val NOTIFICATION_ID = 4101
        private const val SEEK_BACK_MS = 15_000L
        private const val SEEK_FORWARD_MS = 30_000L

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

        fun stop(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }

        fun reset(context: Context, bookId: String, itemCount: Int) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_RESET)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_ITEM_COUNT, itemCount)
            ContextCompat.startForegroundService(context, intent)
        }

        fun seekTo(context: Context, index: Int, positionMs: Long = 0L) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SEEK_TO)
                .putExtra(EXTRA_SEEK_INDEX, index)
                .putExtra(EXTRA_SEEK_POSITION, positionMs)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun saveProgress() {
        val id = bookId ?: return
        if (player.playbackState == Player.STATE_ENDED && player.mediaItemCount >= itemCount && itemCount > 0) {
            progressRepository.save(ReadingProgress(id, itemCount, 0L, itemCount))
            publishProgress(itemCount, 0L)
            return
        }
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0L)
        progressRepository.save(ReadingProgress(id, index, position, itemCount))
        publishProgress()
    }

    private fun publishProgress(indexOverride: Int? = null, positionOverride: Long? = null) {
        val id = bookId ?: return
        val index = indexOverride ?: player.currentMediaItemIndex.coerceAtLeast(0)
        val position = positionOverride ?: player.currentPosition.coerceAtLeast(0L)
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName)
            .putExtra(EXTRA_BOOK_ID, id)
            .putExtra(EXTRA_START, index)
            .putExtra(EXTRA_POSITION, position)
            .putExtra(EXTRA_ITEM_COUNT, itemCount))
    }

    private fun createPlaybackNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reproducción", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bookreader)
            .setContentTitle("BookReader")
            .setContentText("Audiobook playback")
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_skip_back, "Back 15 seconds", commandIntent(ACTION_SEEK_BACK, 1))
            .addAction(android.R.drawable.ic_media_pause, "Pause", commandIntent(ACTION_TOGGLE, 2))
            .addAction(R.drawable.ic_skip_forward, "Forward 30 seconds", commandIntent(ACTION_SEEK_FORWARD, 3))
            .addAction(R.drawable.ic_stop, "Stop", commandIntent(ACTION_STOP, 4))
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2, 3)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(commandIntent(ACTION_STOP, 4))
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun launchActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, com.audiobookreader.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun commandIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

}
