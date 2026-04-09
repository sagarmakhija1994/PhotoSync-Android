package com.sagar.prosync.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "photo_sync_channel"
    private val notificationId = 101

    override suspend fun doWork(): Result {
        val syncPhotos = inputData.getBoolean("SYNC_PHOTOS", true)
        val syncVideos = inputData.getBoolean("SYNC_VIDEOS", false)
        val repo = UploadRepository(applicationContext)

        return try {
            val items = MediaScanner.scan(applicationContext, syncPhotos, syncVideos)
            val total = items.size

            if (total == 0) return Result.success()

            // Initialize Foreground Service with "Scanning..." state
            setForeground(createForegroundInfo(0, total, "Scanning for new files..."))

            // Hand off to the Batch Processor
            repo.processSync(items) { current, newTotal ->
                // This callback fires after every successful upload
                if (newTotal == 0) {
                    // Everything is already backed up!
                    setForeground(createForegroundInfo(total, total, "Everything is up to date!"))
                } else {
                    setForeground(createForegroundInfo(current, newTotal, "Uploading $current of $newTotal..."))
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private fun createForegroundInfo(current: Int, total: Int, message: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Photo Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("PhotoSync Backup")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, current, false)
            .setContentText(message)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}