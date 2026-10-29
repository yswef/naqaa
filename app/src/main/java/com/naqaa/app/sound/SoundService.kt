package com.naqaa.app.sound

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import com.naqaa.app.notify.Notices
import com.naqaa.app.util.CrashLog

/**
 * Plays one bundled recording and stops itself when it ends.
 *
 * It runs as a foreground media service because a plain receiver is allowed to work for a
 * few seconds only, which is shorter than a call to prayer. The notification has a stop
 * action. Any failure ends the service quietly; the failure is written to the crash log
 * file so it can be read later.
 */
class SoundService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // The system requires the notification within a few seconds of the start request.
        val notification = Notices.soundPlaying(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notices.ID_SOUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notices.ID_SOUND, notification)
        }
        val file = intent?.getStringExtra(EXTRA_FILE)
        if (file == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val loud = intent.getBooleanExtra(EXTRA_LOUD, false)
        release()
        try {
            val descriptor = assets.openFd("${Sounds.DIRECTORY}/$file")
            val created = MediaPlayer()
            player = created
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (loud) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            created.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            descriptor.close()
            created.setOnCompletionListener { stopSelf() }
            created.setOnErrorListener { _, _, _ ->
                stopSelf()
                true
            }
            created.prepare()
            created.start()
        } catch (error: Exception) {
            CrashLog.note(this, "sound $file", error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun release() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_LOUD = "loud"
        const val ACTION_STOP = "com.naqaa.app.action.STOP_SOUND"
    }
}
