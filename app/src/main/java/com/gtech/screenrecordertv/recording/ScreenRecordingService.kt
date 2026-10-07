package com.gtech.screenrecordertv.recording

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.gtech.screenrecordertv.MainActivity
import com.gtech.screenrecordertv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (isRecording) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = getIntentExtra(intent, EXTRA_RESULT_DATA)
        val config = getParcelableExtra<RecordingConfig>(intent, EXTRA_CONFIG) ?: return

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return
        }

        startForegroundCompat(buildNotification())

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        try {
            val recorder = createRecorder(config)
            mediaRecorder = recorder

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorderTV",
                config.width,
                config.height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()
            isRecording = true
        } catch (_: Exception) {
            cleanup(deleteIncomplete = true)
            stopSelf()
        }
    }

    private fun createRecorder(config: RecordingConfig): MediaRecorder {
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()

        val fileName =
            "TV_Record_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecorderTV")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        outputUri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Unable to create output recording")

        val descriptor = contentResolver.openFileDescriptor(outputUri!!, "w")
            ?: throw IllegalStateException("Unable to open output recording")

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(descriptor.fileDescriptor)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(config.width, config.height)
            setVideoFrameRate(config.fps)
            setVideoEncodingBitRate(config.bitrate)
            prepare()
        }

        descriptor.close()
        return recorder
    }

    private fun stopRecording() {
        cleanup(deleteIncomplete = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup(deleteIncomplete: Boolean) {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }

        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        outputUri?.let { uri ->
            if (deleteIncomplete) {
                contentResolver.delete(uri, null, null)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, values, null, null)
            }
        }

        outputUri = null
        isRecording = false
    }

    private fun buildNotification(): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launcherPendingIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Screen Recorder TV")
            .setContentText("Screen recording is active")
            .setOngoing(true)
            .setContentIntent(launcherPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screen recording",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        if (isRecording) cleanup(deleteIncomplete = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun getIntentExtra(intent: Intent, key: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            intent.getParcelableExtra(key)
        }

    @Suppress("DEPRECATION")
    private inline fun <reified T> getParcelableExtra(intent: Intent, key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            intent.getParcelableExtra(key)
        }

    companion object {
        @Volatile
        var isRecording: Boolean = false
            private set

        private const val ACTION_START = "com.gtech.screenrecordertv.action.START"
        private const val ACTION_STOP = "com.gtech.screenrecordertv.action.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_CONFIG = "config"
        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            config: RecordingConfig
        ): Intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_CONFIG, config)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
