package com.bbence.ticktasks.repo

import android.content.Context
import com.bbence.ticktasks.data.AppDatabase
import com.bbence.ticktasks.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class TaskRepository(context: Context) {
    private val dao = AppDatabase.get(context).taskDao()

    fun getAll(): Flow<List<Task>> = dao.getAll()

    suspend fun insert(task: Task): Long = withContext(Dispatchers.IO) { dao.insert(task) }

    suspend fun update(task: Task) = withContext(Dispatchers.IO) { dao.update(task) }

    suspend fun delete(task: Task) = withContext(Dispatchers.IO) { dao.delete(task) }

    suspend fun cleanupCheckedBefore(cutoff: Long) = withContext(Dispatchers.IO) { dao.deleteCheckedBefore(cutoff) }
}
