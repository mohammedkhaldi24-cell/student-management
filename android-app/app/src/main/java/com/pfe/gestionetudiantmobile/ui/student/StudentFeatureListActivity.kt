package com.pfe.gestionetudiantmobile.ui.student

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import com.pfe.gestionetudiantmobile.data.model.StudentModuleItem
import com.pfe.gestionetudiantmobile.data.model.SubmissionFileItem
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.StudentRepository
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.common.AcademicHistoryTimelineUi
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationCopy
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationStatus
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUi
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUiItem
import com.pfe.gestionetudiantmobile.ui.common.FeatureStateController
import com.pfe.gestionetudiantmobile.ui.common.NotificationCenterUi
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.common.ProfileUi
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.AcademicStatisticsActivity
import com.pfe.gestionetudiantmobile.ui.home.StudentHomeActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.AuthenticatedFileOpener
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import com.pfe.gestionetudiantmobile.util.NotificationReadStore
import com.pfe.gestionetudiantmobile.util.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class StudentFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository by lazy { StudentRepository(this) }
    private val authRepository = AuthRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private lateinit var stateController: FeatureStateController
    private lateinit var sessionStore: SessionStore
    private lateinit var notificationReadStore: NotificationReadStore
    private var currentFeature: String = ""
    private var assignmentRows: Map<Long, AssignmentItem> = emptyMap()
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()
    private var moduleRows: Map<Long, StudentModuleItem> = emptyMap()
    private var assignmentModuleRows: Map<Long, AssignmentModuleGroup> = emptyMap()
    private var moduleResourceRows: Map<Long, StudentModuleResourceRow> = emptyMap()
    private var notificationRows: Map<Long, NotificationItem> = emptyMap()
    private var historyRows: Map<Long, AcademicHistoryEvent> = emptyMap()
    private var pendingSubmissionDraft: SubmissionDraftUi? = null
    private var assignmentFilter: String = "all"
    private var moduleOptions: List<StudentModuleItem> = emptyList()
    private var selectedModuleId: Long? = null
    private val assignmentDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.FRENCH)

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val activeDraft = pendingSubmissionDraft ?: return@registerForActivityResult
        if (uris.isEmpty()) {
            return@registerForActivityResult
        }
        val freshUris = uris.filterNot { uri -> activeDraft.selectedUris.contains(uri) }
        if (freshUris.isEmpty()) {
            Toast.makeText(this, "Ces fichiers sont deja dans la selection.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        activeDraft.selectedUris.addAll(freshUris)
        renderSubmissionDraftFiles(activeDraft)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = SessionStore(this)
        notificationReadStore = NotificationReadStore(this, sessionStore.getUser()?.id)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        stateController = FeatureStateController(binding) { loadFeature(currentFeature) }

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        selectedModuleId = intent.takeIf { it.hasExtra(EXTRA_MODULE_ID) }?.getLongExtra(EXTRA_MODULE_ID, -1L)
            ?.takeIf { it > 0L }
        binding.tvTitle.text = when (currentFeature) {
            "modules" -> "Mes modules"
            "module_overview" -> intent.getStringExtra(EXTRA_MODULE_NAME)?.takeIf { it.isNotBlank() } ?: "Module"
            "notes" -> "Mes notes"
            "absences" -> "Mes absences"
            "timetable" -> "Mon emploi du temps"
            "courses" -> "Mes cours"
            "announcements" -> "Mes annonces"
            "assignments" -> "Mes devoirs"
            "history" -> "Historique"
            "notifications" -> "Notifications"
            "profile" -> "Mon profil"
            else -> "Liste"
        }
        configureHeaderControls()
        configureNotificationHint()
        configureBottomNavigation()

        binding.btnBack.setOnClickListener { goDashboard() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goDashboard()
            }
        })

        binding.swipeLayout.setOnRefreshListener {
            lifecycleScope.launch {
                loadStudentModulesIfNeeded()
                loadFeature(currentFeature)
            }
        }

        lifecycleScope.launch {
            loadStudentModulesIfNeeded()
            loadFeature(currentFeature)
        }
    }

    private suspend fun loadStudentModulesIfNeeded() {
        if (!needsStudentModuleOptions() || moduleOptions.isNotEmpty()) {
            return
        }
        when (val result = repository.modules()) {
            is ApiResult.Success -> {
                moduleOptions = result.data.sortedBy { it.nom.lowercase() }
                when (currentFeature) {
                    "assignments" -> {
                        renderAssignmentModuleSelector()
                        updateAssignmentFilterSummary()
                    }
                    "courses" -> {
                        renderCourseModuleSelector()
                        updateCourseFilterSummary()
                    }
                    else -> updateModuleFilterSummary()
                }
            }
            is ApiResult.Error -> showError(result.message)
        }
    }

    private fun loadFeature(feature: String) {
        lifecycleScope.launch {
            val refreshing = binding.swipeLayout.isRefreshing
            binding.swipeLayout.isRefreshing = true
            if (!refreshing) {
                stateController.showLoading("Chargement de ${binding.tvTitle.text.toString().lowercase(Locale.ROOT)}...")
            }

            when (feature) {
                "modules" -> when (val result = repository.modules()) {
                    is ApiResult.Success -> {
                        moduleOptions = result.data.sortedBy { it.nom.lowercase() }
                        moduleRows = moduleOptions.associateBy { it.id }
                        submitRows(moduleOptions.map {
                            UiRow(
                                id = it.id,
                                title = "${it.nom} (${it.code})",
                                subtitle = "Cours, devoirs et annonces | Semestre: ${it.semestre ?: "-"} | Prof: ${it.teacherName ?: "-"}",
                                badge = "Ouvrir"
                            )
                        })
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "module_overview" -> loadModuleOverview()

                "notes" -> when (val result = repository.notes(selectedModuleId)) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            title = "${it.moduleNom ?: "Module"} (${it.semestre})",
                            subtitle = "CC: ${it.noteCc ?: "-"} | Examen: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                            badge = it.statut
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "absences" -> when (val result = repository.absences(selectedModuleId)) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            title = "${it.moduleNom ?: "Module"} - ${it.dateAbsence}",
                            subtitle = "${it.nombreHeures}h | ${if (it.justifiee) "Justifiee" else "Non justifiee"}",
                            badge = if (it.justifiee) "OK" else "A traiter"
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "history" -> {
                    if (selectedModuleId == null) {
                        showHistoryModuleSelection()
                    } else {
                        when (val result = repository.history(selectedModuleId)) {
                            is ApiResult.Success -> {
                                historyRows = result.data.mapIndexed { index, event -> index.toLong() to event }.toMap()
                                binding.tvFilterSummary.visibility = View.VISIBLE
                                binding.tvFilterSummary.text = AcademicHistoryTimelineUi.summary(result.data)
                                submitRows(AcademicHistoryTimelineUi.rows(result.data) { index -> index.toLong() })
                            }
                            is ApiResult.Error -> showLoadError(result.message)
                        }
                    }
                }

                "timetable" -> when (val result = repository.timetable()) {
                    is ApiResult.Success -> submitRows(result.data.map {
                        UiRow(
                            title = "${it.jour} ${it.heureDebut} - ${it.heureFin}",
                            subtitle = "${it.moduleNom ?: "Module"} | Salle ${it.salle}",
                            badge = if (it.valide) "Valide" else "Brouillon"
                        )
                    })
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "courses" -> when (val result = repository.courses(selectedModuleId)) {
                    is ApiResult.Success -> {
                        renderCourses(result.data)
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

                "notifications" -> when (val result = repository.notifications()) {
                    is ApiResult.Success -> {
                        renderNotifications(result.data)
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "profile" -> when (val result = repository.profile()) {
                    is ApiResult.Success -> {
                        val profile = result.data
                        submitRows(ProfileUi.studentRows(profile, sessionStore.getUser()))
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                "assignments" -> when (val result = repository.assignments(assignmentFilter, selectedModuleId)) {
                    is ApiResult.Success -> {
                        renderAssignments(result.data)
                    }
                    is ApiResult.Error -> showLoadError(result.message)
                }

                else -> stateController.showEmpty(
                    title = "Section indisponible",
                    message = "Cette section mobile n'est pas encore disponible.",
                    icon = "?"
                )
            }

            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun renderNotifications(items: List<NotificationItem>) {
        val sorted = NotificationCenterUi.sortedItems(items)
        notificationRows = sorted.mapIndexed { index, item -> index.toLong() to item }.toMap()
        updateNotificationSummary(sorted)
        submitRows(NotificationCenterUi.rows(sorted, notificationReadStore) { index -> index.toLong() })
    }

    private suspend fun loadModuleOverview() {
        val moduleId = selectedModuleId
        if (moduleId == null) {
            showLoadError("Module introuvable.")
            return
        }

        val module = selectedStudentModule()
        if (module != null) {
            binding.tvTitle.text = module.nom
        }
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = "Chargement automatique des cours, devoirs et annonces"

        val (coursesResult, assignmentsResult, announcementsResult) = coroutineScope {
            val courses = async { repository.courses(moduleId) }
            val assignments = async { repository.assignments("all", moduleId) }
            val announcements = async { repository.announcements() }
            Triple(courses.await(), assignments.await(), announcements.await())
        }
        val rows = mutableListOf<UiRow>()
        val resourceRows = mutableMapOf<Long, StudentModuleResourceRow>()
        val errors = mutableListOf<String>()
        var rowId = 1L

        when (coursesResult) {
            is ApiResult.Success -> {
                val courses = coursesResult.data.sortedWith(
                    compareByDescending<CourseItem> { it.createdAt ?: LocalDateTime.MIN }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
                rows += UiRow(
                    title = "Cours",
                    subtitle = "${courses.size} support(s) publie(s) pour ce module",
                    badge = "Cours",
                    icon = "CRS"
                )
                courses.forEach { course ->
                    val id = rowId++
                    resourceRows[id] = StudentModuleResourceRow(course = course)
                    rows += CourseDocumentUi.courseRow(course).copy(id = id)
                }
            }
            is ApiResult.Error -> errors += "Cours: ${coursesResult.message}"
        }

        when (assignmentsResult) {
            is ApiResult.Success -> {
                val assignments = assignmentsResult.data.sortedWith(
                    compareBy<AssignmentItem> { it.dueDate }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
                rows += UiRow(
                    title = "Devoirs",
                    subtitle = "${assignments.size} devoir(s) pour ce module",
                    badge = "Devoirs",
                    icon = "DEV"
                )
                assignments.forEach { assignment ->
                    val status = assignmentStatusUi(assignment)
                    val id = rowId++
                    resourceRows[id] = StudentModuleResourceRow(assignment = assignment)
                    rows += UiRow(
                        id = id,
                        title = assignment.title,
                        subtitle = assignmentListSubtitle(assignment, status),
                        badge = status.label,
                        icon = "DEV"
                    )
                }
            }
            is ApiResult.Error -> errors += "Devoirs: ${assignmentsResult.message}"
        }

        when (announcementsResult) {
            is ApiResult.Success -> {
                val announcements = filterAnnouncementsForModule(announcementsResult.data, module).sortedWith(
                    compareByDescending<AnnouncementItem> { it.createdAt ?: LocalDateTime.MIN }
                        .thenBy { it.title.lowercase(Locale.ROOT) }
                )
                rows += UiRow(
                    title = "Annonces",
                    subtitle = "${announcements.size} annonce(s) pertinente(s) pour ce module",
                    badge = "Annonces",
                    icon = "ANN"
                )
                announcements.forEach { announcement ->
                    val id = rowId++
                    resourceRows[id] = StudentModuleResourceRow(announcement = announcement)
                    rows += UiRow(
                        id = id,
                        title = announcement.title,
                        subtitle = announcementListSubtitle(announcement),
                        badge = announcementDateLabel(announcement),
                        icon = "ANN"
                    )
                }
            }
            is ApiResult.Error -> errors += "Annonces: ${announcementsResult.message}"
        }

        moduleResourceRows = resourceRows
        binding.tvFilterSummary.text = buildModuleOverviewSummary(module, resourceRows)

        if (rows.none { it.id != null } && errors.isNotEmpty()) {
            showLoadError(errors.joinToString("\n"))
            return
        }
        submitRows(rows)
    }

    private fun buildModuleOverviewSummary(
        module: StudentModuleItem?,
        resources: Map<Long, StudentModuleResourceRow>
    ): String {
        val courseCount = resources.values.count { it.course != null }
        val assignmentCount = resources.values.count { it.assignment != null }
        val announcementCount = resources.values.count { it.announcement != null }
        val moduleLabel = module?.let { "${it.nom} (${it.code})" } ?: "Module selectionne"
        return "$moduleLabel | $courseCount cours | $assignmentCount devoir(s) | $announcementCount annonce(s)"
    }

    private fun showHistoryModuleSelection() {
        moduleRows = moduleOptions.associateBy { it.id }
        submitRows(moduleOptions.map {
            UiRow(
                id = it.id,
                title = "${it.nom} (${it.code})",
                subtitle = "Choisir ce module pour voir les notes et absences recentes | Prof: ${it.teacherName ?: "-"}",
                badge = it.semestre ?: "Module",
                icon = "HIS"
            )
        })
    }

    private fun submitRows(rows: List<UiRow>) {
        stateController.showRows(
            adapter = adapter,
            rows = rows,
            emptyTitle = emptyTitleForFeature(),
            emptyMessage = emptyMessageForFeature(),
            emptyIcon = emptyIconForFeature()
        )
    }

    private fun renderCourses(courses: List<CourseItem>) {
        courseRows = courses.associateBy { it.id }
        renderCourseModuleSelector()
        updateCourseFilterSummary(courses.size)
        submitRows(CourseDocumentUi.rowsByModule(courses))
    }

    private fun renderAssignments(assignments: List<AssignmentItem>) {
        renderAssignmentModuleSelector()

        if (selectedModuleId == null) {
            assignmentRows = emptyMap()
            val groups = assignmentModuleGroups(assignments)
            assignmentModuleRows = groups.associateBy { it.moduleId }
            updateAssignmentFilterSummary(assignments.size)
            submitRows(groups.map { group -> assignmentModuleRow(group) })
            return
        }

        assignmentModuleRows = emptyMap()
        val sortedAssignments = assignments.sortedWith(
            compareBy<AssignmentItem> { it.dueDate }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        assignmentRows = sortedAssignments.associateBy { it.id }
        updateAssignmentFilterSummary(sortedAssignments.size)
        submitRows(sortedAssignments.map { assignment -> assignmentRow(assignment) })
    }

    private fun showLoadError(message: String) {
        stateController.showError(
            message = message,
            title = "Chargement impossible",
            retryVisible = true
        )
    }

    private fun emptyTitleForFeature(): String {
        return when (currentFeature) {
            "modules" -> "Aucun module"
            "module_overview" -> "Aucun contenu"
            "notes" -> "Aucune note"
            "absences" -> "Aucune absence"
            "timetable" -> "Aucune seance"
            "courses" -> "Aucun cours"
            "announcements" -> "Aucune annonce"
            "assignments" -> "Aucun devoir"
            "history" -> if (selectedModuleId == null) "Choisissez un module" else "Aucun historique"
            "notifications" -> "Aucune notification"
            "profile" -> "Profil indisponible"
            else -> "Aucune donnee"
        }
    }

    private fun emptyMessageForFeature(): String {
        return when (currentFeature) {
            "notes" -> "Vos notes apparaitront ici des leur publication."
            "module_overview" -> "Aucun cours, devoir ou annonce n'est encore disponible pour ce module."
            "absences" -> "Les absences enregistrees apparaitront ici."
            "courses" -> if (selectedModuleId == null) {
                "Les documents de cours publies par vos enseignants apparaitront ici."
            } else {
                "Aucun cours publie pour le module selectionne."
            }
            "announcements" -> "Les annonces de votre classe ou filiere apparaitront ici."
            "assignments" -> "Aucun devoir ne correspond au filtre selectionne."
            "history" -> if (selectedModuleId == null) {
                "Selectionnez un module pour voir les notes et absences recentes."
            } else {
                "Aucune action de note ou d'absence n'est disponible pour ce module."
            }
            "notifications" -> "Les evenements academiques recents apparaitront ici."
            else -> "Tirez vers le bas pour actualiser ou revenez plus tard."
        }
    }

    private fun emptyIconForFeature(): String {
        return when (currentFeature) {
            "notes" -> "20"
            "module_overview" -> "MOD"
            "absences" -> "ABS"
            "courses" -> "CRS"
            "assignments" -> "DEV"
            "history" -> "HIS"
            "notifications" -> "NOT"
            "profile" -> "PRO"
            else -> "VID"
        }
    }

    private fun assignmentListSubtitle(assignment: AssignmentItem, status: AssignmentStatusUi): String {
        return buildString {
            appendLine(deadlineLine(assignment))
            appendLine("Status: ${status.description}")
            append("${assignment.moduleNom ?: "Module"} | ${assignment.teacherName ?: "Enseignant"}")
            assignmentShortDescription(assignment)?.let {
                appendLine()
                append(it)
            }
        }
    }

    private fun assignmentRow(assignment: AssignmentItem): UiRow {
        val status = assignmentStatusUi(assignment)
        return UiRow(
            id = assignment.id,
            title = assignment.title,
            subtitle = assignmentListSubtitle(assignment, status),
            badge = status.label,
            icon = "DEV"
        )
    }

    private fun assignmentModuleGroups(assignments: List<AssignmentItem>): List<AssignmentModuleGroup> {
        return assignments
            .filter { it.moduleId != null }
            .groupBy { it.moduleId!! }
            .map { (moduleId, moduleAssignments) ->
                val module = moduleOptions.firstOrNull { it.id == moduleId }
                AssignmentModuleGroup(
                    moduleId = moduleId,
                    moduleName = module?.nom ?: moduleAssignments.firstOrNull()?.moduleNom ?: "Module",
                    moduleCode = module?.code ?: moduleAssignments.firstOrNull()?.moduleCode,
                    assignments = moduleAssignments.sortedWith(
                        compareBy<AssignmentItem> { it.dueDate }
                            .thenBy { it.title.lowercase(Locale.ROOT) }
                    )
                )
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun assignmentModuleRow(group: AssignmentModuleGroup): UiRow {
        return UiRow(
            id = group.moduleId,
            title = group.label,
            subtitle = listOf(
                "${group.assignments.size} devoir(s)",
                assignmentModuleStatusSummary(group.assignments),
                "Touchez pour afficher les devoirs de ce module"
            ).joinToString("\n"),
            badge = "${group.assignments.size}",
            icon = "MOD"
        )
    }

    private fun assignmentModuleStatusSummary(assignments: List<AssignmentItem>): String {
        val statuses = assignments.map { assignmentStatusUi(it).label }
        val pending = statuses.count { it == "Pending" }
        val late = statuses.count { it == "Late" }
        val submitted = statuses.count { it == "Submitted" }
        val reviewed = statuses.count { it == "Reviewed" }
        return listOfNotNull(
            pending.takeIf { it > 0 }?.let { "$it pending" },
            late.takeIf { it > 0 }?.let { "$it late" },
            submitted.takeIf { it > 0 }?.let { "$it submitted" },
            reviewed.takeIf { it > 0 }?.let { "$it reviewed" }
        ).joinToString(" | ").ifBlank { "Aucun statut actif" }
    }

    private fun filterAnnouncementsForModule(
        announcements: List<AnnouncementItem>,
        module: StudentModuleItem?
    ): List<AnnouncementItem> {
        if (module == null) {
            return announcements
        }
        val filtered = announcements.filter { announcement ->
            val classMatches = announcement.targetClasseId != null && announcement.targetClasseId == module.classeId
            val filiereMatches = announcement.targetFiliereId != null && announcement.targetFiliereId == module.filiereId
            val broadAnnouncement = announcement.targetClasseId == null && announcement.targetFiliereId == null
            classMatches || filiereMatches || broadAnnouncement
        }
        return filtered.ifEmpty { announcements }
    }

    private fun announcementListSubtitle(announcement: AnnouncementItem): String {
        return buildString {
            appendLine("Date: ${announcement.createdAt?.let { formatDateTime(it) } ?: "-"}")
            appendLine("Auteur: ${announcement.authorName ?: "Administration"}")
            append(announcementShortMessage(announcement))
            if (!announcement.attachmentPath.isNullOrBlank()) {
                appendLine()
                append("Piece jointe: ${announcement.attachmentName.ifBlank { "document" }}")
            }
        }
    }

    private fun announcementShortMessage(announcement: AnnouncementItem): String {
        val clean = announcement.message
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "Aucun message."
        return if (clean.length <= 120) clean else "${clean.take(117).trimEnd()}..."
    }

    private fun announcementDateLabel(announcement: AnnouncementItem): String {
        return announcement.createdAt?.toLocalDate()?.format(DateTimeFormatter.ofPattern("dd MMM", Locale.FRENCH))
            ?: "Annonce"
    }

    private fun assignmentShortDescription(assignment: AssignmentItem): String? {
        return assignment.description
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { if (it.length > 120) "${it.take(117)}..." else it }
    }

    private fun assignmentStatusUi(assignment: AssignmentItem): AssignmentStatusUi {
        val status = assignment.submissionStatus.trim().uppercase(Locale.ROOT)
        return when {
            status == "REVIEWED" || status == "GRADED" -> AssignmentStatusUi(
                label = "Reviewed",
                description = if (assignment.score != null) {
                    "Reviewed - note: ${assignment.score} /20"
                } else {
                    "Reviewed - feedback available"
                }
            )
            status == "LATE" || status == "OVERDUE" ||
                assignment.lateSubmission ||
                (!hasAssignmentSubmission(assignment) && assignment.overdue) -> AssignmentStatusUi(
                label = "Late",
                description = if (!hasAssignmentSubmission(assignment)) {
                    "Late - deadline passed without submission"
                } else {
                    "Late - submitted after deadline"
                }
            )
            hasAssignmentSubmission(assignment) -> AssignmentStatusUi(
                label = "Submitted",
                description = "Submitted${assignment.submittedAt?.let { " on ${formatDateTime(it)}" }.orEmpty()}"
            )
            else -> AssignmentStatusUi(
                label = "Pending",
                description = "Pending - no submission yet"
            )
        }
    }

    private fun hasAssignmentSubmission(assignment: AssignmentItem): Boolean {
        val status = assignment.submissionStatus.trim().uppercase(Locale.ROOT)
        return assignment.submittedAt != null ||
            assignment.lateSubmission ||
            status in setOf("SUBMITTED", "TURNED_IN", "SENT", "REVIEWED", "GRADED")
    }

    private fun deadlineLine(assignment: AssignmentItem): String {
        return "Deadline: ${formatDateTime(assignment.dueDate)} (${deadlineRelativeLabel(assignment.dueDate)})"
    }

    private fun deadlineRelativeLabel(deadline: LocalDateTime): String {
        val now = LocalDateTime.now()
        return when {
            deadline.isBefore(now) -> "passed"
            deadline.toLocalDate() == now.toLocalDate() -> "today"
            deadline.toLocalDate() == now.toLocalDate().plusDays(1) -> "tomorrow"
            else -> "${java.time.Duration.between(now, deadline).toDays().coerceAtLeast(1)} days left"
        }
    }

    private fun formatDateTime(value: LocalDateTime): String {
        return value.format(assignmentDateFormatter)
    }

    private fun canSubmitAssignment(assignment: AssignmentItem): Boolean {
        return !hasAssignmentSubmission(assignment) || LocalDateTime.now().isBefore(assignment.dueDate)
    }

    private fun readableSubmissionStatus(status: String, late: Boolean): String {
        val normalized = status.trim().uppercase(Locale.ROOT)
        return when {
            normalized == "REVIEWED" || normalized == "GRADED" -> "Reviewed"
            late || normalized == "LATE" || normalized == "OVERDUE" -> "Late"
            normalized in setOf("SUBMITTED", "TURNED_IN", "SENT") -> "Submitted"
            else -> "Pending"
        }
    }

    private fun onRowClicked(row: UiRow) {
        if (currentFeature == "modules") {
            val module = row.id?.let { moduleRows[it] } ?: return
            openModuleOverview(module)
            return
        }

        if (currentFeature == "module_overview") {
            val resource = row.id?.let { moduleResourceRows[it] } ?: return
            resource.course?.let { openCourseActions(it) }
            resource.assignment?.let { openAssignmentActions(it) }
            resource.announcement?.let { openAnnouncementActions(it) }
            return
        }

        if (currentFeature == "history") {
            if (selectedModuleId == null) {
                val module = row.id?.let { moduleRows[it] } ?: return
                openModuleHistory(module)
            } else {
                val event = row.id?.let { historyRows[it] } ?: return
                AlertDialog.Builder(this)
                    .setTitle(event.title)
                    .setMessage(AcademicHistoryTimelineUi.details(event))
                    .setPositiveButton("Fermer", null)
                    .show()
            }
            return
        }

        if (currentFeature == "courses") {
            val course = row.id?.let { courseRows[it] } ?: return
            openCourseActions(course)
            return
        }

        if (currentFeature == "announcements") {
            val announcement = row.id?.let { announcementRows[it] } ?: return
            openAnnouncementActions(announcement)
            return
        }

        if (currentFeature == "notifications") {
            val notification = row.id?.let { notificationRows[it] } ?: return
            notificationReadStore.markRead(notification)
            val feature = AcademicNotificationStatus.actionFeature(notification)
            if (feature != "notifications") {
                navigateToFeature(feature)
            } else {
                Toast.makeText(this, notification.message, Toast.LENGTH_LONG).show()
                renderNotifications(notificationRows.values.toList())
            }
            return
        }

        if (currentFeature != "assignments") {
            return
        }
        if (selectedModuleId == null) {
            val group = row.id?.let { assignmentModuleRows[it] } ?: return
            selectedModuleId = group.moduleId
            renderAssignmentModuleSelector()
            updateAssignmentFilterSummary()
            loadFeature(currentFeature)
            return
        }
        val assignmentId = row.id ?: return
        val assignment = assignmentRows[assignmentId] ?: return
        openAssignmentActions(assignment)
    }

    private fun openAssignmentActions(assignment: AssignmentItem) {
        val options = mutableListOf<String>()
        options += "Voir le devoir"
        if (assignment.attachmentPath != null) {
            options += "Telecharger l'enonce"
        }
        if (canSubmitAssignment(assignment)) {
            options += if (hasAssignmentSubmission(assignment)) {
                "Modifier ma soumission"
            } else {
                "Soumettre"
            }
        }
        if (hasAssignmentSubmission(assignment)) {
            options += "Voir ma soumission"
        }

        AlertDialog.Builder(this)
            .setTitle(assignment.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir le devoir" -> showAssignmentDetails(assignment)
                    "Telecharger l'enonce" -> openExternal(assignment.attachmentPath ?: return@setItems)
                    "Soumettre",
                    "Modifier ma soumission" -> openSubmissionDialog(assignment)
                    "Voir ma soumission" -> loadAndShowSubmission(assignment)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun openAnnouncementActions(announcement: AnnouncementItem) {
        val options = mutableListOf("Voir l'annonce")
        if (!announcement.attachmentPath.isNullOrBlank()) {
            options += "Ouvrir la piece jointe"
        }

        AlertDialog.Builder(this)
            .setTitle(announcement.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir l'annonce" -> showAnnouncementDetails(announcement)
                    "Ouvrir la piece jointe" -> openExternal(
                        announcement.attachmentPath ?: return@setItems,
                        announcement.attachmentName
                    )
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showAnnouncementDetails(announcement: AnnouncementItem) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 8)
        }
        addDetailRow(root, "Date", announcement.createdAt?.let { formatDateTime(it) } ?: "-", true)
        addDetailRow(root, "Auteur", announcement.authorName ?: "Administration")
        addDetailRow(root, "Audience", announcementAudience(announcement))
        addDetailRow(
            root,
            "Piece jointe",
            announcement.attachmentName.takeIf { !announcement.attachmentPath.isNullOrBlank() && it.isNotBlank() } ?: "-"
        )
        addDetailRow(root, "Message", announcement.message, multiline = true)

        AlertDialog.Builder(this)
            .setTitle(announcement.title)
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun announcementAudience(announcement: AnnouncementItem): String {
        return listOfNotNull(
            announcement.targetClasseNom?.takeIf { it.isNotBlank() }?.let { "Classe: $it" },
            announcement.targetFiliereNom?.takeIf { it.isNotBlank() }?.let { "Filiere: $it" }
        ).joinToString(" | ").ifBlank { "Tous les etudiants concernes" }
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

    private fun openModuleOverview(module: StudentModuleItem) {
        startActivity(Intent(this, StudentFeatureListActivity::class.java).apply {
            putExtra(EXTRA_FEATURE, "module_overview")
            putExtra(EXTRA_MODULE_ID, module.id)
            putExtra(EXTRA_MODULE_NAME, module.nom)
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openModuleHistory(module: StudentModuleItem) {
        openModuleFeature(module, "history")
    }

    private fun openModuleFeature(module: StudentModuleItem, feature: String) {
        startActivity(Intent(this, StudentFeatureListActivity::class.java).apply {
            putExtra(EXTRA_FEATURE, feature)
            putExtra(EXTRA_MODULE_ID, module.id)
        })
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openModuleStatistics(module: StudentModuleItem? = null) {
        val intent = Intent(this, AcademicStatisticsActivity::class.java)
            .putExtra(AcademicStatisticsActivity.EXTRA_ROLE, AcademicStatisticsActivity.ROLE_STUDENT)
        (module?.id ?: selectedModuleId)?.let { intent.putExtra(AcademicStatisticsActivity.EXTRA_MODULE_ID, it) }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSubmissionDialog(assignment: AssignmentItem) {
        val status = assignmentStatusUi(assignment)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 6)
        }

        val statusText = TextView(this).apply {
            text = "Status: ${status.description}"
            setTextColor(getColor(R.color.textPrimary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val deadline = TextView(this).apply {
            text = deadlineLine(assignment)
            setTextColor(getColor(R.color.statRed))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 6, 0, 0)
        }

        val moduleText = TextView(this).apply {
            text = "${assignment.moduleNom ?: "Module"} | ${assignment.teacherName ?: "Enseignant"}"
            setTextColor(getColor(R.color.textSecondary))
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }

        val helper = TextView(this).apply {
            text = "Ajoutez un texte, un ou plusieurs fichiers, ou les deux. Verifiez la liste avant l'envoi."
            setTextColor(getColor(R.color.textSecondary))
            textSize = 13f
            setPadding(0, 8, 0, 14)
        }

        val input = EditText(this).apply {
            hint = "Message ou commentaire optionnel"
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(24, 18, 24, 18)
        }

        val addFilesButton = MaterialButton(this).apply {
            text = "Ajouter un ou plusieurs fichiers"
            textSize = 13f
            minHeight = 44
            minimumHeight = 44
            setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        }

        val summaryText = TextView(this).apply {
            setTextColor(getColor(R.color.textPrimary))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 0)
        }

        val fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        root.addView(statusText)
        root.addView(deadline)
        root.addView(moduleText)
        root.addView(helper)
        root.addView(input)
        root.addView(addFilesButton)
        root.addView(summaryText)
        root.addView(fileList)

        val scrollView = ScrollView(this).apply { addView(root) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Soumettre le devoir")
            .setView(scrollView)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer", null)
            .create()

        val draft = SubmissionDraftUi(
            assignment = assignment,
            textInput = input,
            selectedUris = mutableListOf(),
            summaryText = summaryText,
            fileList = fileList
        )
        pendingSubmissionDraft = draft
        renderSubmissionDraftFiles(draft)

        dialog.setOnDismissListener {
            if (pendingSubmissionDraft === draft) {
                pendingSubmissionDraft = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text?.toString().orEmpty().trim()
                if (text.isBlank() && draft.selectedUris.isEmpty()) {
                    Toast.makeText(this, "Ajoutez un texte ou au moins un fichier.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val files = draft.selectedUris.toList()
                dialog.dismiss()
                if (files.isEmpty()) {
                    submitAssignmentText(assignment.id, text)
                } else {
                    submitAssignmentDraft(assignment.id, text.takeIf { it.isNotBlank() }, files)
                }
            }
        }
        dialog.show()
    }

    private fun submitAssignmentText(assignmentId: Long, text: String) {
        val uploadProgress = showUploadProgressDialog(emptyList(), 0L)
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            uploadProgress.updateIndeterminate(
                status = "Envoi en cours",
                detail = "Soumission texte sans fichier"
            )
            when (val result = repository.submitAssignment(assignmentId, submissionText = text, fileParts = null)) {
                is ApiResult.Success -> {
                    val late = result.data.lateSubmission
                    uploadProgress.update(
                        percent = 100,
                        status = "Soumission enregistree",
                        detail = "Votre travail a ete transmis."
                    )
                    delay(350)
                    uploadProgress.dismiss()
                    if (late) {
                        showSuccess("Soumission envoyee en retard.")
                    } else {
                        showSuccess("Soumission enregistree.")
                    }
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> {
                    uploadProgress.dismiss()
                    showError(result.message)
                }
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun renderSubmissionDraftFiles(draft: SubmissionDraftUi) {
        draft.fileList.removeAllViews()
        draft.summaryText.text = buildSelectedFilesSummary(draft.selectedUris)

        if (draft.selectedUris.isEmpty()) {
            draft.fileList.addView(TextView(this).apply {
                text = "Aucun fichier selectionne. Vous pouvez envoyer seulement un texte si besoin."
                setTextColor(getColor(R.color.textSecondary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            })
            return
        }

        draft.selectedUris.forEachIndexed { index, uri ->
            draft.fileList.addView(createSelectedFileRow(index + 1, uri) {
                draft.selectedUris.removeAt(index)
                renderSubmissionDraftFiles(draft)
            })
        }
    }

    private fun createSelectedFileRow(displayIndex: Int, uri: Uri, onRemove: () -> Unit): View {
        val mimeType = FileUploadUtils.resolveMimeType(this, uri)
        val fileName = FileUploadUtils.resolveFileName(this, uri)
        val size = FileUploadUtils.resolveFileSize(this, uri)
            ?.let { FileUploadUtils.readableSize(it) }
            ?: "taille inconnue"
        val type = FileUploadUtils.readableDocumentType(mimeType, fileName)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL

            addView(TextView(this@StudentFeatureListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "Fichier $displayIndex - ${FileUploadUtils.iconForMimeType(mimeType)}  $fileName\n$type - $size"
                setTextColor(getColor(R.color.textPrimary))
                textSize = 14f
                setLineSpacing(2f, 1f)
            })

            addView(MaterialButton(this@StudentFeatureListActivity).apply {
                text = "Retirer"
                textSize = 12f
                minHeight = 40
                minimumHeight = 40
                setTextColor(getColor(R.color.statRed))
                setOnClickListener { onRemove() }
            })
        }
    }

    private fun buildSelectedFilesSummary(uris: List<Uri>): String {
        if (uris.isEmpty()) {
            return "Aucun fichier selectionne"
        }
        val knownSizes = uris.mapNotNull { uri -> FileUploadUtils.resolveFileSize(this, uri) }
        val knownTotal = knownSizes.sum()
        val unknownCount = uris.size - knownSizes.size
        val sizeLabel = when {
            knownTotal > 0L && unknownCount > 0 -> "${FileUploadUtils.readableSize(knownTotal)} + taille inconnue"
            knownTotal > 0L -> FileUploadUtils.readableSize(knownTotal)
            else -> "taille inconnue"
        }
        return "${uris.size} fichier(s) pret(s) a envoyer - $sizeLabel"
    }

    private fun showUploadProgressDialog(uris: List<Uri>, totalKnownBytes: Long): UploadProgressUi {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 8)
        }

        val statusText = TextView(this).apply {
            text = if (uris.isEmpty()) "Envoi de la soumission" else "Preparation des fichiers"
            setTextColor(getColor(com.pfe.gestionetudiantmobile.R.color.textPrimary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val detailText = TextView(this).apply {
            text = if (uris.isEmpty()) "Soumission texte sans fichier" else buildSelectedFilesSummary(uris)
            setTextColor(getColor(com.pfe.gestionetudiantmobile.R.color.textSecondary))
            textSize = 13f
            setPadding(0, 6, 0, 14)
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = totalKnownBytes <= 0L
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(statusText)
        root.addView(detailText)
        root.addView(progressBar)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Envoi de la soumission")
            .setView(root)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        return UploadProgressUi(dialog, progressBar, statusText, detailText)
    }

    private fun submitAssignmentDraft(assignmentId: Long, submissionText: String?, uris: List<Uri>) {
        val knownSizes = uris.map { uri -> FileUploadUtils.resolveFileSize(this, uri) ?: -1L }
        val totalKnownBytes = knownSizes.filter { it > 0L }.sum()
        val fileNames = uris.map { uri -> FileUploadUtils.resolveFileName(this, uri) }
        val uploadProgress = showUploadProgressDialog(uris, totalKnownBytes)
        val uploadedByFile = LongArray(uris.size)
        val uploadedTotal = AtomicLong(0L)

        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            uploadProgress.updateIndeterminate(
                status = "Preparation des fichiers",
                detail = buildSelectedFilesSummary(uris)
            )

            val fileParts = runCatching {
                withContext(Dispatchers.IO) {
                    uris.mapIndexed { index, uri ->
                        FileUploadUtils.uriToMultipartPart(
                            context = this@StudentFeatureListActivity,
                            uri = uri,
                            partName = "files"
                        ) { bytesWritten, contentLength ->
                            if (contentLength <= 0L || totalKnownBytes <= 0L) {
                                runOnUiThread {
                                    uploadProgress.updateIndeterminate(
                                        status = "Envoi en cours",
                                        detail = "Fichier ${index + 1}/${uris.size}: ${fileNames[index]}"
                                    )
                                }
                                return@uriToMultipartPart
                            }

                            val previous = uploadedByFile[index]
                            if (bytesWritten > previous) {
                                uploadedByFile[index] = bytesWritten
                                uploadedTotal.addAndGet(bytesWritten - previous)
                            }

                            val percent = ((uploadedTotal.get().toDouble() / totalKnownBytes.toDouble()) * 100)
                                .roundToInt()
                                .coerceIn(1, 99)

                            runOnUiThread {
                                uploadProgress.update(
                                    percent = percent,
                                    status = "Envoi en cours",
                                    detail = "${FileUploadUtils.readableSize(uploadedTotal.get())} / ${FileUploadUtils.readableSize(totalKnownBytes)} - fichier ${index + 1}/${uris.size}"
                                )
                            }
                        }
                    }
                }
            }.getOrElse {
                binding.swipeLayout.isRefreshing = false
                uploadProgress.dismiss()
                Toast.makeText(
                    this@StudentFeatureListActivity,
                    it.message ?: "Erreur lors de la lecture d'un fichier.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            if (totalKnownBytes > 0L) {
                uploadProgress.update(
                    percent = 2,
                    status = "Connexion au serveur",
                    detail = "Envoi de ${uris.size} fichier(s)"
                )
            } else {
                uploadProgress.updateIndeterminate(
                    status = "Connexion au serveur",
                    detail = "Envoi de ${uris.size} fichier(s)"
                )
            }

            when (val result = repository.submitAssignment(assignmentId, submissionText = submissionText, fileParts = fileParts)) {
                is ApiResult.Success -> {
                    val late = result.data.lateSubmission
                    uploadProgress.update(
                        percent = 100,
                        status = "Soumission enregistree",
                        detail = "Votre travail a ete transmis."
                    )
                    delay(450)
                    uploadProgress.dismiss()
                    if (late) {
                        showSuccess("Soumission envoyee en retard.")
                    } else {
                        showSuccess("Soumission envoyee avec succes.")
                    }
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> {
                    uploadProgress.dismiss()
                    showError(result.message)
                }
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun showError(message: String) {
        stateController.showErrorMessage(message)
    }

    private fun showSuccess(message: String) {
        stateController.showSuccess(message)
    }

    private fun markVisibleNotificationsRead() {
        val items = notificationRows.values.toList()
        if (items.isEmpty()) {
            Toast.makeText(this, "Aucune notification a marquer.", Toast.LENGTH_LONG).show()
            return
        }
        notificationReadStore.markAllRead(items)
        renderNotifications(items)
    }

    private fun updateNotificationSummary(items: List<NotificationItem>) {
        if (currentFeature != "notifications") {
            return
        }
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = NotificationCenterUi.summary(items, notificationReadStore)
    }

    private fun isModuleFilterFeature(): Boolean {
        return currentFeature == "notes" || currentFeature == "absences" || currentFeature == "history"
    }

    private fun needsStudentModuleOptions(): Boolean {
        return currentFeature in setOf("notes", "absences", "courses", "assignments", "history", "module_overview")
    }

    private fun configureHeaderControls() {
        binding.moduleSelectorScroll.visibility = View.GONE
        binding.layoutModuleSelector.removeAllViews()

        if (currentFeature == "notifications") {
            binding.btnFilter.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.text = "Tout lu"
            binding.btnAction.setOnClickListener { markVisibleNotificationsRead() }
            binding.tvFilterSummary.visibility = View.VISIBLE
            binding.tvFilterSummary.text = "Evenements academiques recents"
            return
        }

        if (currentFeature == "profile") {
            binding.btnFilter.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.text = "Deconnexion"
            binding.btnAction.setOnClickListener { confirmLogout() }
            binding.tvFilterSummary.visibility = View.VISIBLE
            binding.tvFilterSummary.text = "Compte, role et coordonnees"
            return
        }

        if (currentFeature == "modules") {
            binding.btnFilter.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.text = "Stats"
            binding.btnAction.setOnClickListener { openModuleStatistics() }
            binding.tvFilterSummary.visibility = View.GONE
            return
        }

        if (currentFeature == "module_overview") {
            binding.btnFilter.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.text = "Stats"
            binding.btnAction.setOnClickListener { openModuleStatistics(selectedStudentModule()) }
            binding.tvFilterSummary.visibility = View.VISIBLE
            binding.tvFilterSummary.text = "Cours, devoirs et annonces charges automatiquement"
            return
        }

        if (currentFeature == "courses") {
            binding.btnAction.visibility = View.GONE
            binding.btnFilter.visibility = View.VISIBLE
            binding.btnFilter.text = "Module"
            binding.btnFilter.setOnClickListener { openModuleFilterDialog() }
            renderCourseModuleSelector()
            updateCourseFilterSummary()
            return
        }

        if (currentFeature == "assignments") {
            binding.btnAction.visibility = View.GONE
            binding.btnFilter.visibility = View.VISIBLE
            binding.btnFilter.text = "Statut"
            binding.btnFilter.setOnClickListener { openAssignmentFilterDialog() }
            renderAssignmentModuleSelector()
            updateAssignmentFilterSummary()
            return
        }

        if (isModuleFilterFeature()) {
            binding.btnAction.visibility = View.GONE
            binding.btnFilter.visibility = View.VISIBLE
            binding.btnFilter.text = "Module"
            binding.btnFilter.setOnClickListener { openModuleFilterDialog() }
            updateModuleFilterSummary()
            return
        }

        binding.btnFilter.visibility = View.GONE
        binding.btnAction.visibility = View.GONE
        binding.tvFilterSummary.visibility = View.GONE
    }

    private fun configureNotificationHint() {
        val supportsAcademicNotification = currentFeature in setOf(
            "notes",
            "absences",
            "courses",
            "module_overview",
            "announcements",
            "assignments",
            "notifications"
        )
        binding.tvNotificationHint.visibility = if (supportsAcademicNotification) View.VISIBLE else View.GONE
        binding.tvNotificationHint.text = AcademicNotificationCopy.studentHint
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.STUDENT,
            currentFeature = if (currentFeature == "module_overview") "modules" else currentFeature.ifBlank { "dashboard" },
            onDashboard = { goDashboard() },
            onFeature = { navigateToFeature(it) },
            onProfile = { navigateToFeature("profile") }
        )
    }

    private fun navigateToFeature(feature: String) {
        if (currentFeature == feature) {
            binding.recyclerView.smoothScrollToPosition(0)
            loadFeature(currentFeature)
            return
        }

        startActivity(
            Intent(this, StudentFeatureListActivity::class.java)
                .putExtra(EXTRA_FEATURE, feature)
        )
        finishWithTransition()
    }

    private fun goDashboard() {
        startActivity(
            Intent(this, StudentHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finishWithTransition()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Se deconnecter")
            .setMessage("Vous devrez vous reconnecter pour acceder a votre espace mobile.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Deconnexion") { _, _ -> logoutAndReturnToLogin() }
            .show()
    }

    private fun logoutAndReturnToLogin() {
        lifecycleScope.launch {
            authRepository.logout()
            sessionStore.clear()
            startActivity(
                Intent(this@StudentFeatureListActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finishWithTransition()
        }
    }

    private fun openModuleFilterDialog() {
        if (moduleOptions.isEmpty()) {
            Toast.makeText(this, "Aucun module disponible.", Toast.LENGTH_LONG).show()
            return
        }

        val labels = mutableListOf("Tous les modules") + moduleOptions.map { "${it.nom} (${it.code})" }
        val selectedIndex = moduleOptions.indexOfFirst { it.id == selectedModuleId }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0

        AlertDialog.Builder(this)
            .setTitle("Filtrer par module")
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, which ->
                selectedModuleId = if (which == 0) null else moduleOptions[which - 1].id
                if (currentFeature == "courses") {
                    renderCourseModuleSelector()
                    updateCourseFilterSummary()
                } else {
                    updateModuleFilterSummary()
                }
                loadFeature(currentFeature)
                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun renderCourseModuleSelector() {
        if (currentFeature != "courses") {
            binding.moduleSelectorScroll.visibility = View.GONE
            binding.layoutModuleSelector.removeAllViews()
            return
        }

        binding.moduleSelectorScroll.visibility = View.VISIBLE
        binding.layoutModuleSelector.removeAllViews()

        val options = mutableListOf<Pair<Long?, String>>(Pair(null, "Tous"))
        options += moduleOptions.map { module -> module.id to moduleSelectorLabel(module) }

        options.forEach { (moduleId, label) ->
            binding.layoutModuleSelector.addView(createModuleSelectorChip(moduleId, label))
        }
    }

    private fun renderAssignmentModuleSelector() {
        if (currentFeature != "assignments") {
            binding.moduleSelectorScroll.visibility = View.GONE
            binding.layoutModuleSelector.removeAllViews()
            return
        }

        binding.moduleSelectorScroll.visibility = View.VISIBLE
        binding.layoutModuleSelector.removeAllViews()

        val options = mutableListOf<Pair<Long?, String>>(Pair(null, "Modules"))
        options += moduleOptions.map { module -> module.id to moduleSelectorLabel(module) }

        options.forEach { (moduleId, label) ->
            binding.layoutModuleSelector.addView(createModuleSelectorChip(moduleId, label))
        }
    }

    private fun createModuleSelectorChip(moduleId: Long?, label: String): MaterialButton {
        val selected = selectedModuleId == moduleId
        return MaterialButton(this).apply {
            text = label
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(18)
            strokeWidth = dp(1)
            maxWidth = dp(220)
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(getColor(if (selected) R.color.onPrimary else R.color.textPrimary))
            backgroundTintList = ColorStateList.valueOf(
                getColor(if (selected) R.color.studentPrimary else R.color.surface)
            )
            strokeColor = ColorStateList.valueOf(
                getColor(if (selected) R.color.studentPrimary else R.color.outlineStrong)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                if (selectedModuleId != moduleId) {
                    selectedModuleId = moduleId
                    when (currentFeature) {
                        "courses" -> {
                            renderCourseModuleSelector()
                            updateCourseFilterSummary()
                        }
                        "assignments" -> {
                            renderAssignmentModuleSelector()
                            updateAssignmentFilterSummary()
                        }
                    }
                    loadFeature(currentFeature)
                }
            }
        }
    }

    private fun updateCourseFilterSummary(courseCount: Int? = null) {
        if (currentFeature != "courses") {
            return
        }
        val moduleLabel = selectedCourseModuleLabel()
        val countLabel = courseCount?.let { "$it cours" } ?: "Cours"
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = "$countLabel | Module: $moduleLabel"
    }

    private fun selectedCourseModuleLabel(): String {
        val moduleId = selectedModuleId ?: return "Tous les modules"
        return moduleOptions.firstOrNull { it.id == moduleId }?.let { moduleSelectorLabel(it) }
            ?: "Module selectionne"
    }

    private fun moduleSelectorLabel(module: StudentModuleItem): String {
        return module.code
            .takeIf { it.isNotBlank() }
            ?.let { "${module.nom} ($it)" }
            ?: module.nom
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun updateModuleFilterSummary() {
        if (!isModuleFilterFeature()) {
            binding.btnFilter.visibility = View.GONE
            binding.tvFilterSummary.visibility = View.GONE
            return
        }
        val selectedLabel = moduleOptions.firstOrNull { it.id == selectedModuleId }?.nom
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = if (selectedLabel.isNullOrBlank()) {
            "Module: Tous"
        } else {
            "Module: $selectedLabel"
        }
    }

    private fun selectedStudentModule(): StudentModuleItem? {
        val moduleId = selectedModuleId ?: return null
        return moduleOptions.firstOrNull { it.id == moduleId } ?: moduleRows[moduleId]
    }

    private fun openAssignmentFilterDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 24)
        }

        val statusLabels = listOf("Tous", "A venir", "En retard", "Soumis", "Non soumis")
        val statusValues = listOf("all", "upcoming", "overdue", "submitted", "not_submitted")
        val statusSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@StudentFeatureListActivity,
                android.R.layout.simple_spinner_dropdown_item,
                statusLabels
            )
            setSelection(statusValues.indexOf(assignmentFilter).takeIf { it >= 0 } ?: 0)
        }

        addStudentFilterLabel(root, "Statut")
        root.addView(statusSpinner)

        AlertDialog.Builder(this)
            .setTitle("Filtrer par statut")
            .setView(root)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Tous") { _, _ ->
                assignmentFilter = "all"
                updateAssignmentFilterSummary()
                loadFeature(currentFeature)
            }
            .setPositiveButton("Appliquer") { _, _ ->
                assignmentFilter = statusValues[statusSpinner.selectedItemPosition]
                updateAssignmentFilterSummary()
                loadFeature(currentFeature)
            }
            .show()
    }

    private fun updateAssignmentFilterSummary(assignmentCount: Int? = null) {
        val statusLabel = when (assignmentFilter) {
            "upcoming" -> "A venir"
            "overdue" -> "En retard"
            "submitted" -> "Soumis"
            "not_submitted" -> "Non soumis"
            else -> "Tous"
        }
        val moduleLabel = selectedModuleId
            ?.let { id -> moduleOptions.firstOrNull { it.id == id }?.let { moduleSelectorLabel(it) } }
            ?: "Choisir un module"
        val countLabel = assignmentCount?.let { "$it devoir(s)" }
        binding.tvFilterSummary.visibility = View.VISIBLE
        binding.tvFilterSummary.text = listOfNotNull(
            countLabel,
            "Module: $moduleLabel",
            "Statut: $statusLabel"
        ).joinToString(" | ")
    }

    private fun addStudentFilterLabel(root: LinearLayout, label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(getColor(R.color.textSecondary))
            setPadding(0, if (root.childCount == 0) 0 else 14, 0, 4)
        })
    }

    private fun showAssignmentDetails(assignment: AssignmentItem) {
        val status = assignmentStatusUi(assignment)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 8)
        }

        addDetailRow(root, "Status", status.description, true)
        addDetailRow(root, "Deadline", "${formatDateTime(assignment.dueDate)} (${deadlineRelativeLabel(assignment.dueDate)})", true)
        addDetailRow(root, "Module", assignment.moduleNom ?: "-")
        addDetailRow(root, "Teacher", assignment.teacherName ?: "-")
        addDetailRow(root, "Target", assignment.targetClasseNom ?: assignment.targetFiliereNom ?: "-")
        addDetailRow(root, "Attachment", assignment.attachmentName.takeIf { it.isNotBlank() } ?: "-")
        addDetailRow(root, "Submitted", assignment.submittedAt?.let { formatDateTime(it) } ?: "-")
        addDetailRow(root, "Score", assignment.score?.let { "$it /20" } ?: "-")
        addDetailRow(root, "Feedback", assignment.feedback ?: "-")
        addDetailRow(root, "Instructions", assignment.description, false, multiline = true)

        AlertDialog.Builder(this)
            .setTitle(assignment.title)
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun addDetailRow(
        root: LinearLayout,
        label: String,
        value: String,
        emphasized: Boolean = false,
        multiline: Boolean = false
    ) {
        root.addView(TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.textSecondary))
            textSize = 12f
            setPadding(0, 10, 0, 2)
        })
        root.addView(TextView(this).apply {
            text = value.ifBlank { "-" }
            setTextColor(getColor(R.color.textPrimary))
            textSize = if (emphasized) 15f else 14f
            if (emphasized) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            if (multiline) {
                setLineSpacing(3f, 1f)
            }
        })
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
                        val infoRoot = LinearLayout(this@StudentFeatureListActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(36, 20, 36, 8)
                        }
                        addDetailRow(infoRoot, "Status", readableSubmissionStatus(submission.status, submission.lateSubmission), true)
                        addDetailRow(infoRoot, "Submitted", submission.submittedAt?.let { formatDateTime(it) } ?: "-")
                        addDetailRow(infoRoot, "Late", if (submission.lateSubmission) "Yes" else "No")
                        addDetailRow(infoRoot, "Score", submission.score?.let { "$it /20" } ?: "-")
                        addDetailRow(infoRoot, "Feedback", submission.feedback ?: "-")
                        addDetailRow(infoRoot, "Text", submission.submissionText ?: "-", multiline = true)

                        AlertDialog.Builder(this@StudentFeatureListActivity)
                            .setTitle("Details de ma soumission")
                            .setView(ScrollView(this@StudentFeatureListActivity).apply { addView(infoRoot) })
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
            val size = file.fileSize?.let { FileUploadUtils.readableSize(it) } ?: "-"
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
                    showSuccess("Fichier supprime de la soumission.")
                    loadFeature(currentFeature)
                }
                is ApiResult.Error -> showError(result.message)
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openExternal(url: String, suggestedFileName: String? = null) {
        Toast.makeText(this, "Telechargement du document...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = AuthenticatedFileOpener.downloadAndOpen(
                this@StudentFeatureListActivity,
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
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_MODULE_NAME = "extra_module_name"
    }
}

private class UploadProgressUi(
    private val dialog: AlertDialog,
    private val progressBar: ProgressBar,
    private val statusText: TextView,
    private val detailText: TextView
) {
    fun update(percent: Int, status: String, detail: String) {
        progressBar.isIndeterminate = false
        progressBar.progress = percent.coerceIn(0, 100)
        statusText.text = status
        detailText.text = detail
    }

    fun updateIndeterminate(status: String, detail: String) {
        progressBar.isIndeterminate = true
        statusText.text = status
        detailText.text = detail
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}

private data class AssignmentStatusUi(
    val label: String,
    val description: String
)

private data class SubmissionDraftUi(
    val assignment: AssignmentItem,
    val textInput: EditText,
    val selectedUris: MutableList<Uri>,
    val summaryText: TextView,
    val fileList: LinearLayout
)

private data class AssignmentModuleGroup(
    val moduleId: Long,
    val moduleName: String,
    val moduleCode: String?,
    val assignments: List<AssignmentItem>
) {
    val label: String
        get() = moduleCode
            ?.takeIf { it.isNotBlank() }
            ?.let { "$moduleName ($it)" }
            ?: moduleName
}

private data class StudentModuleResourceRow(
    val course: CourseItem? = null,
    val assignment: AssignmentItem? = null,
    val announcement: AnnouncementItem? = null
)
