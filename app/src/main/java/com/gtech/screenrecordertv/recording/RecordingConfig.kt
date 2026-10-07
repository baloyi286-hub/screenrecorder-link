package com.gtech.screenrecordertv.recording

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecordingConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
) : Parcelable
