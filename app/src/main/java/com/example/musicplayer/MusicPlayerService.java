package com.example.musicplayer;


import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;

public class MusicPlayerService extends Service implements
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private MediaPlayer mediaPlayer;
    private String mediaFile;
    private int resume;
    private AudioManager audioManager;
    private final IBinder iBinder = new MyLocalBinder();
    private static final float LEFT_VOLUME = 1.0f;
    private static final float RIGHT_VOLUME = 1.0f;
    private static final float LOWER_VOLUME = 0.1f;

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //audio file is passed to the service
        try {
            mediaFile = Objects.requireNonNull(intent.getExtras()).getString("media");
        } catch (NullPointerException e) {
            stopSelf();
        }

        if (requestAudioFocus() == false)
            stopSelf();

        if (!TextUtils.isEmpty(mediaFile))
            initMusicPlayer();

        return super.onStartCommand(intent, flags, startId);
    }

    private void initMusicPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        //reset so that media player is pointing to another source.
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            mediaPlayer.setDataSource(mediaFile);
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }

        mediaPlayer.prepareAsync();
    }

    private void playMusic() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void stopMusic() {
        if (mediaPlayer == null)
            return;
        if (mediaPlayer.isPlaying())
            mediaPlayer.stop();
    }

    private void pauseMusic() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resume = mediaPlayer.getCurrentPosition();
        }
    }

    private void resumeMusic() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resume);
            mediaPlayer.start();
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        //when audio focus of the system changed.
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                //resume playing
                if (mediaPlayer == null)
                    initMusicPlayer();
                else playMusic();
                mediaPlayer.setVolume(LEFT_VOLUME, RIGHT_VOLUME);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                //lost focus. stop playing and release media player
                stopMusic();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                //lost focus for short time, stop media. don't release media player as it's likely to resume
                pauseMusic();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                //lost focus for short time but keep playing with lower volume
                if (mediaPlayer.isPlaying())
                    mediaPlayer.setVolume(LOWER_VOLUME, LOWER_VOLUME);
                break;

        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //buffering status of the media being streamed over network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //when playback of media has completed.
        stopMusic();
        stopSelf();
    }

    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            //focus gained
            return true;

        //couldn't gain focus
        return false;
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //when there's an error during asynchronous operation.
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //to communicate some information.
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //when media is ready for playback.
        playMusic();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //indicating the completion of seek operation.
    }

    public class MyLocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMusic();
            mediaPlayer.release();
        }
        removeAudioFocus();
    }
}
