package com.pfe.gestionetudiantmobile.ui.common

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding

class FeatureStateController(
    private val binding: ActivityFeatureListBinding,
    private val onRetry: () -> Unit
) {
    init {
        binding.btnStateRetry.setOnClickListener { onRetry() }
    }

    fun showLoading(message: String = "Chargement des donnees...") {
        binding.swipeLayout.isRefreshing = false
        binding.recyclerView.visibility = View.INVISIBLE
        binding.layoutState.visibility = View.VISIBLE
        binding.progressState.visibility = View.VISIBLE
        binding.tvStateIcon.visibility = View.GONE
        binding.btnStateRetry.visibility = View.GONE
        binding.tvStateTitle.text = "Chargement"
        binding.tvStateMessage.text = message
    }

    fun showRows(
        adapter: UiRowAdapter,
        rows: List<UiRow>,
        emptyTitle: String,
        emptyMessage: String,
        emptyIcon: String = "VID"
    ) {
        adapter.submitList(rows)
        if (rows.isEmpty()) {
            showEmpty(emptyTitle, emptyMessage, emptyIcon)
        } else {
            showContent()
        }
    }

    fun showContent() {
        binding.layoutState.visibility = View.GONE
        binding.progressState.visibility = View.GONE
        binding.tvStateIcon.visibility = View.GONE
        binding.btnStateRetry.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.swipeLayout.isRefreshing = false
    }

    fun showEmpty(
        title: String = "Aucune donnee disponible",
        message: String = "Tirez vers le bas pour actualiser ou modifiez vos filtres.",
        icon: String = "VID"
    ) {
        binding.swipeLayout.isRefreshing = false
        binding.recyclerView.visibility = View.INVISIBLE
        binding.layoutState.visibility = View.VISIBLE
        binding.progressState.visibility = View.GONE
        binding.tvStateIcon.visibility = View.VISIBLE
        binding.tvStateIcon.text = icon
        binding.tvStateTitle.text = title
        binding.tvStateMessage.text = message
        binding.btnStateRetry.visibility = View.GONE
    }

    fun showError(
        message: String,
        title: String = "Impossible de charger",
        retryVisible: Boolean = true
    ) {
        binding.swipeLayout.isRefreshing = false
        binding.recyclerView.visibility = View.INVISIBLE
        binding.layoutState.visibility = View.VISIBLE
        binding.progressState.visibility = View.GONE
        binding.tvStateIcon.visibility = View.VISIBLE
        binding.tvStateIcon.text = "ERR"
        binding.tvStateTitle.text = title
        binding.tvStateMessage.text = message
        binding.btnStateRetry.visibility = if (retryVisible) View.VISIBLE else View.GONE
        showErrorMessage(message)
    }

    fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(binding.root.context, R.color.statGreen))
            .setTextColor(ContextCompat.getColor(binding.root.context, R.color.onPrimary))
            .show()
    }

    fun showErrorMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(binding.root.context, R.color.error))
            .setTextColor(ContextCompat.getColor(binding.root.context, R.color.onPrimary))
            .show()
    }
}
