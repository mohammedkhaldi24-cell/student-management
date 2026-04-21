package com.pfe.gestionetudiantmobile.ui.teacher

import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherCreateAnnouncementBinding
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationCopy
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import kotlinx.coroutines.launch

class TeacherCreateAnnouncementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherCreateAnnouncementBinding
    private val repository = TeacherRepository()
    private var selectedFileUri: Uri? = null
    private var modules: List<TeacherModuleItem> = emptyList()
    private var initialModuleId: Long? = null
    private var initialClasseId: Long? = null
    private var initialFiliereId: Long? = null
    private var resolvedClasseId: Long? = null
    private var resolvedFiliereId: Long? = null
    private var audienceRequestVersion = 0

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            binding.tvFileName.text = FileUploadUtils.resolveFileName(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherCreateAnnouncementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initialModuleId = intent.getLongExtra(EXTRA_MODULE_ID, -1L).takeIf { it > 0 }
        initialClasseId = intent.getLongExtra(EXTRA_CLASSE_ID, -1L).takeIf { it > 0 }
        initialFiliereId = intent.getLongExtra(EXTRA_FILIERE_ID, -1L).takeIf { it > 0 }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseFile.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnSave.setOnClickListener { createAnnouncement() }

        loadModules()
    }

    private fun loadModules() {
        lifecycleScope.launch {
            when (val result = repository.modules()) {
                is ApiResult.Success -> {
                    modules = result.data
                    if (modules.isEmpty()) {
                        binding.spinnerModule.adapter = ArrayAdapter(
                            this@TeacherCreateAnnouncementActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            listOf("Aucun module")
                        )
                        binding.tvAudienceSummary.text = "Audience: aucun module assigne a votre compte"
                        binding.tvStudentSummary.text = "Etudiants: aucun"
                        binding.btnSave.isEnabled = false
                        return@launch
                    }

                    binding.spinnerModule.adapter = ArrayAdapter(
                        this@TeacherCreateAnnouncementActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        modules.map { "${it.nom} (${it.code})" }
                    )
                    binding.spinnerModule.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            loadAudienceForSelectedModule()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                    val initialIndex = initialModuleId?.let { id -> modules.indexOfFirst { it.id == id } } ?: -1
                    if (initialIndex >= 0) {
                        binding.spinnerModule.setSelection(initialIndex)
                    }
                    loadAudienceForSelectedModule()
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateAnnouncementActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun selectedModule(): TeacherModuleItem? {
        return modules.getOrNull(binding.spinnerModule.selectedItemPosition)
    }

    private fun contextualClasseId(module: TeacherModuleItem): Long? {
        return initialClasseId?.takeIf { initialModuleId == null || initialModuleId == module.id }
    }

    private fun contextualFiliereId(module: TeacherModuleItem): Long? {
        return initialFiliereId?.takeIf { initialModuleId == null || initialModuleId == module.id }
    }

    private fun loadAudienceForSelectedModule() {
        val module = selectedModule()
        if (module == null) {
            resolvedClasseId = null
            resolvedFiliereId = null
            binding.tvAudienceSummary.text = "Audience: choisissez un module"
            binding.tvStudentSummary.text = "Etudiants: en attente"
            binding.btnSave.isEnabled = false
            return
        }

        val requestVersion = ++audienceRequestVersion
        val contextClasseId = contextualClasseId(module)
        val contextFiliereId = contextualFiliereId(module)
        resolvedClasseId = contextClasseId
        resolvedFiliereId = contextFiliereId ?: module.filiereId
        binding.btnSave.isEnabled = false
        binding.tvAudienceSummary.text = "Audience: detection automatique..."
        binding.tvStudentSummary.text = "Etudiants: chargement..."

        lifecycleScope.launch {
            val classesResult = repository.classes(module.id, contextFiliereId ?: module.filiereId)
            if (requestVersion != audienceRequestVersion) return@launch

            val classes: List<ClasseItem>
            val classLoadWarning: String?
            when (classesResult) {
                is ApiResult.Success -> {
                    classes = classesResult.data
                    classLoadWarning = null
                }
                is ApiResult.Error -> {
                    classes = emptyList()
                    classLoadWarning = classesResult.message
                }
            }

            val explicitClass = contextClasseId?.let { id -> classes.firstOrNull { it.id == id } }
            val singleClass = classes.singleOrNull()
            val targetClass = explicitClass ?: singleClass
            resolvedClasseId = targetClass?.id ?: contextClasseId
            resolvedFiliereId = targetClass?.filiereId
                ?: contextFiliereId
                ?: module.filiereId
                ?: classes.mapNotNull { it.filiereId }.distinct().singleOrNull()

            binding.tvAudienceSummary.text = audienceSummary(module, targetClass, classes, classLoadWarning)

            val studentsResult = repository.students(
                moduleId = module.id,
                classeId = resolvedClasseId,
                filiereId = resolvedFiliereId,
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

    private fun audienceSummary(
        module: TeacherModuleItem,
        targetClass: ClasseItem?,
        classes: List<ClasseItem>,
        warning: String?
    ): String {
        val target = when {
            targetClass != null -> "classe ${targetClass.nom}"
            resolvedClasseId != null -> "classe detectee depuis la seance"
            resolvedFiliereId != null -> "filiere ${module.filiereNom ?: targetClass?.filiereNom ?: "du module"}"
            classes.size > 1 -> "plusieurs classes trouvees, filiere non fournie"
            else -> "non determinee"
        }
        return buildString {
            append("Audience: ")
            append(target)
            if (classes.size > 1 && targetClass == null && resolvedFiliereId != null) {
                append(" (")
                append(classes.size)
                append(" classes)")
            }
            if (warning != null && resolvedClasseId == null && resolvedFiliereId == null) {
                append(" | API: ")
                append(warning)
            }
        }
    }

    private fun createAnnouncement() {
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val message = binding.etMessage.text?.toString().orEmpty().trim()

        if (title.isBlank() || message.isBlank()) {
            Toast.makeText(this, "Titre et message sont obligatoires.", Toast.LENGTH_LONG).show()
            return
        }

        val moduleId = selectedModule()?.id
        if (moduleId == null) {
            Toast.makeText(this, "Choisissez un module pour determiner automatiquement l'audience.", Toast.LENGTH_LONG).show()
            return
        }

        val filePart = selectedFileUri?.let { uri ->
            runCatching { FileUploadUtils.uriToMultipartPart(this, uri, "attachment") }
                .getOrElse {
                    Toast.makeText(this, it.message ?: "Erreur fichier", Toast.LENGTH_LONG).show()
                    return
                }
        }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            when (
                val result = repository.createAnnouncement(
                    title = title,
                    message = message,
                    moduleId = moduleId,
                    classeId = resolvedClasseId,
                    filiereId = resolvedFiliereId,
                    attachment = filePart
                )
            ) {
                is ApiResult.Success -> {
                    Toast.makeText(
                        this@TeacherCreateAnnouncementActivity,
                        AcademicNotificationCopy.success("Annonce publiee avec succes"),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateAnnouncementActivity, result.message, Toast.LENGTH_LONG).show()
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_CLASSE_ID = "extra_classe_id"
        const val EXTRA_FILIERE_ID = "extra_filiere_id"
    }
}
