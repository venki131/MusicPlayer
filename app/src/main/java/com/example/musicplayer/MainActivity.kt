package com.example.musicplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayer.MusicPlayerService.MyLocalBinder
import java.util.*

class MainActivity : AppCompatActivity() {
    private var musicPlayerService: MusicPlayerService? = null
    private var serviceBound = false
    private var audioList: MutableList<Audio>? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadAudio()
        if (audioList!!.size > 0) playAudio(audioList!![0].data) else playAudio("http://mysound.ge/track/364/lady-gaga-amp-bradley-cooper-shallow")
    }

    //binding client to audio player service
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            //we've bound to local service, cast the IBinder and get LocalService instance
            val localBinder = service as MyLocalBinder
            musicPlayerService = localBinder.service
            serviceBound = true
            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    private fun playAudio(media: String) {
        if (!serviceBound) {
            val intent = Intent(this, MusicPlayerService::class.java)
            intent.putExtra("media", media)
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            //service is active
            musicPlayerService!!.stopSelf()
        }
    }

    /**
     * retrieve audio files from external storage
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("Recycle")
    private fun loadAudio() {
        val contentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!=0"
        val sortOrder = MediaStore.Audio.Media.TITLE + "ASC"
        val cursor = contentResolver.query(uri, null, selection, null, sortOrder)
        if (cursor != null && cursor.count > 0) {
            audioList = ArrayList()
            while (cursor.moveToNext()) {
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))

                //save to audioList
                (audioList as ArrayList<Audio>).add(Audio(data, title, album, artist))
            }
        }
        cursor!!.close()
        Toast.makeText(this, "number of audio files = " + audioList!!.size, Toast.LENGTH_LONG).show()
    }
}