package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AdminTimetableUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.ChefRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUi
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUiItem
import com.pfe.gestionetudiantmobile.ui.common.FeatureStateController
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.common.ProfileUi
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.AuthenticatedFileOpener
import com.pfe.gestionetudiantmobile.util.SessionStore
import kotlinx.coroutines.launch

class ChefFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = ChefRepository()
    private val authRepository = AuthRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private lateinit var stateController: FeatureStateController
    private lateinit var sessionStore: SessionStore

    private var currentFeature: String = ""
    private var selectedClasseId: Long? = null

    private var classOptions: List<ClasseItem> = emptyList()
    private var moduleOptions: List<TeacherModuleItem> = emptyList()
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()
    private var timetableRows: Map<Long, TimetableItem> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = SessionStore(this)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        stateController = FeatureStateController(binding) { loadFeature() }

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

        binding.btnBack.setOnClickListener { finishWithTransition() }
        binding.btnAction.visibility = if (currentFeature == "timetable") View.VISIBLE else View.GONE
        binding.btnAction.text = "Nouveau"
        binding.btnAction.setOnClickListener { openTimetableDialog(null) }

        if (supportsClasseFilter(currentFeature)) {
            binding.btnFilter.visibility = View.VISIBLE
            binding.btnFilter.text = "Classe"
            binding.btnFilter.setOnClickListener { openClasseFilterDialog() }
        } else {
            binding.btnFilter.visibility = View.GONE
            binding.tvFilterSummary.visibility = View.GONE
        }

        binding.swipeLayout.setOnRefreshListener { loadFeature() }
        configureBottomNavigation()

        lifecycleScope.launch {
            loadClassOptions()
            loadModuleOptions()
            loadFeature()
        }
    }

    private suspend fun loadClassOptions() {
        when (val result = repository.classes()) {
            is ApiResult.Success -> classOptions = result.data.sortedBy { it.nom.lowercase() }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadModuleOptions() {
        when (val result = repository.modules()) {
            is ApiResult.Success -> moduleOptions = result.data.sortedBy { it.nom.lowercase() }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private fun supportsClasseFilter(feature: String): Boolean {
        return feature in setOf("students", "notes", "absences")
    }

    private fun loadFeature() {
        lifecycleScope.launch {
            val refreshing = binding.swipeLayout.isRefreshing
            binding.swipeLayout.isRefreshing = true
            if (!refreshing) {
                stateController.showLoading("Chargement de ${binding.tvTitle.text.toString().lowercase()}...")
            }
            when (currentFeature) {
                "students" -> when (val result = repository.students(selectedClasseId)) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.fullName} (${it.matricule})",
                            subtitle = "${it.classe ?: "-"} | ${it.filiere ?: "-"}",
                            badge = it.email ?: ""
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "notes" -> when (val result = repository.notes(selectedClasseId)) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                            subtitle = "CC: ${it.noteCc ?: "-"} | EX: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                            badge = it.statut
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "absences" -> when (val result = repository.absences(selectedClasseId)) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            id = it.id,
                            title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                            subtitle = "${it.dateAbsence} | ${it.nombreHeures}h",
                            badge = if (it.justifiee) "Justifiee" else "Non justifiee"
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "courses" -> when (val result = repository.courses()) {
                    is ApiResult.Success -> {
                        courseRows = result.data.associateBy { it.id }
                        submitRows(CourseDocumentUi.rowsByModule(result.data))
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "announcements" -> when (val result = repository.announcements()) {
                    is ApiResult.Success -> {
                        announcementRows = result.data.associateBy { it.id }
                        submitRows(result.data.map {
                            UiRow(
                                id = it.id,
                                title = it.title,
                                subtitle = "${it.message}\nFichier: ${it.attachmentName}",
                                badge = it.createdAt?.toString() ?: ""
                            )
                        })
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "timetable" -> when (val result = repository.timetable()) {
                    is ApiResult.Success -> {
                        timetableRows = result.data.associateBy { it.id }
                        submitRows(result.data.map {
                            UiRow(
                                id = it.id,
                                title = "${it.jour} ${it.heureDebut} - ${it.heureFin}",
                                subtitle = "${it.moduleNom ?: "Module"} | ${it.classeNom ?: "-"} | Salle ${it.salle}",
                                badge = if (it.valide) "Valide" else "Brouillon"
                            )
                        })
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                else -> stateController.showEmpty("Section indisponible", "Cette section mobile n'est pas encore disponible.", "?")
            }

            updateFilterSummary()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.CHEF,
            currentFeature = currentFeature.ifBlank { "dashboard" },
            onDashboard = { finishWithTransition() },
            onFeature = { navigateToFeature(it) },
            onProfile = { showProfileDialog() }
        )
    }

    private fun submitRows(rows: List<UiRow>) {
        stateController.showRows(
            adapter = adapter,
            rows = rows,
            emptyTitle = when (currentFeature) {
                "students" -> "Aucun etudiant"
                "notes" -> "Aucune note"
                "absences" -> "Aucune absence"
                "courses" -> "Aucun cours"
                "announcements" -> "Aucune annonce"
                "timetable" -> "Aucune seance"
                else -> "Aucune donnee"
            },
            emptyMessage = when (currentFeature) {
                "students" -> "Aucun etudiant ne correspond a la classe selectionnee."
                "notes" -> "Aucune note ne correspond aux filtres selectionnes."
                "absences" -> "Aucune absence ne correspond aux filtres selectionnes."
                "courses" -> "Les cours publies dans votre filiere apparaitront ici."
                "announcements" -> "Les annonces publiees dans votre filiere apparaitront ici."
                "timetable" -> "Les seances validees apparaitront ici."
                else -> "Tirez vers le bas pour actualiser ou modifiez vos filtres."
            },
            emptyIcon = when (currentFeature) {
                "students" -> "ETU"
                "notes" -> "20"
                "absences" -> "ABS"
                "courses" -> "CRS"
                "announcements" -> "ANN"
                "timetable" -> "EDT"
                else -> "VID"
            }
        )
    }

    private fun showLoadError(message: String) {
        stateController.showError(message, "Chargement impossible", retryVisible = true)
    }

    private fun navigateToFeature(feature: String) {
        if (currentFeature == feature) {
            binding.recyclerView.smoothScrollToPosition(0)
            loadFeature()
            return
        }

        startActivity(
            Intent(this, ChefFeatureListActivity::class.java)
                .putExtra(EXTRA_FEATURE, feature)
        )
        finishWithTransition()
    }

    private fun showProfileDialog() {
        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }
        ProfileUi.showSessionProfileDialog(this, user) {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }
    }

    private fun goLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finishWithTransition()
    }

    private fun updateFilterSummary() {
        if (!supportsClasseFilter(currentFeature)) {
            binding.tvFilterSummary.visibility = View.GONE
            return
        }

        val label = classOptions.firstOrNull { it.id == selectedClasseId }?.nom
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = if (label.isNullOrBlank()) "Toutes classes" else "Classe: $label"
    }

    private fun openClasseFilterDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }

        val options = mutableListOf(ChefOptionItem(null, "Toutes classes")) + classOptions.map { ChefOptionItem(it.id, it.nom) }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options.map { it.label })
        spinner.setSelection(options.indexOfFirst { it.id == selectedClasseId }.takeIf { it >= 0 } ?: 0)

        root.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle("Filtrer par classe")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Reinitialiser") { _, _ ->
                selectedClasseId = null
                loadFeature()
            }
            .setPositiveButton("Appliquer") { _, _ ->
                selectedClasseId = options[spinner.selectedItemPosition].id
                loadFeature()
            }
            .show()
    }

    private fun openCourseActions(course: CourseItem) {
        val documents = CourseDocumentUi.documentsFor(course)
        val options = mutableListOf("Voir le cours")
        when (documents.size) {
            0 -> Unit
            1 -> options += "Ouvrir le document"
            else -> options += "Voir les fichiers (${documents.size})"
        }

        AlertDialog.Builder(this)
            .setTitle(course.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir le cours" -> showCourseDetails(course)
                    "Ouvrir le document" -> openCourseDocument(documents.first())
                    "Voir les fichiers (${documents.size})" -> showCourseDocumentsDialog(course, documents)
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showCourseDetails(course: CourseItem) {
        AlertDialog.Builder(this)
            .setTitle(course.title)
            .setMessage(CourseDocumentUi.detailsText(course))
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun showCourseDocumentsDialog(course: CourseItem, documents: List<CourseDocumentUiItem>) {
        if (documents.isEmpty()) {
            Toast.makeText(this, "Aucun document joint a ce cours.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Documents: ${course.title}")
            .setItems(documents.map { CourseDocumentUi.documentLabel(it) }.toTypedArray()) { _, which ->
                openCourseDocument(documents[which])
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun openCourseDocument(document: CourseDocumentUiItem) {
        openExternal(document.filePath, document.fileName)
    }

    private fun onRowClicked(row: UiRow) {
        if (currentFeature == "courses") {
            val course = row.id?.let { courseRows[it] } ?: return
            openCourseActions(course)
            return
        }

        if (currentFeature == "announcements") {
            val announcement = row.id?.let { announcementRows[it] } ?: return
            val url = announcement.attachmentPath ?: return
            openExternal(url)
            return
        }

        if (currentFeature == "timetable") {
            val item = row.id?.let { timetableRows[it] } ?: return
            AlertDialog.Builder(this)
                .setTitle("${item.jour} ${item.heureDebut}")
                .setItems(arrayOf("Modifier", "Supprimer")) { _, which ->
                    when (which) {
                        0 -> openTimetableDialog(item)
                        1 -> confirmDelete("Supprimer cette seance ?") { deleteTimetable(item.id) }
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun openTimetableDialog(item: TimetableItem?) {
        if (classOptions.isEmpty() || moduleOptions.isEmpty()) {
            showError("Aucune classe ou module disponible pour votre filiere.")
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }

        val dayInput = EditText(this).apply { hint = "Jour"; setText(item?.jour ?: "LUNDI") }
        val startInput = EditText(this).apply { hint = "Heure debut (HH:mm)"; setText(item?.heureDebut?.toString() ?: "08:30") }
        val endInput = EditText(this).apply { hint = "Heure fin (HH:mm)"; setText(item?.heureFin?.toString() ?: "10:30") }
        val roomInput = EditText(this).apply { hint = "Salle"; setText(item?.salle ?: "A1") }
        val validSwitch = Switch(this).apply { text = "Seance validee"; isChecked = item?.valide ?: true }

        val classSpinner = Spinner(this)
        classSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classOptions.map { it.nom })
        classSpinner.setSelection(classOptions.indexOfFirst { it.id == item?.classeId }.takeIf { it >= 0 } ?: 0)

        val moduleSpinner = Spinner(this)
        moduleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            moduleOptions.map { "${it.nom} (${it.code})" }
        )
        moduleSpinner.setSelection(moduleOptions.indexOfFirst { it.id == item?.moduleId }.takeIf { it >= 0 } ?: 0)

        root.addView(dayInput)
        root.addView(startInput)
        root.addView(endInput)
        root.addView(roomInput)
        root.addView(classSpinner)
        root.addView(moduleSpinner)
        root.addView(validSwitch)

        AlertDialog.Builder(this)
            .setTitle(if (item == null) "Nouvelle seance EDT" else "Modifier seance EDT")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(if (item == null) "Creer" else "Mettre a jour") { _, _ ->
                val selectedClasse = classOptions[classSpinner.selectedItemPosition]
                val selectedModule = moduleOptions[moduleSpinner.selectedItemPosition]
                val request = AdminTimetableUpsertRequest(
                    jour = dayInput.text?.toString()?.trim().orEmpty(),
                    heureDebut = startInput.text?.toString()?.trim().orEmpty(),
                    heureFin = endInput.text?.toString()?.trim().orEmpty(),
                    moduleId = selectedModule.id,
                    classeId = selectedClasse.id,
                    filiereId = selectedClasse.filiereId ?: selectedModule.filiereId ?: 0L,
                    teacherId = 0L,
                    salle = roomInput.text?.toString()?.trim().orEmpty(),
                    valide = validSwitch.isChecked
                )
                saveTimetable(item?.id, request)
            }
            .show()
    }

    private fun saveTimetable(timetableId: Long?, request: AdminTimetableUpsertRequest) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            val result = if (timetableId == null) {
                repository.createTimetable(request)
            } else {
                repository.updateTimetable(timetableId, request)
            }
            when (result) {
                is ApiResult.Success -> showSuccess("Seance EDT enregistree.")
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteTimetable(timetableId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteTimetable(timetableId)) {
                is ApiResult.Success -> showSuccess(result.data.message)
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun confirmDelete(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage(message)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ -> onConfirm() }
            .show()
    }

    private fun showError(message: String) {
        stateController.showErrorMessage(message)
    }

    private fun showSuccess(message: String) {
        stateController.showSuccess(message)
    }

    private fun openExternal(url: String, suggestedFileName: String? = null) {
        Toast.makeText(this, "Telechargement du document...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = AuthenticatedFileOpener.downloadAndOpen(
                this@ChefFeatureListActivity,
                url,
                suggestedFileName
            )) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> showError(result.message)
            }
        }
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
    }
}

private data class ChefOptionItem(
    val id: Long?,
    val label: String
)
