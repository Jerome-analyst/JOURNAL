package com.aibalance.tracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aibalance.tracker.R

class AiUsageAdapter : ListAdapter<AiUsageItem, AiUsageAdapter.AiUsageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiUsageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_usage, parent, false)
        return AiUsageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AiUsageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AiUsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvPackage: TextView = itemView.findViewById(R.id.tvPackage)
        private val tvMinutes: TextView = itemView.findViewById(R.id.tvMinutes)

        fun bind(item: AiUsageItem) {
            tvAppName.text = item.appName
            tvPackage.text = item.packageName
            tvMinutes.text = itemView.context.getString(R.string.minutes_value, item.minutes)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AiUsageItem>() {
        override fun areItemsTheSame(oldItem: AiUsageItem, newItem: AiUsageItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AiUsageItem, newItem: AiUsageItem): Boolean {
            return oldItem == newItem
        }
    }
}
