package com.pfe.gestionetudiantmobile.ui.student

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.SubmissionFileItem
import com.pfe.gestionetudiantmobile.data.repository.StudentRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.AppUrlUtils
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class StudentFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = StudentRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private var currentFeature: String = ""
    private var assignmentRows: Map<Long, AssignmentItem> = emptyMap()
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()
    private var pendingAssignmentIdForFiles: Long? = null
    private var assignmentFilter: String = "all"

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val assignmentId = pendingAssignmentIdForFiles
        pendingAssignmentIdForFiles = null
        if (uris.isEmpty() || assignmentId == null) {
            return@registerForActivityResult
        }
        openSelectedFilesDialog(assignmentId, uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        binding.tvTitle.text = when (currentFeature) {
            "notes" -> "Mes notes"
            "absences" -> "Mes absences"
            "timetable" -> "Mon emploi du temps"
            "courses" -> "Mes cours"
            "announcements" -> "Mes annonces"
            "assignments" -> "Mes devoirs"
            else -> "Liste"
        }
        configureActionButton()

        binding.btnBack.setOnClickListener { finish() }

        binding.swipeLayout.setOnRefreshListener { loadFeature(currentFeature) }
        loadFeature(currentFeature)
    }

    private fun loadFeature(feature: String) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true

            when (feature) {
                "notes" -> when (val result = repository.notes()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            title = "${it.moduleNom ?: "Module"} (${it.semestre})",
                            subtitle = "CC: ${it.noteCc ?: "-"} | Examen: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                            badge = it.statut
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                "absences" -> when (val result = repository.absences()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            title = "${it.moduleNom ?: "Module"} - ${it.dateAbsence}",
                            subtitle = "${it.nombreHeures}h | ${if (it.justifiee) "Justifiee" else "Non justifiee"}",
                            badge = if (it.justifiee) "OK" else "A traiter"
                        )
                    })
                    is ApiResult.Error -> showError(result.message)
                }

                "timetable" -> when (val result = repository.timetable()) {
                    is ApiResult.Success -> adapter.submitList(result.data.map {
                        UiRow(
                            title = "${it.jour} ${it.heureDebut} - ${it.heureFin}",
                            subtitle = "${it.moduleNom ?: "Module"} | Salle ${it.salle}",
                            badge = if (it.valide) "Valide" else "Brouillon"
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
                                badge = "Ouvrir"
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

                "assignments" -> when (val result = repository.assignments(assignmentFilter)) {
                    is ApiResult.Success -> {
                        assignmentRows = result.data.associateBy { it.id }
                        adapter.submitList(result.data.map {
                            UiRow(
                                id = it.id,
                                title = it.title,
                                subtitle = "Deadline: ${it.dueDate} | Module: ${it.moduleNom ?: "-"} | Statut: ${it.submissionStatus}",
                                badge = when {
                                    it.overdue -> "Overdue"
                                    it.lateSubmission -> "Late"
                                    else -> it.submissionStatus
                                }
                            )
                        })
                    }
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
            if (announcement.attachmentPath != null) {
                openExternal(announcement.attachmentPath)
            } else {
                Toast.makeText(this, "Aucun document joint pour cette annonce.", Toast.LENGTH_LONG).show()
            }
            return
        }

        if (currentFeature != "assignments") {
            return
        }
        val assignmentId = row.id ?: return
        val assignment = assignmentRows[assignmentId] ?: return

        val options = mutableListOf<String>()
        options += "Voir details"
        if (assignment.attachmentPath != null) {
            options += "Telecharger l'enonce"
        }
        if (!assignment.overdue) {
            options += "Soumettre texte"
            options += "Soumettre fichiers"
        }
        if (assignment.submissionStatus != "NOT_SUBMITTED") {
            options += "Voir ma soumission"
        }

        AlertDialog.Builder(this)
            .setTitle(assignment.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir details" -> showAssignmentDetails(assignment)
                    "Telecharger l'enonce" -> openExternal(assignment.attachmentPath ?: return@setItems)
                    "Soumettre texte" -> openSubmissionDialog(assignmentId, assignment.title)
                    "Soumettre fichiers" -> {
                        pendingAssignmentIdForFiles = assignmentId
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Voir ma soumission" -> loadAndShowSubmission(assignment)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openSubmissionDialog(assignmentId: Long, title: String) {
        val input = EditText(this).apply {
            hint = "Votre reponse"
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(36, 24, 36, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("Soumettre: $title")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer") { _, _ ->
                val text = input.text?.toString().orEmpty().trim()
                if (text.isBlank()) {
                    Toast.makeText(this, "Veuillez saisir une reponse.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                submitAssignmentText(assignmentId, text)
            }
            .show()
    }

    private fun submitAssignmentText(assignmentId: Long, text: String) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.submitAssignment(assignmentId, submissionText = text, fileParts = null)) {
                is ApiResult.Success -> {
                    val late = result.data.lateSubmission
                    if (late) {
                        Toast.makeText(this@StudentFeatureListActivity, "Soumis en retard.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@StudentFeatureListActivity, "Soumission enregistree.", Toast.LENGTH_LONG).show()
                    }
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> showError(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openSelectedFilesDialog(assignmentId: Long, uris: List<Uri>) {
        val labels = uris.map { FileUploadUtils.describeUri(this, it) }
        val keepFlags = BooleanArray(labels.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Fichiers selectionnes")
            .setMultiChoiceItems(labels.toTypedArray(), keepFlags) { _, which, isChecked ->
                keepFlags[which] = isChecked
            }
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer") { _, _ ->
                val finalUris = uris.filterIndexed { index, _ -> keepFlags[index] }
                if (finalUris.isEmpty()) {
                    Toast.makeText(this, "Aucun fichier conserve pour l'envoi.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                submitAssignmentFiles(assignmentId, finalUris)
            }
            .show()
    }

    private fun submitAssignmentFiles(assignmentId: Long, uris: List<Uri>) {
        val fileParts = mutableListOf<okhttp3.MultipartBody.Part>()
        for (uri in uris) {
            val part = runCatching { FileUploadUtils.uriToMultipartPart(this, uri, "files") }
                .getOrElse {
                    Toast.makeText(this, it.message ?: "Erreur lors de la lecture d'un fichier.", Toast.LENGTH_LONG).show()
                    return
                }
            fileParts += part
        }

        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.submitAssignment(assignmentId, submissionText = null, fileParts = fileParts)) {
                is ApiResult.Success -> {
                    val late = result.data.lateSubmission
                    if (late) {
                        Toast.makeText(this@StudentFeatureListActivity, "Fichiers soumis en retard.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@StudentFeatureListActivity, "Fichiers soumis avec succes.", Toast.LENGTH_LONG).show()
                    }
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> showError(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun configureActionButton() {
        if (currentFeature != "assignments") {
            binding.btnAction.visibility = View.GONE
            return
        }

        binding.btnAction.visibility = View.VISIBLE
        updateAssignmentFilterButtonText()
        binding.btnAction.setOnClickListener { openAssignmentFilterDialog() }
    }

    private fun openAssignmentFilterDialog() {
        val items = arrayOf(
            "Tous",
            "A venir",
            "En retard",
            "Soumis",
            "Non soumis"
        )
        val values = listOf("all", "upcoming", "overdue", "submitted", "not_submitted")
        val checked = values.indexOf(assignmentFilter).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Filtrer les devoirs")
            .setSingleChoiceItems(items, checked) { dialog, which ->
                assignmentFilter = values[which]
                updateAssignmentFilterButtonText()
                loadFeature(currentFeature)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateAssignmentFilterButtonText() {
        binding.btnAction.text = when (assignmentFilter) {
            "upcoming" -> "Filtre: A venir"
            "overdue" -> "Filtre: En retard"
            "submitted" -> "Filtre: Soumis"
            "not_submitted" -> "Filtre: Non soumis"
            else -> "Filtre: Tous"
        }
    }

    private fun showAssignmentDetails(assignment: AssignmentItem) {
        val details = buildString {
            appendLine("Description:")
            appendLine(assignment.description)
            appendLine()
            appendLine("Module: ${assignment.moduleNom ?: "-"}")
            appendLine("Enseignant: ${assignment.teacherName ?: "-"}")
            appendLine("Date limite: ${assignment.dueDate}")
            appendLine("Statut: ${assignment.submissionStatus}")
            appendLine("Soumis le: ${assignment.submittedAt ?: "-"}")
            appendLine("Note: ${assignment.score ?: "-"}")
            appendLine("Feedback: ${assignment.feedback ?: "-"}")
        }
        AlertDialog.Builder(this)
            .setTitle(assignment.title)
            .setMessage(details)
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun loadAndShowSubmission(assignment: AssignmentItem) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.assignmentSubmission(assignment.id)) {
                is ApiResult.Success -> openSubmissionDetailsDialog(assignment, result.data)
                is ApiResult.Error -> showError(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openSubmissionDetailsDialog(assignment: AssignmentItem, submission: AssignmentSubmissionItem) {
        val files = if (submission.files.isNotEmpty()) {
            submission.files.filter { !it.filePath.isNullOrBlank() }
        } else {
            listOf(
                SubmissionFileItem(
                    id = null,
                    filePath = submission.filePath,
                    fileName = submission.fileName,
                    contentType = null,
                    fileSize = null,
                    uploadedAt = null
                )
            ).filter { !it.filePath.isNullOrBlank() }
        }
        val canManageFiles = !assignment.overdue && LocalDateTime.now().isBefore(assignment.dueDate)

        val options = mutableListOf<String>()
        options += "Voir informations"
        if (files.isNotEmpty()) {
            options += "Voir mes fichiers (${files.size})"
        }

        AlertDialog.Builder(this)
            .setTitle("Soumission: ${assignment.title}")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir informations" -> {
                        val info = buildString {
                            appendLine("Statut: ${submission.status}")
                            appendLine("Soumis le: ${submission.submittedAt ?: "-"}")
                            appendLine("Retard: ${if (submission.lateSubmission) "Oui" else "Non"}")
                            appendLine("Note: ${submission.score ?: "-"}")
                            appendLine("Feedback: ${submission.feedback ?: "-"}")
                            appendLine()
                            appendLine("Texte:")
                            appendLine(submission.submissionText ?: "-")
                        }
                        AlertDialog.Builder(this@StudentFeatureListActivity)
                            .setTitle("Details de ma soumission")
                            .setMessage(info)
                            .setPositiveButton("Fermer", null)
                            .show()
                    }

                    "Voir mes fichiers (${files.size})" -> openSubmissionFilesDialog(
                        assignmentId = assignment.id,
                        files = files,
                        canDelete = canManageFiles
                    )
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun openSubmissionFilesDialog(assignmentId: Long,
                                          files: List<SubmissionFileItem>,
                                          canDelete: Boolean) {
        if (files.isEmpty()) {
            Toast.makeText(this, "Aucun fichier disponible.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = files.map { file ->
            val size = file.fileSize?.let { "$it o" } ?: "-"
            "${file.fileName} ($size)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Mes fichiers soumis")
            .setItems(labels) { _, which ->
                val file = files[which]
                val options = mutableListOf("Telecharger")
                if (canDelete && file.id != null) {
                    options += "Supprimer ce fichier"
                }

                AlertDialog.Builder(this)
                    .setTitle(file.fileName)
                    .setItems(options.toTypedArray()) { _, actionIndex ->
                        when (options[actionIndex]) {
                            "Telecharger" -> {
                                val target = file.filePath
                                if (target.isNullOrBlank()) {
                                    showError("Lien de telechargement indisponible.")
                                } else {
                                    openExternal(target)
                                }
                            }
                            "Supprimer ce fichier" -> confirmDeleteSubmissionFile(assignmentId, file.id)
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun confirmDeleteSubmissionFile(assignmentId: Long, fileId: Long?) {
        if (fileId == null) {
            Toast.makeText(this, "Fichier legacy non supprimable individuellement.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Supprimer ce fichier")
            .setMessage("Ce fichier sera retire de votre soumission. Continuer ?")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                deleteSubmissionFile(assignmentId, fileId)
            }
            .show()
    }

    private fun deleteSubmissionFile(assignmentId: Long, fileId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteSubmissionFile(assignmentId, fileId)) {
                is ApiResult.Success -> {
                    Toast.makeText(
                        this@StudentFeatureListActivity,
                        "Fichier supprime de la soumission.",
                        Toast.LENGTH_LONG
                    ).show()
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> showError(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openExternal(url: String) {
        val target = AppUrlUtils.toAbsolute(url)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
    }
}
