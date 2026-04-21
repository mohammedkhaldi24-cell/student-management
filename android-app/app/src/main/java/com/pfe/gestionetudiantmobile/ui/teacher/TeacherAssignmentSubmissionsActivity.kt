package com.pfe.gestionetudiantmobile.ui.teacher

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.SubmissionFileItem
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.FeatureStateController
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.AuthenticatedFileOpener
import kotlinx.coroutines.launch

class TeacherAssignmentSubmissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = TeacherRepository()
    private val adapter = UiRowAdapter { row -> onSubmissionClicked(row) }
    private lateinit var stateController: FeatureStateController
    private var assignmentId: Long = -1L
    private var submissionsById: Map<Long, AssignmentSubmissionItem> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assignmentId = intent.getLongExtra(EXTRA_ASSIGNMENT_ID, -1L)
        val title = intent.getStringExtra(EXTRA_ASSIGNMENT_TITLE).orEmpty()

        if (assignmentId <= 0) {
            Toast.makeText(this, "Assignment invalide", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        stateController = FeatureStateController(binding) { loadSubmissions() }
        binding.tvTitle.text = "Soumissions: $title"
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeLayout.setOnRefreshListener { loadSubmissions() }

        loadSubmissions()
    }

    private fun loadSubmissions() {
        lifecycleScope.launch {
            val refreshing = binding.swipeLayout.isRefreshing
            binding.swipeLayout.isRefreshing = true
            if (!refreshing) {
                stateController.showLoading("Chargement des soumissions...")
            }
            when (val result = repository.submissions(assignmentId)) {
                is ApiResult.Success -> {
                    submissionsById = result.data.associateBy { it.id }
                    stateController.showRows(
                        adapter = adapter,
                        rows = result.data.map {
                            UiRow(
                                id = it.id,
                                title = "${it.studentName ?: "Etudiant"} (${it.matricule ?: "-"})",
                                subtitle = "Statut: ${it.status} | Date: ${it.submittedAt ?: "-"} | Note: ${it.score ?: "-"} | Fichiers: ${it.files.size}",
                                badge = if (it.lateSubmission) "Late" else "On time"
                            )
                        },
                        emptyTitle = "Aucune soumission",
                        emptyMessage = "Les travaux envoyes par les etudiants apparaitront ici.",
                        emptyIcon = "DEV"
                    )
                }
                is ApiResult.Error -> stateController.showError(result.message, "Chargement impossible", retryVisible = true)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun onSubmissionClicked(row: UiRow) {
        val submissionId = row.id ?: return
        val submission = submissionsById[submissionId] ?: return

        val options = mutableListOf<String>()
        if (submission.files.isNotEmpty() || submission.filePath != null) {
            options += "Voir les fichiers soumis"
        }
        options += "Corriger / Noter"

        AlertDialog.Builder(this)
            .setTitle(submission.studentName ?: "Soumission")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir les fichiers soumis" -> openSubmissionFilesDialog(submission)
                    "Corriger / Noter" -> openReviewDialog(submission)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openSubmissionFilesDialog(submission: AssignmentSubmissionItem) {
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

        if (files.isEmpty()) {
            Toast.makeText(this, "Aucun fichier soumis.", Toast.LENGTH_LONG).show()
            return
        }

        val labels = files.map { file ->
            val size = file.fileSize?.let { "$it o" } ?: "-"
            "${file.fileName} ($size)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Fichiers de la soumission")
            .setItems(labels) { _, which ->
                openExternal(files[which].filePath ?: return@setItems)
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun openReviewDialog(submission: AssignmentSubmissionItem) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
        }

        val scoreInput = EditText(this).apply {
            hint = "Note (/20)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(submission.score?.toString().orEmpty())
        }

        val feedbackInput = EditText(this).apply {
            hint = "Feedback"
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(submission.feedback.orEmpty())
        }

        val spinner = Spinner(this)
        val statuses = listOf("SUBMITTED", "REVIEWED", "GRADED")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statuses)
        val selectedIndex = statuses.indexOf(submission.status).takeIf { it >= 0 } ?: 1
        spinner.setSelection(selectedIndex)

        root.addView(scoreInput)
        root.addView(feedbackInput)
        root.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle("Corriger soumission")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val scoreValue = scoreInput.text?.toString()?.trim().orEmpty()
                val score = scoreValue.toDoubleOrNull()
                val feedback = feedbackInput.text?.toString()?.trim().orEmpty().ifBlank { null }
                val status = statuses[spinner.selectedItemPosition]
                reviewSubmission(submission.id, score, feedback, status)
            }
            .show()
    }

    private fun reviewSubmission(submissionId: Long, score: Double?, feedback: String?, status: String) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.reviewSubmission(assignmentId, submissionId, score, feedback, status)) {
                is ApiResult.Success -> {
                    stateController.showSuccess("Evaluation enregistree")
                    loadSubmissions()
                }
                is ApiResult.Error -> stateController.showErrorMessage(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openExternal(url: String) {
        Toast.makeText(this, "Telechargement du document...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = AuthenticatedFileOpener.downloadAndOpen(this@TeacherAssignmentSubmissionsActivity, url)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> stateController.showErrorMessage(result.message)
            }
        }
    }

    companion object {
        const val EXTRA_ASSIGNMENT_ID = "extra_assignment_id"
        const val EXTRA_ASSIGNMENT_TITLE = "extra_assignment_title"
    }
}
