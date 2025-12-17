package com.bbence.ticktasks.data
// Data Access Object for Task entity
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAll(): Flow<List<Task>>

    @Insert
    fun insert(task: Task): Long

    @Update
    fun update(task: Task): Int

    @Delete
    fun delete(task: Task): Int

    @Query("DELETE FROM tasks WHERE checked = 1 AND checkedAt <= :cutoff")
    fun deleteCheckedBefore(cutoff: Long): Int
}
