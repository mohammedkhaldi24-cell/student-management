package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.repository.ChefRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.AppUrlUtils
import com.pfe.gestionetudiantmobile.util.ApiResult
import kotlinx.coroutines.launch

class ChefFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = ChefRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private var currentFeature: String = ""
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.btnAction.visibility = android.view.View.GONE

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        binding.tvTitle.text = when (currentFeature) {
            "students" -> "Etudiants"
            "notes" -> "Notes"
            "absences" -> "Absences"
            "courses" -> "Cours"
            "announcements" -> "Annonces"
            "timetable" -> "Emploi du temps"
            else -> "Liste"
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeLayout.setOnRefreshListener { loadFeature() }
        loadFeature()
    }

    private fun loadFeature() {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (currentFeature) {
                "students" -> when (val result = repository.students()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.fullName} (${it.matricule})",
                            subtitle = "${it.classe ?: "-"} | ${it.filiere ?: "-"}",
                            badge = it.email ?: ""
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                "notes" -> when (val result = repository.notes()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                            subtitle = "CC: ${it.noteCc ?: "-"} | EX: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                            badge = it.statut
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                "absences" -> when (val result = repository.absences()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                            subtitle = "${it.dateAbsence} | ${it.nombreHeures}h",
                            badge = if (it.justifiee) "Justifiee" else "Non justifiee"
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                "courses" -> when (val result = repository.courses()) {
                    is ApiResult.Success -> {
                        courseRows = result.data.associateBy { it.id }
                        adapter.submitList(result.data.map {
                            UiRow(
                                id = it.id,
                                title = it.title,
                                subtitle = "${it.moduleNom ?: "-"} | ${it.fileName}",
                                badge = if (it.filePath != null) "Ouvrir" else "Sans fichier"
                            )
                        })
                    }
                    is ApiResult.Error -> showError(result.message)
                }

                "announcements" -> when (val result = repository.announcements()) {
                    is ApiResult.Success -> {
                        announcementRows = result.data.associateBy { it.id }
                        adapter.submitList(result.data.map {
                            UiRow(
                                id = it.id,
                                title = it.title,
                                subtitle = "${it.message}\nFichier: ${it.attachmentName}",
                                badge = it.createdAt?.toString() ?: ""
                            )
                        })
                    }
                    is ApiResult.Error -> showError(result.message)
                }

                "timetable" -> when (val result = repository.timetable()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.jour} ${it.heureDebut} - ${it.heureFin}",
                            subtitle = "${it.moduleNom ?: "Module"} | ${it.classeNom ?: "-"} | Salle ${it.salle}",
                            badge = if (it.valide) "Valide" else "Brouillon"
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                else -> adapter.submitList(listOf(UiRow(title = "Feature inconnue", subtitle = "Aucune donnee")))
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun onRowClicked(row: UiRow) {
        if (currentFeature == "courses") {
            val course = row.id?.let { courseRows[it] } ?: return
            val url = course.filePath ?: return
            openExternal(url)
            return
        }

        if (currentFeature == "announcements") {
            val announcement = row.id?.let { announcementRows[it] } ?: return
            val url = announcement.attachmentPath ?: return
            openExternal(url)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openExternal(url: String) {
        val target = AppUrlUtils.toAbsolute(url)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
    }
}
