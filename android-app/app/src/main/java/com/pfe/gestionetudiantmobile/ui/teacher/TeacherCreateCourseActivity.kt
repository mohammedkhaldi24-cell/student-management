package com.pfe.gestionetudiantmobile.ui.teacher

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherCreateCourseBinding
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationCopy
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import kotlinx.coroutines.launch

class TeacherCreateCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherCreateCourseBinding
    private val repository = TeacherRepository()

    private var modules: List<TeacherModuleItem> = emptyList()
    private val selectedFileUris = mutableListOf<Uri>()
    private var audienceRequestVersion = 0

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val freshUris = uris.filterNot { selectedFileUris.contains(it) }
            if (freshUris.isEmpty()) {
                Toast.makeText(this, "Ces fichiers sont deja selectionnes.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            selectedFileUris.addAll(freshUris)
            renderSelectedFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherCreateCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseFile.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnSave.setOnClickListener { createCourse() }
        renderSelectedFiles()

        loadModules()
    }

    private fun renderSelectedFiles() {
        if (selectedFileUris.isEmpty()) {
            binding.tvFileName.text = "Aucun fichier selectionne"
            return
        }

        binding.tvFileName.maxLines = 6
        binding.tvFileName.ellipsize = TextUtils.TruncateAt.END
        binding.tvFileName.text = selectedFileUris.mapIndexed { index, uri ->
            val mimeType = FileUploadUtils.resolveMimeType(this, uri)
            val name = FileUploadUtils.resolveFileName(this, uri)
            val size = FileUploadUtils.resolveFileSize(this, uri)
                ?.let { FileUploadUtils.readableSize(it) }
                ?: "taille inconnue"
            "${index + 1}. ${FileUploadUtils.iconForMimeType(mimeType)} $name ($size)"
        }.joinToString("\n")
    }

    private fun loadModules() {
        lifecycleScope.launch {
            when (val result = repository.modules()) {
                is ApiResult.Success -> {
                    modules = result.data
                    if (modules.isEmpty()) {
                        Toast.makeText(this@TeacherCreateCourseActivity, "Aucun module assigne.", Toast.LENGTH_LONG).show()
                        finish()
                        return@launch
                    }
                    val labels = modules.map { "${it.nom} (${it.code})" }
                    binding.spinnerModule.adapter = ArrayAdapter(
                        this@TeacherCreateCourseActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        labels
                    )
                    binding.spinnerModule.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            loadAudienceForSelectedModule()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                    loadAudienceForSelectedModule()
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateCourseActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun selectedModule(): TeacherModuleItem? {
        return modules.getOrNull(binding.spinnerModule.selectedItemPosition)
    }

    private fun loadAudienceForSelectedModule() {
        val module = selectedModule()
        if (module == null) {
            binding.tvAudienceSummary.text = "Audience: choisissez un module"
            binding.tvStudentSummary.text = "Etudiants: en attente"
            binding.btnSave.isEnabled = false
            return
        }

        val requestVersion = ++audienceRequestVersion
        binding.btnSave.isEnabled = false
        binding.tvAudienceSummary.text = "Audience: detection automatique..."
        binding.tvStudentSummary.text = "Etudiants: chargement..."

        lifecycleScope.launch {
            val classesResult = repository.classes(module.id, module.filiereId)
            if (requestVersion != audienceRequestVersion) return@launch

            val classes: List<ClasseItem>
            val warning: String?
            when (classesResult) {
                is ApiResult.Success -> {
                    classes = classesResult.data
                    warning = null
                }
                is ApiResult.Error -> {
                    classes = emptyList()
                    warning = classesResult.message
                }
            }

            binding.tvAudienceSummary.text = buildString {
                append("Audience: ")
                append(module.filiereNom?.let { "filiere $it" } ?: "filiere du module")
                if (classes.isNotEmpty()) {
                    append(" (")
                    append(classes.size)
                    append(" classe")
                    append(if (classes.size > 1) "s" else "")
                    append(")")
                }
                if (warning != null && module.filiereId == null) {
                    append(" | API: ")
                    append(warning)
                }
            }

            val studentsResult = repository.students(
                moduleId = module.id,
                classeId = null,
                filiereId = module.filiereId,
                query = null
            )
            if (requestVersion != audienceRequestVersion) return@launch

            binding.tvStudentSummary.text = when (studentsResult) {
                is ApiResult.Success -> "Etudiants: ${studentsResult.data.size} charge(s) automatiquement"
                is ApiResult.Error -> "Etudiants: verification indisponible (${studentsResult.message})"
            }
            binding.btnSave.isEnabled = true
        }
    }

    private fun createCourse() {
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val description = binding.etDescription.text?.toString().orEmpty().trim().ifBlank { null }

        if (title.isBlank()) {
            Toast.makeText(this, "Le titre est obligatoire.", Toast.LENGTH_LONG).show()
            return
        }

        if (modules.isEmpty()) {
            Toast.makeText(this, "Aucun module disponible.", Toast.LENGTH_LONG).show()
            return
        }

        val selectedModule = selectedModule()
        if (selectedModule == null) {
            Toast.makeText(this, "Choisissez un module.", Toast.LENGTH_LONG).show()
            return
        }
        val fileParts = selectedFileUris.map { uri ->
            runCatching { FileUploadUtils.uriToMultipartPart(this, uri, "files") }
                .getOrElse {
                    Toast.makeText(this, it.message ?: "Erreur fichier", Toast.LENGTH_LONG).show()
                    return
                }
        }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            when (
                val result = repository.createCourse(
                    title = title,
                    description = description,
                    moduleId = selectedModule.id,
                    classeId = null,
                    filiereId = null,
                    fileParts = fileParts.takeIf { it.isNotEmpty() }
                )
            ) {
                is ApiResult.Success -> {
                    Toast.makeText(
                        this@TeacherCreateCourseActivity,
                        AcademicNotificationCopy.success("Cours cree avec succes"),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateCourseActivity, result.message, Toast.LENGTH_LONG).show()
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }
}
