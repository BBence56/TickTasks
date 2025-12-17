package com.bbence.ticktasks.ui
// Adapter for displaying tasks in a RecyclerView
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bbence.ticktasks.R
import com.bbence.ticktasks.data.Task

class TaskAdapter(
    private val onView: (Task) -> Unit,
    private val onDelete: (Task) -> Unit,
    private val onCheck: (Task, Boolean) -> Unit
) : ListAdapter<Task, TaskAdapter.VH>(DIFF) {

    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        // Enable or disable selection mode
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
    }

    fun toggleSelect(id: Long) {
        // Toggle selection state for a task by id
        val idx = currentList.indexOfFirst { it.id == id }
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()
    fun clearSelection() { selectedIds.clear(); notifyDataSetChanged() }

    companion object {
        // DiffUtil for efficient list updates
        val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        // ViewHolder for task item views
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val root: View = view.findViewById(R.id.item_root)
        val divider: View = view.findViewById(R.id.divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // Inflate the task item layout
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        // Bind data to the ViewHolder
        val task = getItem(position)
        holder.tvTitle.text = task.title
        holder.checkbox.isChecked = task.checked

        val isSelected = selectedIds.contains(task.id)

        if (selectionMode) {
            // Show full-item border: dotted for unselected, solid for selected
            holder.divider.visibility = View.GONE
            val bgRes = if (isSelected) R.drawable.solid_border else R.drawable.dotted_border
            holder.root.setBackgroundResource(bgRes)
            holder.root.animate().alpha(1f).setDuration(180).start()
        } else {
            // Normal mode: remove border
            holder.divider.visibility = View.GONE
            holder.root.animate().alpha(1f).setDuration(180).withEndAction { holder.root.background = null }.start()
        }

        // Keep consistent padding and no scale changes
        holder.root.setPadding(12, 12, 12, 12)

        // Click the whole item to view, but in selection mode clicking toggles selection
        holder.root.setOnClickListener {
            if (selectionMode) {
                toggleSelect(task.id)
            } else {
                onView(task)
            }
        }

        holder.btnDelete.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onDelete(task)
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (task.checked != isChecked) onCheck(task, isChecked)
        }
    }
}
