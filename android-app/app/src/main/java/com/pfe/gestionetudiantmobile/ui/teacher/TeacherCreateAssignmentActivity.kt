package com.pfe.gestionetudiantmobile.ui.teacher

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherCreateAssignmentBinding
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class TeacherCreateAssignmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherCreateAssignmentBinding
    private val repository = TeacherRepository()

    private var modules: List<TeacherModuleItem> = emptyList()
    private var selectedFileUri: Uri? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            binding.tvFileName.text = FileUploadUtils.resolveFileName(this, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherCreateAssignmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseFile.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnSave.setOnClickListener { createAssignment() }

        if (binding.etDueDate.text.isNullOrBlank()) {
            binding.etDueDate.setText(LocalDateTime.now().plusDays(7).withSecond(0).withNano(0).toString())
        }

        loadModules()
    }

    private fun loadModules() {
        lifecycleScope.launch {
            when (val result = repository.modules()) {
                is ApiResult.Success -> {
                    modules = result.data
                    val labels = if (modules.isEmpty()) {
                        listOf("Aucun module")
                    } else {
                        modules.map { "${it.nom} (${it.code})" }
                    }
                    binding.spinnerModule.adapter = ArrayAdapter(
                        this@TeacherCreateAssignmentActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        labels
                    )
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateAssignmentActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun createAssignment() {
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val description = binding.etDescription.text?.toString().orEmpty().trim()
        val dueDate = binding.etDueDate.text?.toString().orEmpty().trim()

        if (title.isBlank() || description.isBlank() || dueDate.isBlank()) {
            Toast.makeText(this, "Titre, description et date limite sont obligatoires.", Toast.LENGTH_LONG).show()
            return
        }

        val selectedModule = modules.getOrNull(binding.spinnerModule.selectedItemPosition)
        val moduleId = selectedModule?.id

        val classeId = binding.etClasseId.text?.toString()?.trim()?.toLongOrNull()
        val userFiliereId = binding.etFiliereId.text?.toString()?.trim()?.toLongOrNull()
        val filiereId = userFiliereId ?: selectedModule?.filiereId

        if (classeId == null && filiereId == null) {
            Toast.makeText(this, "Precisez classeId ou filiereId (ou choisissez un module avec filiere).", Toast.LENGTH_LONG).show()
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
                val result = repository.createAssignment(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    moduleId = moduleId,
                    classeId = classeId,
                    filiereId = filiereId,
                    published = binding.switchPublished.isChecked,
                    attachment = filePart
                )
            ) {
                is ApiResult.Success -> {
                    Toast.makeText(this@TeacherCreateAssignmentActivity, "Devoir cree avec succes.", Toast.LENGTH_LONG).show()
                    finish()
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherCreateAssignmentActivity, result.message, Toast.LENGTH_LONG).show()
                    binding.btnSave.isEnabled = true
                }
            }
        }
    }
}
