package com.gtech.screenrecordertv

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gtech.screenrecordertv.databinding.ActivityMainBinding
import com.gtech.screenrecordertv.recording.RecordingConfig
import com.gtech.screenrecordertv.recording.ScreenRecordingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager
    private var recordingStartedAt = 0L

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (ScreenRecordingService.isRecording) {
                val elapsed = SystemClock.elapsedRealtime() - recordingStartedAt
                val seconds = elapsed / 1000
                binding.timerText.text = String.format(
                    "%02d:%02d:%02d",
                    seconds / 3600,
                    (seconds % 3600) / 60,
                    seconds % 60
                )
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                updateUi()
                return@registerForActivityResult
            }

            val serviceIntent = ScreenRecordingService.startIntent(
                context = this,
                resultCode = result.resultCode,
                resultData = result.data!!,
                config = selectedConfig()
            )

            ContextCompat.startForegroundService(this, serviceIntent)
            recordingStartedAt = SystemClock.elapsedRealtime()
            updateUi(recording = true)
            timerHandler.post(timerRunnable)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startCapture()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.qualitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("1080p", "720p")
        )

        binding.fpsSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("30 FPS", "60 FPS")
        )

        binding.recordButton.setOnClickListener {
            if (ScreenRecordingService.isRecording) stopRecording() else requestCapture()
        }

        binding.recordButton.requestFocus()
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun requestCapture() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startCapture()
    }

    private fun startCapture() {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopRecording() {
        startService(ScreenRecordingService.stopIntent(this))
        timerHandler.removeCallbacks(timerRunnable)
        binding.timerText.text = "00:00:00"
        updateUi(recording = false)
    }

    private fun selectedConfig(): RecordingConfig {
        val is1080p = binding.qualitySpinner.selectedItemPosition == 0
        val fps = if (binding.fpsSpinner.selectedItemPosition == 0) 30 else 60

        return if (is1080p) {
            RecordingConfig(
                width = 1920,
                height = 1080,
                fps = fps,
                bitrate = if (fps == 60) 16_000_000 else 10_000_000
            )
        } else {
            RecordingConfig(
                width = 1280,
                height = 720,
                fps = fps,
                bitrate = if (fps == 60) 10_000_000 else 6_000_000
            )
        }
    }

    private fun updateUi(recording: Boolean = ScreenRecordingService.isRecording) {
        binding.recordButton.text =
            if (recording) getString(R.string.stop_recording)
            else getString(R.string.start_recording)

        binding.statusText.text =
            if (recording) "Recording screen"
            else "Ready to record"

        binding.qualitySpinner.isEnabled = !recording
        binding.fpsSpinner.isEnabled = !recording
    }
}
