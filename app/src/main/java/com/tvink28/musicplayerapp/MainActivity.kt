package com.tvink28.musicplayerapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    private lateinit var playPauseBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var img: ImageView
    private lateinit var nameTrack: TextView
    private lateinit var artist: TextView
    private lateinit var seekBar: SeekBar

    private var playerService: PlayerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true

            val firstTrack = playerService?.songList()?.get(0)
            if (firstTrack != null) {
                nameTrack.text = firstTrack.trackName
                artist.text = firstTrack.artist
                img.setImageResource(firstTrack.img)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0
        supportActionBar?.setBackgroundDrawable(ColorDrawable(0))

        playPauseBtn = findViewById(R.id.playPauseBtn)
        nameTrack = findViewById(R.id.track_name)
        artist = findViewById(R.id.artist)
        img = findViewById(R.id.img)
        nextBtn = findViewById(R.id.nextBtn)
        prevBtn = findViewById(R.id.prevBtn)
        seekBar = findViewById(R.id.seekBar)

        setupButtonClickListeners()
        setupBroadcastReceiver()
        bindToService()
        seekBar.setOnSeekBarChangeListener(this)
    }

    private fun bindToService() {
        val serviceIntent = Intent(this, PlayerService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupButtonClickListeners() {
        playPauseBtn.setOnClickListener {
            val isPlaying = playerService?.getMediaPlayer()?.isPlaying == true
            startPlayerService(Constants.PLAY_PAUSE_FOREGROUND_ACTION)
            updatePlayPauseButton(!isPlaying)
        }

        nextBtn.setOnClickListener { startPlayerService(Constants.NEXT_FOREGROUND_ACTION) }
        prevBtn.setOnClickListener { startPlayerService(Constants.PREV_FOREGROUND_ACTION) }
    }

    private fun startPlayerService(action: String) {
        Intent(applicationContext, PlayerService::class.java).also {
            it.action = action
            startService(it)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupBroadcastReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Constants.INTENT_MUSIC_STATE_CHANGED) {
                    val isPlaying = intent.getBooleanExtra(Constants.IS_PLAYING, false)
                    updatePlayPauseButton(isPlaying)
                } else if (intent?.action == Constants.INTENT_SEEKBAR_UPDATE) {
                    val progress = intent.getIntExtra(Constants.PROGRESS, 0)
                    seekBar.progress = progress
                }
            }
        }

        val filter = IntentFilter(Constants.INTENT_MUSIC_STATE_CHANGED)
        filter.addAction(Constants.INTENT_SEEKBAR_UPDATE)
        registerReceiver(receiver, filter)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val drawableRes = if (isPlaying) R.drawable.play_icon else R.drawable.pause_icon
        playPauseBtn.setImageResource(drawableRes)

        playerService?.getCurrentTrack()?.let {
            img.setImageResource(it.img)
            nameTrack.text = it.trackName
            artist.text = it.artist
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val newPosition = progress * (playerService?.getMediaPlayer()?.duration ?: 0) / 100
            playerService?.getMediaPlayer()?.seekTo(newPosition)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, PlayerService::class.java)
        stopService(serviceIntent)
        unbindService(serviceConnection)
    }
}