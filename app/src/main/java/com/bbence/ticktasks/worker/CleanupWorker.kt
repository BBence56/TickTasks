package com.bbence.ticktasks.worker
// Worker for cleaning up old checked tasks
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bbence.ticktasks.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class CleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val days = prefs.getInt("auto_delete_days", 3)
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            AppDatabase.get(applicationContext).taskDao().deleteCheckedBefore(cutoff)
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            androidx.work.ListenableWorker.Result.failure()
        }
    }
}
