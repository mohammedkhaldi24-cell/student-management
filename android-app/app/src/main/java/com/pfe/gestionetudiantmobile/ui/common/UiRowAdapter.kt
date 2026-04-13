package com.pfe.gestionetudiantmobile.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pfe.gestionetudiantmobile.databinding.ItemUiRowBinding

class UiRowAdapter(
    private val onRowClick: (UiRow) -> Unit = {}
) : RecyclerView.Adapter<UiRowAdapter.RowViewHolder>() {

    private val items = mutableListOf<UiRow>()

    fun submitList(rows: List<UiRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = ItemUiRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(items[position], onRowClick)
    }

    override fun getItemCount(): Int = items.size

    class RowViewHolder(private val binding: ItemUiRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UiRow, onRowClick: (UiRow) -> Unit) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.tvBadge.text = item.badge
            binding.tvBadge.visibility = if (item.badge.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.root.setOnClickListener { onRowClick(item) }
        }
    }
}
