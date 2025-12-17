package com.bbence.ticktasks


import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bbence.ticktasks.data.Task
import com.bbence.ticktasks.ui.TaskAdapter
import com.bbence.ticktasks.ui.TaskViewModel
import com.bbence.ticktasks.worker.CleanupWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val vm: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter
    private var inSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize activity
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = prefs.getInt("theme", 0)
        AppCompatDelegate.setDefaultNightMode(if (theme == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        adapter = TaskAdapter(
            onView = { task -> showViewDialog(task) },
            onDelete = { task -> confirmDeleteWithUndo(task) },
            onCheck = { task, checked -> vm.check(task, checked) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        lifecycleScope.launch {
            vm.tasks.collectLatest { list ->
                adapter.submitList(list)
            }
        }

        val btnAdd = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)
        val btnSettings = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_settings)
        val btnSelect = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_select)
        val btnMultiDelete = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_multi_delete)

        btnAdd.setOnClickListener { showCreateDialog() }
        btnSettings.setOnClickListener { showSettingsDialog() }

        btnSelect.setOnClickListener {
            // Toggle selection mode
            inSelectionMode = !inSelectionMode
            adapter.setSelectionMode(inSelectionMode)
            btnMultiDelete.visibility = if (inSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
            btnAdd.visibility = if (inSelectionMode) android.view.View.GONE else android.view.View.VISIBLE
            btnSettings.visibility = if (inSelectionMode) android.view.View.GONE else android.view.View.VISIBLE
            btnSelect.text = if (inSelectionMode) getString(R.string.done) else getString(R.string.select)
        }
        // Handle bulk deletion
        btnMultiDelete.setOnClickListener {
            val selected = adapter.getSelectedIds()
            if (selected.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_tasks_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Confirm bulk deletion
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_tasks_title))
                .setMessage(getString(R.string.delete_tasks_message, selected.size))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    val current = adapter.currentList
                    val toDelete = selected.mapNotNull { id -> current.find { it.id == id } }
                    toDelete.forEach { vm.delete(it) }
                    adapter.clearSelection()
                    adapter.setSelectionMode(false)
                    inSelectionMode = false
                    btnMultiDelete.visibility = android.view.View.GONE
                    btnAdd.visibility = android.view.View.VISIBLE
                    btnSettings.visibility = android.view.View.VISIBLE
                    btnSelect.text = getString(R.string.select)

                    // Show undo snackbar with single undo operation restoring all deleted tasks
                    val root = findViewById<android.view.View>(R.id.main)
                    Snackbar.make(root, getString(R.string.delete_tasks_title), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) {
                            toDelete.forEach { vm.restore(it) }
                        }
                        .show()
                }
                .show()
        }

        scheduleCleanup()
    }

    private fun confirmDeleteWithUndo(task: Task) {
        // Confirm deletion with undo option
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_task_title))
            .setMessage(getString(R.string.delete_task_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                vm.delete(task)
                val root = findViewById<android.view.View>(R.id.main)
                Snackbar.make(root, getString(R.string.delete_task_title), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) {
                        vm.restore(task)
                    }
                    .show()
            }
            .show()
    }

    private fun showCreateDialog() {
        // Show dialog to create a new task
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_create_task, null)
        val etTitle = v.findViewById<TextInputEditText>(R.id.et_title)
        val etDescription = v.findViewById<TextInputEditText>(R.id.et_description)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.create_task))
            .setView(v)
            .setPositiveButton(getString(R.string.create)) { d, _ ->
                val title = etTitle.text?.toString()?.trim().orEmpty()
                val desc = etDescription.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) {
                    Toast.makeText(this, getString(R.string.title_cannot_be_empty), Toast.LENGTH_SHORT).show()
                } else {
                    vm.add(title, desc)
                    d.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showViewDialog(task: Task) {
        // Show dialog to view/edit a task
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_view_task, null)
        val tvTitle = v.findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = v.findViewById<android.widget.TextView>(R.id.tv_description)
        val btnDelete = v.findViewById<android.widget.ImageButton>(R.id.dialog_delete)
        val cb = v.findViewById<android.widget.CheckBox>(R.id.dialog_checkbox)

        tvTitle.text = task.title
        tvDesc.text = task.description
        cb.isChecked = task.checked

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton(getString(R.string.close)) { d, _ -> d.dismiss() }
            .show()

        btnDelete.setOnClickListener {
            dialog.dismiss()
            confirmDeleteWithUndo(task)
        }

        cb.setOnCheckedChangeListener { _, isChecked ->
            vm.check(task, isChecked)
        }
    }

    private fun confirmDelete(task: Task) {
        // Confirm deletion of a single task
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_task_title))
            .setMessage(getString(R.string.delete_task_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ -> vm.delete(task) }
            .show()
    }

    private fun showSettingsDialog() {
        // Show settings dialog
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etDays = v.findViewById<TextInputEditText>(R.id.et_days)
        val rgTheme = v.findViewById<RadioGroup>(R.id.rg_theme)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val days = prefs.getInt("auto_delete_days", 3)
        etDays.setText(days.toString())
        val theme = prefs.getInt("theme", 0)
        if (theme == 1) rgTheme.check(R.id.rb_dark) else rgTheme.check(R.id.rb_light)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings))
            .setView(v)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val entered = etDays.text?.toString()?.toIntOrNull() ?: 3
                prefs.edit().putInt("auto_delete_days", entered).apply()
                val selected = rgTheme.checkedRadioButtonId
                val themeVal = if (selected == R.id.rb_dark) 1 else 0
                prefs.edit().putInt("theme", themeVal).apply()
                AppCompatDelegate.setDefaultNightMode(if (themeVal == 1) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                scheduleCleanup()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun scheduleCleanup() {
        // Schedule periodic cleanup work based on user settings
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val days = prefs.getInt("auto_delete_days", 3)
        // schedule periodic work once per day to perform cleanup using current setting
        val work = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("cleanup", ExistingPeriodicWorkPolicy.REPLACE, work)
    }
}