package com.pfe.gestionetudiantmobile.ui.teacher

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.NoteBulkItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherModuleWorkspaceBinding
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationCopy
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.home.TeacherHomeActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

class TeacherModuleWorkspaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherModuleWorkspaceBinding
    private val repository by lazy { TeacherRepository(this) }
    private val noteAdapter = TeacherNoteEntryAdapter()
    private val absenceAdapter = TeacherAbsenceSessionAdapter()

    private var moduleId: Long = -1L
    private var moduleName: String = ""
    private var moduleCode: String = ""
    private var moduleSemestre: String = "S1"
    private var classeId: Long? = null
    private var classeName: String? = null
    private var filiereId: Long? = null
    private var filiereName: String? = null
    private var mode: WorkspaceMode = WorkspaceMode.NOTES

    private var classes: List<ClasseItem> = emptyList()
    private var classFilterOptions: List<ClassFilterOption> = emptyList()
    private var students: List<StudentProfile> = emptyList()
    private var notes: List<NoteItem> = emptyList()
    private var absences: List<AbsenceItem> = emptyList()
    private var studentSearchQuery: String = ""
    private var restoredAcademicYear: String? = null
    private var restoredSessionDate: String? = null
    private var restoredSessionHours: String? = null
    private var suggestedSessionDate: String? = null
    private var suggestedSessionHours: String? = null
    private var suggestedSessionLabel: String? = null
    private var suppressClassFilterEvents: Boolean = false
    private val noteDrafts = mutableMapOf<Long, NoteDraft>()
    private val absenceDrafts = mutableMapOf<Long, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherModuleWorkspaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.teacherPrimary)

        moduleId = intent.getLongExtra(EXTRA_MODULE_ID, -1L)
        if (moduleId <= 0) {
            Toast.makeText(this, "Module introuvable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        moduleName = intent.getStringExtra(EXTRA_MODULE_NAME).orEmpty()
        moduleCode = intent.getStringExtra(EXTRA_MODULE_CODE).orEmpty()
        moduleSemestre = intent.getStringExtra(EXTRA_MODULE_SEMESTRE).orEmpty().ifBlank { "S1" }
        val hasExplicitClasse = intent.hasExtra(EXTRA_CLASSE_ID)
        val hasExplicitMode = intent.hasExtra(EXTRA_INITIAL_MODE)
        val hasExplicitSession = intent.hasExtra(EXTRA_SESSION_DATE) || intent.hasExtra(EXTRA_SESSION_HOURS)
        classeId = intent.getLongExtra(EXTRA_CLASSE_ID, -1L).takeIf { it > 0 }
        classeName = intent.getStringExtra(EXTRA_CLASSE_NAME)
        filiereId = intent.getLongExtra(EXTRA_FILIERE_ID, -1L).takeIf { it > 0 }
        filiereName = intent.getStringExtra(EXTRA_FILIERE_NAME)
        mode = WorkspaceMode.from(intent.getStringExtra(EXTRA_INITIAL_MODE))
        suggestedSessionDate = intent.getStringExtra(EXTRA_SESSION_DATE)?.takeIf { it.isNotBlank() }
        suggestedSessionHours = intent.getIntExtra(EXTRA_SESSION_HOURS, -1).takeIf { it > 0 }?.toString()
        suggestedSessionLabel = intent.getStringExtra(EXTRA_SESSION_LABEL)?.takeIf { it.isNotBlank() }
        restoreWorkspaceFilters(
            hasExplicitClasse = hasExplicitClasse,
            hasExplicitMode = hasExplicitMode,
            hasExplicitSession = hasExplicitSession
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.tvModuleTitle.text = moduleName.ifBlank { "Module" }
        binding.tvModuleSubtitle.text = listOf(moduleCode, moduleSemestre, classeName, filiereName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" | ")
        binding.tvSemestre.text = moduleSemestre
        binding.inputAcademicYear.setText(restoredAcademicYear ?: defaultAcademicYear())
        binding.inputSessionDate.setText(suggestedSessionDate ?: restoredSessionDate ?: LocalDate.now().toString())
        binding.inputSessionHours.setText(suggestedSessionHours ?: restoredSessionHours ?: "2")
        binding.inputStudentSearch.setText(studentSearchQuery)

        binding.btnBack.setOnClickListener { finishWithTransition() }
        binding.swipeLayout.setOnRefreshListener { loadWorkspace() }
        binding.btnQuickSave.setOnClickListener {
            when (mode) {
                WorkspaceMode.NOTES -> saveNotes()
                WorkspaceMode.ABSENCES -> saveAbsences()
            }
        }
        binding.btnMarkAllPresent.setOnClickListener { absenceAdapter.markAllPresent() }
        binding.inputStudentSearch.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                captureVisibleDrafts()
                studentSearchQuery = s?.toString().orEmpty()
                persistWorkspaceFilters()
                applyMode()
            }
        })
        binding.inputAcademicYear.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                persistWorkspaceFilters()
            }
        })
        binding.inputSessionDate.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                persistWorkspaceFilters()
                if (mode == WorkspaceMode.ABSENCES && s?.length == 10) {
                    captureVisibleDrafts()
                    applyAbsenceEntries()
                    updateSummary()
                }
            }
        })
        binding.inputSessionHours.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                persistWorkspaceFilters()
            }
        })

        binding.modeToggle.check(
            if (mode == WorkspaceMode.NOTES) R.id.btnModeNotes else R.id.btnModeAbsences
        )
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            switchMode(if (checkedId == R.id.btnModeAbsences) WorkspaceMode.ABSENCES else WorkspaceMode.NOTES)
        }

        switchMode(mode)
        persistWorkspaceFilters()
        loadWorkspace()
    }

    private fun loadWorkspace() {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val classResult = repository.classes(moduleId, filiereId)) {
                is ApiResult.Success -> {
                    classes = classResult.data.sortedBy { it.nom.lowercase(Locale.ROOT) }
                    if (classeId != null && classes.none { it.id == classeId }) {
                        classeId = null
                        classeName = null
                    }
                    if (classeId == null && classes.size == 1) {
                        classeId = classes.first().id
                        classeName = classes.first().nom
                        persistWorkspaceFilters()
                    }
                    configureClassFilter()
                }
                is ApiResult.Error -> showError(classResult.message)
            }
            when (val studentResult = repository.students(moduleId, classeId, filiereId, null)) {
                is ApiResult.Success -> students = studentResult.data.sortedBy { it.fullName.lowercase(Locale.ROOT) }
                is ApiResult.Error -> showError(studentResult.message)
            }
            when (val noteResult = repository.notes(moduleId, classeId, null)) {
                is ApiResult.Success -> notes = noteResult.data
                is ApiResult.Error -> showError(noteResult.message)
            }
            when (val absenceResult = repository.absences(moduleId, classeId, null)) {
                is ApiResult.Success -> absences = absenceResult.data
                is ApiResult.Error -> showError(absenceResult.message)
            }
            updateSummary()
            applyMode()
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun configureClassFilter() {
        classFilterOptions = listOf(ClassFilterOption(null, "Toutes les classes")) +
            classes.map { ClassFilterOption(it.id, "${it.nom} (${it.filiereNom ?: "-"})") }

        suppressClassFilterEvents = true
        binding.spinnerClassFilter.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            classFilterOptions.map { it.label }
        )
        val selectedIndex = classFilterOptions.indexOfFirst { it.id == classeId }.takeIf { it >= 0 } ?: 0
        binding.spinnerClassFilter.setSelection(selectedIndex)
        binding.spinnerClassFilter.isEnabled = classes.size > 1 || classeId != null

        binding.spinnerClassFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressClassFilterEvents) return
                val option = classFilterOptions.getOrNull(position) ?: return
                if (option.id == classeId) return
                captureVisibleDrafts()
                classeId = option.id
                classeName = option.id?.let { selectedId -> classes.firstOrNull { it.id == selectedId }?.nom }
                persistWorkspaceFilters()
                loadWorkspace()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.spinnerClassFilter.post { suppressClassFilterEvents = false }
    }

    private fun visibleStudents(): List<StudentProfile> {
        val query = studentSearchQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return students
        return students.filter { student ->
            student.fullName.contains(query, ignoreCase = true) ||
                student.matricule.contains(query, ignoreCase = true) ||
                student.classe.orEmpty().contains(query, ignoreCase = true) ||
                student.filiere.orEmpty().contains(query, ignoreCase = true)
        }
    }

    private fun captureVisibleDrafts() {
        noteAdapter.currentEntries().forEach { entry ->
            noteDrafts[entry.student.id] = NoteDraft(entry.noteCcText, entry.noteExamText)
        }
        absenceAdapter.currentEntries().forEach { entry ->
            absenceDrafts[entry.student.id] = entry.absent
        }
    }

    private fun switchMode(nextMode: WorkspaceMode) {
        captureVisibleDrafts()
        mode = nextMode
        persistWorkspaceFilters()
        applyMode()
    }

    private fun applyMode() {
        binding.layoutNoteTools.visibility = if (mode == WorkspaceMode.NOTES) View.VISIBLE else View.GONE
        binding.layoutAbsenceTools.visibility = if (mode == WorkspaceMode.ABSENCES) View.VISIBLE else View.GONE
        binding.btnMarkAllPresent.visibility = if (mode == WorkspaceMode.ABSENCES) View.VISIBLE else View.GONE
        binding.tvWorkspaceTitle.text = if (mode == WorkspaceMode.NOTES) {
            "Saisie rapide des notes"
        } else {
            "Appel de la session"
        }
        binding.tvModeHint.text = if (mode == WorkspaceMode.NOTES) {
            "Liste chargee automatiquement. Saisissez CC/Examen puis enregistrez."
        } else {
            "Cochez uniquement les absents. Tous les autres restent presents."
        }
        binding.btnQuickSave.text = if (mode == WorkspaceMode.NOTES) {
            "Enregistrer notes"
        } else {
            "Enregistrer absences"
        }

        if (mode == WorkspaceMode.NOTES) {
            binding.recyclerView.adapter = noteAdapter
            applyNoteEntries()
        } else {
            binding.recyclerView.adapter = absenceAdapter
            applyAbsenceEntries()
        }
        updateSummary()
        configureBottomNavigation()
    }

    private fun applyNoteEntries() {
        val noteByStudent = notes
            .filter { it.studentId != null }
            .associateBy { it.studentId!! }

        noteAdapter.submitEntries(visibleStudents().map { student ->
            val note = noteByStudent[student.id]
            val draft = noteDrafts[student.id]
            TeacherNoteEntry(
                student = student,
                note = note,
                noteCcText = draft?.cc ?: note?.noteCc?.compact().orEmpty(),
                noteExamText = draft?.exam ?: note?.noteExamen?.compact().orEmpty()
            )
        })
    }

    private fun applyAbsenceEntries() {
        val sessionDate = selectedSessionDate() ?: return
        val absenceByStudent = absences
            .filter { it.studentId != null && it.dateAbsence == sessionDate }
            .associateBy { it.studentId!! }

        absenceAdapter.submitEntries(visibleStudents().map { student ->
            val absence = absenceByStudent[student.id]
            TeacherAbsenceEntry(
                student = student,
                existingAbsenceId = absence?.id,
                existingHours = absence?.nombreHeures,
                absent = absenceDrafts[student.id] ?: (absence != null)
            )
        })
    }

    private fun saveNotes() {
        lifecycleScope.launch {
            val academicYear = binding.inputAcademicYear.text?.toString()?.trim()
                ?.ifBlank { defaultAcademicYear() } ?: defaultAcademicYear()
            val entries = noteAdapter.currentEntries()
            var saved = 0
            val errors = mutableListOf<String>()
            val drafts = mutableListOf<Pair<TeacherNoteEntry, NoteBulkItem>>()

            for (entry in entries) {
                val errorCountBeforeRow = errors.size
                val cc = parseNote(entry.noteCcText, "CC", entry.student.fullName, errors)
                val exam = parseNote(entry.noteExamText, "Examen", entry.student.fullName, errors)
                if (errors.size > errorCountBeforeRow) continue

                val hasInput = entry.noteCcText.isNotBlank() || entry.noteExamText.isNotBlank()
                if (!hasInput && entry.note == null) continue
                drafts += entry to NoteBulkItem(
                    studentId = entry.student.id,
                    noteCc = cc,
                    noteExamen = exam
                )
            }

            binding.swipeLayout.isRefreshing = true
            if (errors.isEmpty() && drafts.isNotEmpty()) {
                when (val bulkResult = repository.upsertNotesBulk(
                    moduleId = moduleId,
                    semestre = moduleSemestre,
                    anneeAcademique = academicYear,
                    notes = drafts.map { it.second }
                )) {
                    is ApiResult.Success -> saved = bulkResult.data.size
                    is ApiResult.Error -> {
                        for ((entry, draft) in drafts) {
                            when (val result = repository.upsertNote(
                                studentId = draft.studentId,
                                moduleId = moduleId,
                                semestre = moduleSemestre,
                                anneeAcademique = academicYear,
                                noteCc = draft.noteCc,
                                noteExamen = draft.noteExamen,
                                cacheClasseId = classeId
                            )) {
                                is ApiResult.Success -> saved++
                                is ApiResult.Error -> errors += "${entry.student.fullName}: ${result.message}"
                            }
                        }
                    }
                }
            }

            reloadNotes()
            binding.swipeLayout.isRefreshing = false
            showSaveSummary(
                successMessage = if (saved == 0) {
                    "Aucun changement de note."
                } else {
                    AcademicNotificationCopy.success("$saved note(s) enregistree(s)")
                },
                errors = errors
            )
        }
    }

    private fun saveAbsences() {
        val sessionDate = selectedSessionDate()
        if (sessionDate == null) {
            showError("Date invalide. Format attendu: yyyy-MM-dd.")
            return
        }
        val hours = binding.inputSessionHours.text?.toString()?.trim()?.toIntOrNull()
        if (hours == null || hours <= 0) {
            showError("Nombre d'heures invalide.")
            return
        }

        lifecycleScope.launch {
            var saved = 0
            val errors = mutableListOf<String>()
            binding.swipeLayout.isRefreshing = true

            val entries = absenceAdapter.currentEntries()
            val absentStudentIds = entries.filter { it.absent }.map { it.student.id }
            when (val bulkResult = repository.saveAbsenceSession(
                moduleId = moduleId,
                classeId = classeId,
                dateAbsence = sessionDate.toString(),
                nombreHeures = hours,
                absentStudentIds = absentStudentIds
            )) {
                is ApiResult.Success -> {
                    saved = entries.count { entry ->
                        val currentAbsent = entry.absent
                        val wasAbsent = entry.existingAbsenceId != null
                        currentAbsent != wasAbsent || (currentAbsent && entry.existingHours != null && entry.existingHours != hours)
                    }
                }
                is ApiResult.Error -> {
                    for (entry in entries) {
                        val existingId = entry.existingAbsenceId
                        if (entry.absent) {
                            if (existingId == null) {
                                when (val result = repository.createAbsence(
                                    entry.student.id,
                                    moduleId,
                                    sessionDate.toString(),
                                    hours,
                                    cacheClasseId = classeId
                                )) {
                                    is ApiResult.Success -> saved++
                                    is ApiResult.Error -> errors += "${entry.student.fullName}: ${result.message}"
                                }
                            } else if (entry.existingHours != null && entry.existingHours != hours) {
                                when (val deleteResult = repository.deleteAbsence(existingId)) {
                                    is ApiResult.Success -> Unit
                                    is ApiResult.Error -> {
                                        errors += "${entry.student.fullName}: ${deleteResult.message}"
                                        continue
                                    }
                                }
                                when (val createResult = repository.createAbsence(
                                    entry.student.id,
                                    moduleId,
                                    sessionDate.toString(),
                                    hours,
                                    cacheClasseId = classeId
                                )) {
                                    is ApiResult.Success -> saved++
                                    is ApiResult.Error -> errors += "${entry.student.fullName}: ${createResult.message}"
                                }
                            }
                        } else if (existingId != null) {
                            when (val result = repository.deleteAbsence(existingId)) {
                                is ApiResult.Success -> saved++
                                is ApiResult.Error -> errors += "${entry.student.fullName}: ${result.message}"
                            }
                        }
                    }
                }
            }

            reloadAbsences()
            binding.swipeLayout.isRefreshing = false
            showSaveSummary(
                successMessage = if (saved == 0) {
                    "Aucun changement d'absence."
                } else {
                    AcademicNotificationCopy.success("$saved changement(s) d'absence enregistre(s)")
                },
                errors = errors
            )
        }
    }

    private suspend fun reloadNotes() {
        when (val result = repository.notes(moduleId, classeId, null)) {
            is ApiResult.Success -> {
                notes = result.data
                noteDrafts.clear()
            }
            is ApiResult.Error -> showError(result.message)
        }
        applyNoteEntries()
    }

    private suspend fun reloadAbsences() {
        when (val result = repository.absences(moduleId, classeId, null)) {
            is ApiResult.Success -> {
                absences = result.data
                absenceDrafts.clear()
            }
            is ApiResult.Error -> showError(result.message)
        }
        applyAbsenceEntries()
    }

    private fun parseNote(raw: String, label: String, studentName: String, errors: MutableList<String>): Double? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val value = text.toNoteValue()
        if (value == null || value < 0.0 || value > 20.0) {
            errors += "$studentName: note $label invalide"
            return null
        }
        return value
    }

    private fun updateSummary() {
        val classLabel = classeId
            ?.let { selectedId -> classes.firstOrNull { it.id == selectedId }?.nom }
            ?: when {
                classes.isEmpty() -> "classe non definie"
                classes.size == 1 -> classes.first().nom
                else -> "${classes.size} classes"
            }
        val visibleCount = visibleStudents().size
        val totalLabel = if (visibleCount == students.size) {
            "$visibleCount etudiants"
        } else {
            "$visibleCount/${students.size} etudiants"
        }
        binding.tvWorkspaceSummary.text = "$classLabel | $totalLabel"
        binding.tvActiveFilters.text = activeFilterLabel()
    }

    private fun activeFilterLabel(): String {
        val parts = mutableListOf<String>()
        parts += "Module: ${moduleName.ifBlank { moduleCode.ifBlank { "Module" } }}"
        classeId
            ?.let { selectedId -> classes.firstOrNull { it.id == selectedId }?.nom }
            ?.let { parts += "Classe: $it" }
            ?: parts.add("Classe: Toutes")
        if (mode == WorkspaceMode.ABSENCES) {
            parts += "Date: ${binding.inputSessionDate.text?.toString()?.trim().orEmpty().ifBlank { LocalDate.now().toString() }}"
            suggestedSessionLabel?.let { parts += "Session: $it" }
        }
        studentSearchQuery.trim().takeIf { it.isNotBlank() }?.let { parts += "Recherche: $it" }
        return parts.joinToString(" | ")
    }

    private fun selectedSessionDate(): LocalDate? {
        return runCatching {
            LocalDate.parse(binding.inputSessionDate.text?.toString()?.trim().orEmpty())
        }.getOrNull()
    }

    private fun defaultAcademicYear(): String {
        val today = LocalDate.now()
        val startYear = if (today.monthValue >= 9) today.year else today.year - 1
        return "$startYear-${startYear + 1}"
    }

    private fun restoreWorkspaceFilters(
        hasExplicitClasse: Boolean,
        hasExplicitMode: Boolean,
        hasExplicitSession: Boolean
    ) {
        val prefs = getSharedPreferences(WORKSPACE_PREFS, MODE_PRIVATE)
        if (!hasExplicitClasse) {
            classeId = prefs.getLongOrNull(workspaceKey("classeId"))
        }
        if (!hasExplicitMode) {
            mode = WorkspaceMode.from(prefs.getString(workspaceKey("mode"), mode.name.lowercase(Locale.ROOT)))
        }
        studentSearchQuery = prefs.getString(workspaceKey("query"), null).orEmpty()
        restoredAcademicYear = prefs.getString(workspaceKey("academicYear"), null)?.takeIf { it.isNotBlank() }
        if (!hasExplicitSession) {
            restoredSessionDate = prefs.getString(workspaceKey("sessionDate"), null)?.takeIf { it.isNotBlank() }
            restoredSessionHours = prefs.getString(workspaceKey("sessionHours"), null)?.takeIf { it.isNotBlank() }
        }
    }

    private fun persistWorkspaceFilters() {
        val editor = getSharedPreferences(WORKSPACE_PREFS, MODE_PRIVATE).edit()
        editor.putLongOrRemove(workspaceKey("classeId"), classeId)
        editor.putString(workspaceKey("mode"), mode.name.lowercase(Locale.ROOT))
        editor.putStringOrRemove(workspaceKey("query"), studentSearchQuery.trim().takeIf { it.isNotBlank() })
        editor.putStringOrRemove(
            workspaceKey("academicYear"),
            binding.inputAcademicYear.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        )
        editor.putStringOrRemove(
            workspaceKey("sessionDate"),
            binding.inputSessionDate.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        )
        editor.putStringOrRemove(
            workspaceKey("sessionHours"),
            binding.inputSessionHours.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        )
        editor.apply()
    }

    private fun workspaceKey(name: String): String = "module.$moduleId.$name"

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? {
        val value = getLong(key, -1L)
        return value.takeIf { it > 0 }
    }

    private fun android.content.SharedPreferences.Editor.putLongOrRemove(
        key: String,
        value: Long?
    ): android.content.SharedPreferences.Editor {
        return if (value != null && value > 0L) putLong(key, value) else remove(key)
    }

    private fun android.content.SharedPreferences.Editor.putStringOrRemove(
        key: String,
        value: String?
    ): android.content.SharedPreferences.Editor {
        return if (value.isNullOrBlank()) remove(key) else putString(key, value)
    }

    private fun showSaveSummary(successMessage: String, errors: List<String>) {
        if (errors.isEmpty()) {
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, errors.take(2).joinToString("\n"), Toast.LENGTH_LONG).show()
        }
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.TEACHER,
            currentFeature = if (mode == WorkspaceMode.NOTES) "notes" else "absences",
            onDashboard = {
                goDashboard()
            },
            onFeature = { feature ->
                when (feature) {
                    "notes" -> {
                        binding.modeToggle.check(R.id.btnModeNotes)
                        switchMode(WorkspaceMode.NOTES)
                    }
                    "absences" -> {
                        binding.modeToggle.check(R.id.btnModeAbsences)
                        switchMode(WorkspaceMode.ABSENCES)
                    }
                    else -> {
                        startActivity(
                            Intent(this, TeacherFeatureListActivity::class.java)
                                .putExtra(TeacherFeatureListActivity.EXTRA_FEATURE, feature)
                        )
                        finishWithTransition()
                    }
                }
            },
            onProfile = {
                startActivity(
                    Intent(this, TeacherFeatureListActivity::class.java)
                        .putExtra(TeacherFeatureListActivity.EXTRA_FEATURE, "profile")
                )
                finishWithTransition()
            }
        )
    }

    private fun goDashboard() {
        startActivity(
            Intent(this, TeacherHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finishWithTransition()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun Double.compact(): String {
        return String.format(Locale.ROOT, "%.2f", this)
            .trimEnd('0')
            .trimEnd('.')
    }

    companion object {
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_MODULE_NAME = "extra_module_name"
        const val EXTRA_MODULE_CODE = "extra_module_code"
        const val EXTRA_MODULE_SEMESTRE = "extra_module_semestre"
        const val EXTRA_CLASSE_ID = "extra_classe_id"
        const val EXTRA_CLASSE_NAME = "extra_classe_name"
        const val EXTRA_FILIERE_ID = "extra_filiere_id"
        const val EXTRA_FILIERE_NAME = "extra_filiere_name"
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"
        const val EXTRA_SESSION_DATE = "extra_session_date"
        const val EXTRA_SESSION_HOURS = "extra_session_hours"
        const val EXTRA_SESSION_LABEL = "extra_session_label"
        private const val WORKSPACE_PREFS = "teacher_workspace_filters"
    }
}

private data class ClassFilterOption(
    val id: Long?,
    val label: String
)

private data class NoteDraft(
    val cc: String,
    val exam: String
)

private enum class WorkspaceMode {
    NOTES,
    ABSENCES;

    companion object {
        fun from(value: String?): WorkspaceMode {
            return if (value.equals("absences", ignoreCase = true)) ABSENCES else NOTES
        }
    }
}
