package com.example.musicplayer

import android.annotation.TargetApi
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import com.example.musicplayer.MusicPlayerService
import java.io.IOException
import java.util.*

class MusicPlayerService : Service(), OnCompletionListener, OnPreparedListener, OnErrorListener, OnSeekCompleteListener, OnInfoListener, OnBufferingUpdateListener, OnAudioFocusChangeListener {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: String? = null
    private var resume = 0
    private var audioManager: AudioManager? = null
    private val iBinder: IBinder = MyLocalBinder()
    override fun onBind(intent: Intent): IBinder? {
        return iBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //audio file is passed to the service
        try {
            mediaFile = Objects.requireNonNull(intent.extras)?.getString("media")
        } catch (e: NullPointerException) {
            stopSelf()
        }
        if (!requestAudioFocus()) stopSelf()
        if (!TextUtils.isEmpty(mediaFile)) initMusicPlayer()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initMusicPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)

        //reset so that media player is pointing to another source.
        mediaPlayer!!.reset()
        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        try {
            mediaPlayer!!.setDataSource(mediaFile)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }
        mediaPlayer!!.prepareAsync()
    }

    private fun playMusic() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    private fun stopMusic() {
        if (mediaPlayer == null) return
        if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
    }

    private fun pauseMusic() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resume = mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMusic() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resume)
            mediaPlayer!!.start()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        //when audio focus of the system changed.
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                //resume playing
                if (mediaPlayer == null) initMusicPlayer() else playMusic()
                mediaPlayer!!.setVolume(LEFT_VOLUME, RIGHT_VOLUME)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                //lost focus. stop playing and release media player
                stopMusic()
                mediaPlayer!!.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->                 //lost focus for short time, stop media. don't release media player as it's likely to resume
                pauseMusic()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->                 //lost focus for short time but keep playing with lower volume
                if (mediaPlayer!!.isPlaying) mediaPlayer!!.setVolume(LOWER_VOLUME, LOWER_VOLUME)
        }
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        //buffering status of the media being streamed over network.
    }

    override fun onCompletion(mp: MediaPlayer) {
        //when playback of media has completed.
        stopMusic()
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) true else false

        //couldn't gain focus
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager!!.abandonAudioFocus(this)
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //when there's an error during asynchronous operation.
        when (what) {
            MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK $extra")
            MEDIA_ERROR_SERVER_DIED -> Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED $extra")
            MEDIA_ERROR_UNKNOWN -> Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN $extra")
        }
        return false
    }

    override fun onInfo(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //to communicate some information.
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        //when media is ready for playback.
        playMusic()
    }

    override fun onSeekComplete(mp: MediaPlayer) {
        //indicating the completion of seek operation.
    }

    inner class MyLocalBinder : Binder() {
        val service: MusicPlayerService
            get() = this@MusicPlayerService
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMusic()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
    }

    companion object {
        private const val LEFT_VOLUME = 1.0f
        private const val RIGHT_VOLUME = 1.0f
        private const val LOWER_VOLUME = 0.1f
    }
}