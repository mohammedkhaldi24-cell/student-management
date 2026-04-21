package com.pfe.gestionetudiantmobile.ui.common

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.databinding.ItemUiRowBinding
import java.util.Locale

class UiRowAdapter(
    private val onRowClick: (UiRow) -> Unit = {}
) : RecyclerView.Adapter<UiRowAdapter.RowViewHolder>() {

    private val items = mutableListOf<UiRow>()

    init {
        setHasStableIds(true)
    }

    fun submitList(rows: List<UiRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return item.id ?: item.stableHash()
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
            binding.tvIcon.text = item.icon ?: iconFor(item)
            binding.tvBadge.background = badgeBackground(item.badge)
            binding.tvBadge.setTextColor(badgeTextColor(item.badge))
            binding.root.contentDescription = buildString {
                append(item.title)
                if (item.subtitle.isNotBlank()) append(". ${item.subtitle}")
                if (item.badge.isNotBlank()) append(". Statut: ${item.badge}")
            }
            binding.root.alpha = if (item.id == null) 0.86f else 1f
            binding.root.setOnClickListener {
                if (item.id != null) {
                    onRowClick(item)
                }
            }
        }

        private fun iconFor(item: UiRow): String {
            val text = "${item.title} ${item.subtitle} ${item.badge}".lowercase(Locale.ROOT)
            return when {
                "absence" in text || "justifie" in text -> "ABS"
                "note" in text || "cc:" in text || "examen" in text -> "20"
                "devoir" in text || "assignment" in text || "deadline" in text -> "DEV"
                "cours" in text || "module" in text -> "CRS"
                "annonce" in text -> "ANN"
                "profil" in text -> "PRO"
                "historique" in text || "timeline" in text -> "HIS"
                "emploi" in text || "salle" in text -> "EDT"
                "notification" in text -> "NOT"
                else -> "ETU"
            }
        }

        private fun badgeBackground(badge: String): GradientDrawable {
            val context = binding.root.context
            val normalized = badge.lowercase(Locale.ROOT)
            val color = when {
                normalized.contains("late") ||
                    normalized.contains("retard") ||
                    normalized.contains("overdue") ||
                    normalized.contains("non") ||
                    normalized.contains("a traiter") -> R.color.softRed
                normalized.contains("ok") ||
                    normalized.contains("valide") ||
                    normalized.contains("justifiee") ||
                    normalized.contains("graded") ||
                    normalized.contains("reviewed") ||
                    normalized.contains("on time") -> R.color.softGreen
                normalized.contains("pending") ||
                    normalized.contains("brouillon") ||
                    normalized.contains("attente") -> R.color.softAmber
                else -> R.color.primaryContainer
            }
            return GradientDrawable().apply {
                cornerRadius = 999f
                setColor(ContextCompat.getColor(context, color))
            }
        }

        private fun badgeTextColor(badge: String): Int {
            val context = binding.root.context
            val normalized = badge.lowercase(Locale.ROOT)
            val color = when {
                normalized.contains("late") ||
                    normalized.contains("retard") ||
                    normalized.contains("overdue") ||
                    normalized.contains("non") ||
                    normalized.contains("a traiter") -> R.color.statRed
                normalized.contains("ok") ||
                    normalized.contains("valide") ||
                    normalized.contains("justifiee") ||
                    normalized.contains("graded") ||
                    normalized.contains("reviewed") ||
                    normalized.contains("on time") -> R.color.statGreen
                normalized.contains("pending") ||
                    normalized.contains("brouillon") ||
                    normalized.contains("attente") -> R.color.statAmber
                else -> R.color.primary
            }
            return ContextCompat.getColor(context, color)
        }
    }

    private fun UiRow.stableHash(): Long {
        return "${title}|${subtitle}|${badge}|${icon.orEmpty()}".hashCode().toLong()
    }
}
