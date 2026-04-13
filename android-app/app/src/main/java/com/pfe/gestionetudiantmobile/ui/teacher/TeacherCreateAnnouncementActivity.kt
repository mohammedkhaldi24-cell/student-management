package com.pfe.gestionetudiantmobile.ui.teacher

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherCreateAnnouncementBinding
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import kotlinx.coroutines.launch

class TeacherCreateAnnouncementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherCreateAnnouncementBinding
    private val repository = TeacherRepository()
    private var selectedFileUri: Uri? = null

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

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseFile.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        binding.btnSave.setOnClickListener { createAnnouncement() }
    }

    private fun createAnnouncement() {
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val message = binding.etMessage.text?.toString().orEmpty().trim()
        val classeId = binding.etClasseId.text?.toString()?.trim()?.toLongOrNull()
        val filiereId = binding.etFiliereId.text?.toString()?.trim()?.toLongOrNull()

        if (title.isBlank() || message.isBlank()) {
            Toast.makeText(this, "Titre et message sont obligatoires.", Toast.LENGTH_LONG).show()
            return
        }

        if (classeId == null && filiereId == null) {
            Toast.makeText(this, "Precisez classeId ou filiereId.", Toast.LENGTH_LONG).show()
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
                    classeId = classeId,
                    filiereId = filiereId,
                    attachment = filePart
                )
            ) {
                is ApiResult.Success -> {
                    Toast.makeText(
                        this@TeacherCreateAnnouncementActivity,
                        "Annonce publiee avec succes.",
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
}
