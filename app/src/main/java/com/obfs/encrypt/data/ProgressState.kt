package com.obfs.encrypt.data

import android.os.Parcel
import android.os.Parcelable

class ProgressState(
    val progress: Float = 0f,
    val etaSeconds: Long = -1L,
    val speedBytesPerSecond: Long = 0L,
    val currentFile: String? = null,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isIndeterminate: Boolean = false,
    val isPaused: Boolean = false
) : Parcelable {
    val speedFormatted: String
        get() = formatSpeed(speedBytesPerSecond)

    val etaFormatted: String
        get() = formatEta(etaSeconds)

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    companion object {
        fun formatSpeed(bytesPerSecond: Long): String {
            if (bytesPerSecond <= 0) return "0 B/s"
            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
            var speed = bytesPerSecond.toDouble()
            var unitIndex = 0
            while (speed >= 1024 && unitIndex < units.size - 1) {
                speed /= 1024
                unitIndex++
            }
            return "%.1f %s".format(speed, units[unitIndex])
        }

        fun formatEta(seconds: Long): String {
            if (seconds < 0) return "--:--"
            if (seconds == 0L) return "0s"
            
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> "%dh %dm %ds".format(hours, minutes, secs)
                minutes > 0 -> "%dm %ds".format(minutes, secs)
                else -> "%ds".format(secs)
            }
        }
    }

    constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(progress)
        parcel.writeLong(etaSeconds)
        parcel.writeLong(speedBytesPerSecond)
        parcel.writeString(currentFile)
        parcel.writeInt(totalFiles)
        parcel.writeInt(currentFileIndex)
        parcel.writeLong(processedBytes)
        parcel.writeLong(totalBytes)
        parcel.writeByte(if (isIndeterminate) 1 else 0)
        parcel.writeByte(if (isPaused) 1 else 0)
    }

    override fun describeContents(): Int = 0

    fun copy(
        progress: Float = this.progress,
        etaSeconds: Long = this.etaSeconds,
        speedBytesPerSecond: Long = this.speedBytesPerSecond,
        currentFile: String? = this.currentFile,
        totalFiles: Int = this.totalFiles,
        currentFileIndex: Int = this.currentFileIndex,
        processedBytes: Long = this.processedBytes,
        totalBytes: Long = this.totalBytes,
        isIndeterminate: Boolean = this.isIndeterminate,
        isPaused: Boolean = this.isPaused
    ): ProgressState {
        return ProgressState(
            progress,
            etaSeconds,
            speedBytesPerSecond,
            currentFile,
            totalFiles,
            currentFileIndex,
            processedBytes,
            totalBytes,
            isIndeterminate,
            isPaused
        )
    }
}
