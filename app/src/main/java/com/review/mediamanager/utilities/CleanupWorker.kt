package com.review.mediamanager.utilities

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Daily, battery-friendly cleanup of temporary files: decrypted share copies and
 * interrupted import leftovers. Runs only when the phone is idle-ish (WorkManager decides).
 */
class CleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        listOf(File(applicationContext.cacheDir, "share"), File(applicationContext.cacheDir, "import")).forEach { dir ->
            dir.listFiles()?.filter { now - it.lastModified() > TimeUnit.HOURS.toMillis(2) }?.forEach { it.deleteRecursively() }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("review_cleanup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
