package com.bbence.ticktasks.ui
// ViewModel for managing tasks
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbence.ticktasks.data.Task
import com.bbence.ticktasks.repo.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = TaskRepository(application)

    val tasks = repo.getAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(title: String, description: String) {
        // Add a new task
        viewModelScope.launch {
            repo.insert(Task(title = title, description = description))
        }
    }

    fun delete(task: Task) {
        // Delete a task
        viewModelScope.launch {
            repo.delete(task)
        }
    }

    fun restore(task: Task) {
        // Re-insert a previously deleted task. We set id=0 so Room will generate a new id. (could be better implemented with a proper undo mechanism(lusta voltam))
        viewModelScope.launch {
            repo.insert(task.copy(id = 0))
        }
    }

    fun check(task: Task, checked: Boolean) {
        // Update the checked status of a task
        viewModelScope.launch {
            val now = if (checked) System.currentTimeMillis() else null
            repo.update(task.copy(checked = checked, checkedAt = now))
        }
    }

    fun cleanupBefore(cutoff: Long) {
        // Delete all tasks that were checked before the given cutoff timestamp
        viewModelScope.launch {
            repo.cleanupCheckedBefore(cutoff)
        }
    }
}
