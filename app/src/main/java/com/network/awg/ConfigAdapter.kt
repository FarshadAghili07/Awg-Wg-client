package com.network.awg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.network.awg.data.ConfigEntity

class ConfigAdapter(
    private val onSelect: (ConfigEntity) -> Unit,
    private val onDelete: (ConfigEntity) -> Unit
) : ListAdapter<ConfigEntity, ConfigAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioSelect: RadioButton = view.findViewById(R.id.radioSelect)
        val textName: TextView = view.findViewById(R.id.textConfigName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_config, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.textName.text = item.name
        holder.radioSelect.isChecked = item.isSelected

        holder.itemView.setOnClickListener { onSelect(item) }
        holder.radioSelect.setOnClickListener { onSelect(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ConfigEntity>() {
        override fun areItemsTheSame(oldItem: ConfigEntity, newItem: ConfigEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ConfigEntity, newItem: ConfigEntity) = oldItem == newItem
    }
}
