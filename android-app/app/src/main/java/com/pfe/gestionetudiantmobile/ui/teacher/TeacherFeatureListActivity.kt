package com.pfe.gestionetudiantmobile.ui.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.AppUrlUtils
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import java.time.LocalDate
import kotlinx.coroutines.launch

class TeacherFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository = TeacherRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private var currentFeature: String = ""

    private var moduleRows: Map<Long, TeacherModuleItem> = emptyMap()
    private var noteRows: Map<Long, NoteItem> = emptyMap()
    private var absenceRows: Map<Long, AbsenceItem> = emptyMap()
    private var studentRows: Map<Long, StudentProfile> = emptyMap()
    private var assignmentRows: Map<Long, AssignmentItem> = emptyMap()
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()

    private var modulesCache: List<TeacherModuleItem> = emptyList()
    private var classesCache: List<ClasseItem> = emptyList()
    private var filiereOptions: List<FilterOption> = emptyList()

    private var selectedModuleId: Long? = null
    private var selectedClasseId: Long? = null
    private var selectedFiliereId: Long? = null
    private var selectedQuery: String? = null

    private var pendingReplaceAction: PendingReplaceAction? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val action = pendingReplaceAction
        pendingReplaceAction = null
        if (uri == null || action == null) return@registerForActivityResult
        executeReplaceAction(action, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        selectedModuleId = intent.getLongExtra(EXTRA_MODULE_ID, -1L).takeIf { it > 0 }
        selectedFiliereId = intent.getLongExtra(EXTRA_FILIERE_ID, -1L).takeIf { it > 0 }

        binding.tvTitle.text = when (currentFeature) {
            "modules" -> "Mes modules"
            "students" -> "Etudiants"
            "notes" -> "Notes"
            "absences" -> "Absences"
            "courses" -> "Cours"
            "announcements" -> "Annonces"
            "assignments" -> "Devoirs"
            else -> "Liste"
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.swipeLayout.setOnRefreshListener { loadFeature(currentFeature) }
        configureHeaderButtons()

        lifecycleScope.launch {
            loadFilterSources()
            loadFeature(currentFeature)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentFeature.isNotBlank()) {
            loadFeature(currentFeature)
        }
    }

    private fun supportsFiltering(feature: String): Boolean {
        return feature in setOf("notes", "absences", "students", "courses", "assignments")
    }

    private fun configureHeaderButtons() {
        binding.btnFilter.visibility = if (supportsFiltering(currentFeature)) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnFilter.setOnClickListener { openFilterDialog() }

        when (currentFeature) {
            "courses" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startActivity(Intent(this, TeacherCreateCourseActivity::class.java)) }
            }
            "assignments" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startActivity(Intent(this, TeacherCreateAssignmentActivity::class.java)) }
            }
            "announcements" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startActivity(Intent(this, TeacherCreateAnnouncementActivity::class.java)) }
            }
            "notes" -> {
                binding.btnAction.text = "Ajouter"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openCreateNoteDialog(null) }
            }
            "absences" -> {
                binding.btnAction.text = "Ajouter"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openCreateAbsenceDialog(null) }
            }
            else -> binding.btnAction.visibility = android.view.View.GONE
        }
    }

    private suspend fun loadFilterSources() {
        when (val modulesResult = repository.modules()) {
            is ApiResult.Success -> {
                modulesCache = modulesResult.data
                moduleRows = modulesResult.data.associateBy { it.id }
                filiereOptions = modulesResult.data
                    .mapNotNull { m -> m.filiereId?.let { FilterOption(it, m.filiereNom ?: "Filiere") } }
                    .distinctBy { it.id }
                    .sortedBy { it.label.lowercase() }
            }
            is ApiResult.Error -> showError(modulesResult.message)
        }

        when (val classesResult = repository.classes()) {
            is ApiResult.Success -> classesCache = classesResult.data.sortedBy { it.nom.lowercase() }
            is ApiResult.Error -> showError(classesResult.message)
        }
    }

    private fun loadFeature(feature: String) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true

            when (feature) {
                "modules" -> loadModules()
                "students" -> loadStudents()
                "notes" -> loadNotes()
                "absences" -> loadAbsences()
                "courses" -> loadCourses()
                "announcements" -> loadAnnouncements()
                "assignments" -> loadAssignments()
                else -> adapter.submitList(listOf(UiRow(title = "Feature inconnue", subtitle = "Aucune donnee")))
            }

            updateFilterSummary()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private suspend fun loadModules() {
        when (val result = repository.modules()) {
            is ApiResult.Success -> {
                modulesCache = result.data
                moduleRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.nom} (${it.code})",
                        subtitle = "Semestre ${it.semestre} | Filiere: ${it.filiereNom ?: "-"}",
                        badge = "Coeff ${it.coefficient}"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadStudents() {
        when (val result = repository.students(selectedModuleId, selectedClasseId, selectedFiliereId, selectedQuery)) {
            is ApiResult.Success -> {
                studentRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = it.fullName,
                        subtitle = "Matricule: ${it.matricule} | ${it.classe ?: "-"} | ${it.filiere ?: "-"}",
                        badge = it.email ?: ""
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadNotes() {
        when (val result = repository.notes(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> {
                noteRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                        subtitle = "CC: ${it.noteCc ?: "-"} | EX: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                        badge = it.statut
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadAbsences() {
        when (val result = repository.absences(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> {
                absenceRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                        subtitle = "${it.dateAbsence} | ${it.nombreHeures}h",
                        badge = if (it.justifiee) "Justifiee" else "Non justifiee"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadCourses() {
        when (val result = repository.courses(selectedModuleId)) {
            is ApiResult.Success -> {
                courseRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = it.title,
                        subtitle = "${it.moduleNom ?: "-"} | ${it.fileName}",
                        badge = if (it.filePath != null) "Document" else "Sans fichier"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private suspend fun loadAnnouncements() {
        when (val result = repository.announcements()) {
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
    }

    private suspend fun loadAssignments() {
        when (val result = repository.assignments(selectedModuleId)) {
            is ApiResult.Success -> {
                assignmentRows = result.data.associateBy { it.id }
                adapter.submitList(result.data.map {
                    UiRow(
                        id = it.id,
                        title = it.title,
                        subtitle = "Deadline: ${it.dueDate} | Fichier: ${it.attachmentName}",
                        badge = "Gerer"
                    )
                })
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private fun updateFilterSummary() {
        if (!supportsFiltering(currentFeature)) {
            binding.tvFilterSummary.visibility = android.view.View.GONE
            return
        }

        val moduleLabel = modulesCache.firstOrNull { it.id == selectedModuleId }?.nom
        val classeLabel = classesCache.firstOrNull { it.id == selectedClasseId }?.nom
        val filiereLabel = filiereOptions.firstOrNull { it.id == selectedFiliereId }?.label

        val parts = mutableListOf<String>()
        if (!moduleLabel.isNullOrBlank()) parts += "Module: $moduleLabel"
        if (!classeLabel.isNullOrBlank()) parts += "Classe: $classeLabel"
        if (currentFeature == "students" && !filiereLabel.isNullOrBlank()) parts += "Filiere: $filiereLabel"
        if (!selectedQuery.isNullOrBlank()) parts += "Recherche: ${selectedQuery!!.trim()}"

        binding.tvFilterSummary.visibility = android.view.View.VISIBLE
        binding.tvFilterSummary.text = if (parts.isEmpty()) "Aucun filtre actif" else parts.joinToString(" | ")
    }

    private fun openFilterDialog() {
        if (!supportsFiltering(currentFeature)) return

        lifecycleScope.launch {
            if (modulesCache.isEmpty() || classesCache.isEmpty()) loadFilterSources()

            val root = LinearLayout(this@TeacherFeatureListActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 24, 40, 24)
            }

            val moduleSpinner = Spinner(this@TeacherFeatureListActivity)
            val filiereSpinner = Spinner(this@TeacherFeatureListActivity)
            val classeSpinner = Spinner(this@TeacherFeatureListActivity)
            val searchInput = EditText(this@TeacherFeatureListActivity).apply {
                hint = "Rechercher etudiant (nom/matricule)"
                setText(selectedQuery.orEmpty())
            }

            val moduleOptions = mutableListOf(FilterOption(null, "Tous modules"))
            moduleOptions += modulesCache.map { FilterOption(it.id, "${it.nom} (${it.code})", it.filiereId) }
            moduleSpinner.adapter = ArrayAdapter(
                this@TeacherFeatureListActivity,
                android.R.layout.simple_spinner_dropdown_item,
                moduleOptions.map { it.label }
            )

            val filieres = mutableListOf(FilterOption(null, "Toutes filieres"))
            filieres += filiereOptions
            filiereSpinner.adapter = ArrayAdapter(
                this@TeacherFeatureListActivity,
                android.R.layout.simple_spinner_dropdown_item,
                filieres.map { it.label }
            )

            moduleSpinner.setSelection(moduleOptions.indexOfFirst { it.id == selectedModuleId }.takeIf { it >= 0 } ?: 0)
            filiereSpinner.setSelection(filieres.indexOfFirst { it.id == selectedFiliereId }.takeIf { it >= 0 } ?: 0)

            var classOptions = buildClassOptions(moduleOptions, moduleSpinner, filieres, filiereSpinner)
            classeSpinner.adapter = ArrayAdapter(
                this@TeacherFeatureListActivity,
                android.R.layout.simple_spinner_dropdown_item,
                classOptions.map { it.label }
            )
            classeSpinner.setSelection(classOptions.indexOfFirst { it.id == selectedClasseId }.takeIf { it >= 0 } ?: 0)

            val rebuildClasses = {
                classOptions = buildClassOptions(moduleOptions, moduleSpinner, filieres, filiereSpinner)
                classeSpinner.adapter = ArrayAdapter(
                    this@TeacherFeatureListActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    classOptions.map { it.label }
                )
                classeSpinner.setSelection(classOptions.indexOfFirst { it.id == selectedClasseId }.takeIf { it >= 0 } ?: 0)
            }

            moduleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val option = moduleOptions[position]
                    if (currentFeature == "students" && option.filiereId != null) {
                        val idx = filieres.indexOfFirst { it.id == option.filiereId }.takeIf { it >= 0 } ?: 0
                        filiereSpinner.setSelection(idx)
                    }
                    rebuildClasses()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            filiereSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    rebuildClasses()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            root.addView(moduleSpinner)
            if (currentFeature == "students") root.addView(filiereSpinner)
            root.addView(classeSpinner)
            root.addView(searchInput)

            AlertDialog.Builder(this@TeacherFeatureListActivity)
                .setTitle("Filtrer")
                .setView(root)
                .setNegativeButton("Annuler", null)
                .setNeutralButton("Reinitialiser") { _, _ ->
                    selectedModuleId = null
                    selectedClasseId = null
                    selectedFiliereId = null
                    selectedQuery = null
                    loadFeature(currentFeature)
                }
                .setPositiveButton("Appliquer") { _, _ ->
                    val selectedModule = moduleOptions[moduleSpinner.selectedItemPosition]
                    val selectedFiliere = filieres[filiereSpinner.selectedItemPosition]
                    val selectedClasse = classOptions[classeSpinner.selectedItemPosition]

                    selectedModuleId = selectedModule.id
                    selectedFiliereId = if (currentFeature == "students") (selectedModule.filiereId ?: selectedFiliere.id) else null
                    selectedClasseId = selectedClasse.id
                    selectedQuery = searchInput.text?.toString()?.trim()?.ifBlank { null }
                    loadFeature(currentFeature)
                }
                .show()
        }
    }

    private fun buildClassOptions(
        moduleOptions: List<FilterOption>,
        moduleSpinner: Spinner,
        filiereOptionsList: List<FilterOption>,
        filiereSpinner: Spinner
    ): MutableList<FilterOption> {
        val selectedModule = moduleOptions.getOrNull(moduleSpinner.selectedItemPosition)
        val selectedFiliere = filiereOptionsList.getOrNull(filiereSpinner.selectedItemPosition)
        val effectiveFiliere = selectedModule?.filiereId ?: selectedFiliere?.id

        val options = mutableListOf(FilterOption(null, "Toutes classes"))
        options += classesCache
            .filter { effectiveFiliere == null || it.filiereId == effectiveFiliere }
            .map { FilterOption(it.id, "${it.nom} (${it.filiereNom ?: "-"})", it.filiereId) }
            .sortedBy { it.label.lowercase() }
        return options
    }

    private fun onRowClicked(row: UiRow) {
        when (currentFeature) {
            "modules" -> onModuleRowClicked(row)
            "students" -> onStudentRowClicked(row)
            "notes" -> onNoteRowClicked(row)
            "absences" -> onAbsenceRowClicked(row)
            "courses" -> onCourseRowClicked(row)
            "announcements" -> onAnnouncementRowClicked(row)
            "assignments" -> onAssignmentRowClicked(row)
        }
    }

    private fun onModuleRowClicked(row: UiRow) {
        val module = row.id?.let { moduleRows[it] } ?: return
        val options = arrayOf("Etudiants du module", "Notes du module", "Absences du module")
        AlertDialog.Builder(this)
            .setTitle(module.nom)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Etudiants du module" -> openFeatureWithModule("students", module)
                    "Notes du module" -> openFeatureWithModule("notes", module)
                    "Absences du module" -> openFeatureWithModule("absences", module)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openFeatureWithModule(feature: String, module: TeacherModuleItem) {
        val intent = Intent(this, TeacherFeatureListActivity::class.java)
        intent.putExtra(EXTRA_FEATURE, feature)
        intent.putExtra(EXTRA_MODULE_ID, module.id)
        module.filiereId?.let { intent.putExtra(EXTRA_FILIERE_ID, it) }
        startActivity(intent)
    }

    private fun onStudentRowClicked(row: UiRow) {
        val student = row.id?.let { studentRows[it] } ?: return
        val options = mutableListOf("Voir profil")
        if (selectedModuleId != null) {
            options += "Ajouter / modifier note"
            options += "Ajouter absence"
        }

        AlertDialog.Builder(this)
            .setTitle(student.fullName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir profil" -> {
                        val msg = buildString {
                            appendLine("Matricule: ${student.matricule}")
                            appendLine("Classe: ${student.classe ?: "-"}")
                            appendLine("Filiere: ${student.filiere ?: "-"}")
                            appendLine("Email: ${student.email ?: "-"}")
                            appendLine("Telephone: ${student.telephone ?: "-"}")
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Profil etudiant")
                            .setMessage(msg)
                            .setPositiveButton("Fermer", null)
                            .show()
                    }
                    "Ajouter / modifier note" -> openCreateNoteDialog(student.id)
                    "Ajouter absence" -> openCreateAbsenceDialog(student.id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onNoteRowClicked(row: UiRow) {
        val note = row.id?.let { noteRows[it] } ?: return
        val options = arrayOf("Modifier note", "Supprimer note")
        AlertDialog.Builder(this)
            .setTitle(note.studentName ?: "Note")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Modifier note" -> openEditNoteDialog(note)
                    "Supprimer note" -> confirmAction("Supprimer note", "Confirmer la suppression de cette note ?") { deleteNote(note.id) }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onAbsenceRowClicked(row: UiRow) {
        val absence = row.id?.let { absenceRows[it] } ?: return
        val options = mutableListOf<String>()
        if (!absence.justifiee) options += "Justifier absence"
        options += "Supprimer absence"
        AlertDialog.Builder(this)
            .setTitle(absence.studentName ?: "Absence")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Justifier absence" -> openJustifyAbsenceDialog(absence.id)
                    "Supprimer absence" -> confirmAction("Supprimer absence", "Confirmer la suppression ?") { deleteAbsence(absence.id) }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openEditNoteDialog(note: NoteItem) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
        }
        val ccInput = EditText(this).apply { hint = "Note CC"; setText(note.noteCc?.toString().orEmpty()) }
        val examInput = EditText(this).apply { hint = "Note Examen"; setText(note.noteExamen?.toString().orEmpty()) }
        val semestreInput = EditText(this).apply { hint = "Semestre"; setText(note.semestre) }
        val anneeInput = EditText(this).apply { hint = "Annee academique"; setText(note.anneeAcademique) }

        root.addView(ccInput)
        root.addView(examInput)
        root.addView(semestreInput)
        root.addView(anneeInput)

        AlertDialog.Builder(this)
            .setTitle("Modifier note")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val studentId = note.studentId
                val moduleId = note.moduleId
                if (studentId == null || moduleId == null) {
                    showError("Impossible de modifier cette note.")
                    return@setPositiveButton
                }
                saveNote(
                    studentId = studentId,
                    moduleId = moduleId,
                    semestre = semestreInput.text?.toString().orEmpty().ifBlank { "S1" },
                    annee = anneeInput.text?.toString().orEmpty().ifBlank { "2024-2025" },
                    noteCc = ccInput.text?.toString()?.toDoubleOrNull(),
                    noteExamen = examInput.text?.toString()?.toDoubleOrNull()
                )
            }
            .show()
    }

    private fun openCreateNoteDialog(prefilledStudentId: Long?) {
        val moduleId = selectedModuleId
        if (moduleId == null) {
            showError("Selectionnez d'abord un module via Filtrer.")
            return
        }

        lifecycleScope.launch {
            when (val result = repository.students(moduleId, selectedClasseId, selectedFiliereId, null)) {
                is ApiResult.Success -> {
                    if (result.data.isEmpty()) {
                        showError("Aucun etudiant trouve pour ce filtre.")
                        return@launch
                    }
                    val students = result.data
                    val root = LinearLayout(this@TeacherFeatureListActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 24, 40, 24)
                    }
                    val studentSpinner = Spinner(this@TeacherFeatureListActivity)
                    studentSpinner.adapter = ArrayAdapter(
                        this@TeacherFeatureListActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        students.map { "${it.fullName} (${it.matricule})" }
                    )
                    val preIndex = students.indexOfFirst { it.id == prefilledStudentId }.takeIf { it >= 0 } ?: 0
                    studentSpinner.setSelection(preIndex)

                    val ccInput = EditText(this@TeacherFeatureListActivity).apply { hint = "Note CC" }
                    val examInput = EditText(this@TeacherFeatureListActivity).apply { hint = "Note Examen" }
                    val semestreInput = EditText(this@TeacherFeatureListActivity).apply { hint = "Semestre (S1/S2)"; setText("S1") }
                    val anneeInput = EditText(this@TeacherFeatureListActivity).apply { hint = "Annee academique"; setText("2024-2025") }
                    root.addView(studentSpinner)
                    root.addView(ccInput)
                    root.addView(examInput)
                    root.addView(semestreInput)
                    root.addView(anneeInput)

                    AlertDialog.Builder(this@TeacherFeatureListActivity)
                        .setTitle("Ajouter / modifier note")
                        .setView(root)
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Enregistrer") { _, _ ->
                            val studentId = students[studentSpinner.selectedItemPosition].id
                            saveNote(
                                studentId = studentId,
                                moduleId = moduleId,
                                semestre = semestreInput.text?.toString().orEmpty().ifBlank { "S1" },
                                annee = anneeInput.text?.toString().orEmpty().ifBlank { "2024-2025" },
                                noteCc = ccInput.text?.toString()?.toDoubleOrNull(),
                                noteExamen = examInput.text?.toString()?.toDoubleOrNull()
                            )
                        }
                        .show()
                }
                is ApiResult.Error -> showError(result.message)
            }
        }
    }

    private fun saveNote(studentId: Long,
                         moduleId: Long,
                         semestre: String,
                         annee: String,
                         noteCc: Double?,
                         noteExamen: Double?) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.upsertNote(studentId, moduleId, semestre, annee, noteCc, noteExamen)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Note enregistree.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteNote(noteId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteNote(noteId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, result.data.message, Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openCreateAbsenceDialog(prefilledStudentId: Long?) {
        val moduleId = selectedModuleId
        if (moduleId == null) {
            showError("Selectionnez d'abord un module via Filtrer.")
            return
        }

        lifecycleScope.launch {
            when (val result = repository.students(moduleId, selectedClasseId, selectedFiliereId, null)) {
                is ApiResult.Success -> {
                    if (result.data.isEmpty()) {
                        showError("Aucun etudiant trouve pour ce filtre.")
                        return@launch
                    }
                    val students = result.data
                    val root = LinearLayout(this@TeacherFeatureListActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 24, 40, 24)
                    }
                    val studentSpinner = Spinner(this@TeacherFeatureListActivity)
                    studentSpinner.adapter = ArrayAdapter(
                        this@TeacherFeatureListActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        students.map { "${it.fullName} (${it.matricule})" }
                    )
                    val preIndex = students.indexOfFirst { it.id == prefilledStudentId }.takeIf { it >= 0 } ?: 0
                    studentSpinner.setSelection(preIndex)
                    val dateInput = EditText(this@TeacherFeatureListActivity).apply {
                        hint = "Date (yyyy-MM-dd)"
                        setText(LocalDate.now().toString())
                    }
                    val hoursInput = EditText(this@TeacherFeatureListActivity).apply {
                        hint = "Nombre d'heures"
                        setText("2")
                    }
                    root.addView(studentSpinner)
                    root.addView(dateInput)
                    root.addView(hoursInput)

                    AlertDialog.Builder(this@TeacherFeatureListActivity)
                        .setTitle("Ajouter absence")
                        .setView(root)
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Enregistrer") { _, _ ->
                            val studentId = students[studentSpinner.selectedItemPosition].id
                            val date = dateInput.text?.toString()?.trim()?.ifBlank { null }
                            val hours = hoursInput.text?.toString()?.toIntOrNull()
                            saveAbsence(studentId, moduleId, date, hours)
                        }
                        .show()
                }
                is ApiResult.Error -> showError(result.message)
            }
        }
    }

    private fun saveAbsence(studentId: Long, moduleId: Long, date: String?, hours: Int?) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.createAbsence(studentId, moduleId, date, hours)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Absence enregistree.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openJustifyAbsenceDialog(absenceId: Long) {
        val input = EditText(this).apply { hint = "Motif de justification" }
        AlertDialog.Builder(this)
            .setTitle("Justifier l'absence")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Valider") { _, _ ->
                justifyAbsence(absenceId, input.text?.toString()?.trim()?.ifBlank { null })
            }
            .show()
    }

    private fun justifyAbsence(absenceId: Long, motif: String?) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.justifyAbsence(absenceId, motif)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Absence justifiee.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteAbsence(absenceId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteAbsence(absenceId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, result.data.message, Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun onCourseRowClicked(row: UiRow) {
        val course = row.id?.let { courseRows[it] } ?: return
        val options = mutableListOf<String>()
        if (course.filePath != null) {
            options += "Voir / Telecharger"
            options += "Remplacer le document"
            options += "Supprimer le document"
        } else {
            options += "Ajouter / Remplacer le document"
        }
        options += "Supprimer le cours"

        AlertDialog.Builder(this)
            .setTitle(course.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir / Telecharger" -> openExternal(course.filePath ?: return@setItems)
                    "Remplacer le document", "Ajouter / Remplacer le document" -> {
                        pendingReplaceAction = PendingReplaceAction(ReplaceType.COURSE_FILE, course.id)
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Supprimer le document" -> confirmAction(
                        title = "Supprimer le document",
                        message = "Voulez-vous supprimer le document de ce cours ?"
                    ) { removeCourseFile(course.id) }
                    "Supprimer le cours" -> confirmAction(
                        title = "Supprimer le cours",
                        message = "Cette action est irreversible. Continuer ?"
                    ) { deleteCourse(course.id) }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onAnnouncementRowClicked(row: UiRow) {
        val announcement = row.id?.let { announcementRows[it] } ?: return
        val options = mutableListOf<String>()
        if (announcement.attachmentPath != null) options += "Voir / Telecharger"
        options += "Remplacer le document"
        if (announcement.attachmentPath != null) options += "Supprimer le document"
        options += "Supprimer l'annonce"

        AlertDialog.Builder(this)
            .setTitle(announcement.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir / Telecharger" -> openExternal(announcement.attachmentPath ?: return@setItems)
                    "Remplacer le document" -> {
                        pendingReplaceAction = PendingReplaceAction(ReplaceType.ANNOUNCEMENT_ATTACHMENT, announcement.id)
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Supprimer le document" -> confirmAction(
                        title = "Supprimer le document",
                        message = "Voulez-vous supprimer le document de l'annonce ?"
                    ) { removeAnnouncementAttachment(announcement.id) }
                    "Supprimer l'annonce" -> confirmAction(
                        title = "Supprimer l'annonce",
                        message = "Cette action est irreversible. Continuer ?"
                    ) { deleteAnnouncement(announcement.id) }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun onAssignmentRowClicked(row: UiRow) {
        val assignment = row.id?.let { assignmentRows[it] } ?: return
        val options = mutableListOf("Voir soumissions")
        if (assignment.attachmentPath != null) options += "Voir / Telecharger la piece jointe"
        options += "Remplacer la piece jointe"
        if (assignment.attachmentPath != null) options += "Supprimer la piece jointe"
        options += "Supprimer le devoir"

        AlertDialog.Builder(this)
            .setTitle(assignment.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir soumissions" -> openSubmissions(assignment)
                    "Voir / Telecharger la piece jointe" -> openExternal(assignment.attachmentPath ?: return@setItems)
                    "Remplacer la piece jointe" -> {
                        pendingReplaceAction = PendingReplaceAction(ReplaceType.ASSIGNMENT_ATTACHMENT, assignment.id)
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Supprimer la piece jointe" -> confirmAction(
                        title = "Supprimer la piece jointe",
                        message = "Voulez-vous supprimer la piece jointe de ce devoir ?"
                    ) { removeAssignmentAttachment(assignment.id) }
                    "Supprimer le devoir" -> confirmAction(
                        title = "Supprimer le devoir",
                        message = "Cette action est irreversible. Continuer ?"
                    ) { deleteAssignment(assignment.id) }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun executeReplaceAction(action: PendingReplaceAction, uri: Uri) {
        val partName = when (action.type) {
            ReplaceType.COURSE_FILE -> "file"
            ReplaceType.ASSIGNMENT_ATTACHMENT, ReplaceType.ANNOUNCEMENT_ATTACHMENT -> "attachment"
        }

        val filePart = runCatching { FileUploadUtils.uriToMultipartPart(this, uri, partName) }
            .getOrElse {
                showError(it.message ?: "Erreur lors du chargement du document.")
                return
            }

        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (action.type) {
                ReplaceType.COURSE_FILE -> when (val result = repository.replaceCourseFile(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Document du cours remplace.", Toast.LENGTH_LONG).show()
                    is ApiResult.Error -> showError(result.message)
                }
                ReplaceType.ASSIGNMENT_ATTACHMENT -> when (val result = repository.replaceAssignmentAttachment(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Piece jointe du devoir remplacee.", Toast.LENGTH_LONG).show()
                    is ApiResult.Error -> showError(result.message)
                }
                ReplaceType.ANNOUNCEMENT_ATTACHMENT -> when (val result = repository.replaceAnnouncementAttachment(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Document de l'annonce remplace.", Toast.LENGTH_LONG).show()
                    is ApiResult.Error -> showError(result.message)
                }
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun removeCourseFile(courseId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.removeCourseFile(courseId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Document du cours supprime.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteCourse(courseId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteCourse(courseId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, result.data.message, Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun removeAssignmentAttachment(assignmentId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.removeAssignmentAttachment(assignmentId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Piece jointe du devoir supprimee.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun removeAnnouncementAttachment(announcementId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.removeAnnouncementAttachment(announcementId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, "Document de l'annonce supprime.", Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteAnnouncement(announcementId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteAnnouncement(announcementId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, result.data.message, Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun deleteAssignment(assignmentId: Long) {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = repository.deleteAssignment(assignmentId)) {
                is ApiResult.Success -> Toast.makeText(this@TeacherFeatureListActivity, result.data.message, Toast.LENGTH_LONG).show()
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openSubmissions(assignment: AssignmentItem) {
        val intent = Intent(this, TeacherAssignmentSubmissionsActivity::class.java)
        intent.putExtra(TeacherAssignmentSubmissionsActivity.EXTRA_ASSIGNMENT_ID, assignment.id)
        intent.putExtra(TeacherAssignmentSubmissionsActivity.EXTRA_ASSIGNMENT_TITLE, assignment.title)
        startActivity(intent)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun confirmAction(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Confirmer") { _, _ -> onConfirm() }
            .show()
    }

    private fun openExternal(url: String) {
        val target = AppUrlUtils.toAbsolute(url)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_FILIERE_ID = "extra_filiere_id"
    }
}

private data class PendingReplaceAction(
    val type: ReplaceType,
    val entityId: Long
)

private enum class ReplaceType {
    COURSE_FILE,
    ASSIGNMENT_ATTACHMENT,
    ANNOUNCEMENT_ATTACHMENT
}

private data class FilterOption(
    val id: Long?,
    val label: String,
    val filiereId: Long? = null
)
