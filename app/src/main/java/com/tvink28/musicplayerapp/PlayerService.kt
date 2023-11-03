package com.tvink28.musicplayerapp

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat

class PlayerService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaPlayer: MediaPlayer
    private val localBinder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var currentSongIndex = 0

    private val updateSeekBar = object : Runnable {
        override fun run() {
            sendSeekBarUpdate()
            handler.postDelayed(this, 100)
        }
    }

    fun songList(): List<TrackInfo> {
        return listOf(
            TrackInfo(getString(R.string.skeler), getString(R.string.tokyo), R.raw.skeler_tokyo, R.drawable.img1),
            TrackInfo(getString(R.string.lxst_cxntury), getString(R.string.andromeda), R.raw.lxst_cxntury_andromeda, R.drawable.img2),
            TrackInfo(getString(R.string.airshade), getString(R.string.stay_close), R.raw.airshade_stay_close, R.drawable.img3),
            TrackInfo(getString(R.string.izzamuzzic), getString(R.string.room_for_one), R.raw.izzamuzzic_room_for_one, R.drawable.img4)
        )
    }

    fun getMediaPlayer(): MediaPlayer {
        return mediaPlayer
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer.create(this, songList()[0].resourceId)
        mediaPlayer.isLooping = false
        mediaPlayer.setOnCompletionListener { nextSong() }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            Constants.PLAY_PAUSE_FOREGROUND_ACTION -> {
                startForeground(1, showNotification())
                if (mediaPlayer.isPlaying) {
                    stopMusic()
                } else {
                    playMusic()
                }
            }

            Constants.NEXT_FOREGROUND_ACTION -> nextSong()
            Constants.PREV_FOREGROUND_ACTION -> prevSong()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder {
        mediaSession = MediaSessionCompat(baseContext, Constants.MUSIC)
        return localBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlayerService {
            return this@PlayerService
        }
    }

    private fun showNotification(): Notification {
        val playPauseAction =
            if (mediaPlayer.isPlaying) getString(R.string.pause) else getString(R.string.play)
        val playPauseIcon =
            if (mediaPlayer.isPlaying) R.drawable.pause_icon else R.drawable.play_icon
        val playPauseIntent = createActionIntent(Constants.PLAY_PAUSE_FOREGROUND_ACTION)
        val nextIntent = createActionIntent(Constants.NEXT_FOREGROUND_ACTION)
        val prevIntent = createActionIntent(Constants.PREV_FOREGROUND_ACTION)

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(songList()[currentSongIndex].trackName)
            .setContentText(songList()[currentSongIndex].artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(resources, songList()[currentSongIndex].img))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )
            .addAction(R.drawable.prev_icon_notifi, getString(R.string.prev), prevIntent)
            .addAction(playPauseIcon, playPauseAction, playPauseIntent)
            .addAction(R.drawable.next_icon_notifi, getString(R.string.next), nextIntent)

        return builder.build()
    }

    private fun updateNotificationInfo() {
        val notification = showNotification()
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, PlayerService::class.java)
        intent.action = action
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    private fun notifyMusicStateChanged(isPlaying: Boolean) {
        val intent = Intent(Constants.INTENT_MUSIC_STATE_CHANGED)
        intent.putExtra(Constants.IS_PLAYING, isPlaying)
        sendBroadcast(intent)
    }

    private fun sendSeekBarUpdate() {
        val currentPosition = mediaPlayer.currentPosition
        val totalDuration = mediaPlayer.duration
        val progress = (currentPosition * 100) / totalDuration

        val intent = Intent(Constants.INTENT_SEEKBAR_UPDATE)
        intent.putExtra(Constants.PROGRESS, progress)
        sendBroadcast(intent)
    }

    private fun playMusic() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            notifyMusicStateChanged(false)
            handler.post(updateSeekBar)
            updateNotificationInfo()
        }
    }

    private fun stopMusic() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            notifyMusicStateChanged(true)
            updateNotificationInfo()
        }
    }

    private fun nextSong() {
        currentSongIndex = (currentSongIndex + 1) % songList().size
        resetAndPlayNewSong()
    }

    private fun prevSong() {
        currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else songList().size - 1
        resetAndPlayNewSong()
    }

    fun getCurrentTrack(): TrackInfo? {
        return songList().getOrNull(currentSongIndex)
    }

    private fun resetAndPlayNewSong() {
        mediaPlayer.reset()
        mediaPlayer.setDataSource(
            this,
            Uri.parse("android.resource://$packageName/${songList()[currentSongIndex].resourceId}")
        )
        mediaPlayer.prepare()
        playMusic()
        updateNotificationInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMusic()
        handler.removeCallbacks(updateSeekBar)
        stopSelf()
        mediaPlayer.release()
    }
}