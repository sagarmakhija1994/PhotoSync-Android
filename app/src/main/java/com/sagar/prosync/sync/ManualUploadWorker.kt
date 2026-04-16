package com.sagar.prosync.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.ui.processAndUploadUri
import kotlinx.coroutines.delay
import java.io.File

class ManualUploadWorker(
    appContext: Context, // FIX: Use appContext so we don't accidentally shadow the base Context!
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val NOTIFICATION_ID = 888
    private val CHANNEL_ID = "ManualUploadChannel"

    override suspend fun doWork(): Result {
        val queueFilePath = inputData.getString("QUEUE_FILE_PATH") ?: return Result.failure()
        val queueFile = File(queueFilePath)

        if (!queueFile.exists()) return Result.failure()

        val uriStrings = queueFile.readLines()
        val total = uriStrings.size
        var successCount = 0

        val api = ApiClient.create(applicationContext).create(PhotoApi::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Manual Uploads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        // FIX: Wrap this in a try-catch. Android 14 can be overly aggressive about blocking foreground services!
        try {
            setForeground(createForegroundInfo(0, total))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (i in uriStrings.indices) {
            if (uriStrings[i].isBlank()) continue

            val uri = Uri.parse(uriStrings[i])

            setProgress(workDataOf("PROGRESS" to i, "TOTAL" to total))
            notificationManager.notify(NOTIFICATION_ID, createForegroundInfo(i, total).notification)

            try {
                // Use applicationContext here!
                val success = processAndUploadUri(applicationContext, uri, api)
                if (success) successCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setProgress(workDataOf("PROGRESS" to total, "TOTAL" to total))

        if (queueFile.exists()) {
            queueFile.delete()
        }

        delay(500)

        return Result.success(workDataOf("SUCCESS_COUNT" to successCount))
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading Media")
            .setContentText("Uploading file $progress of $total to server...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(total, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}