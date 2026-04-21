package com.pfe.gestionetudiantmobile.ui.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityFeatureListBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.AcademicHistoryTimelineUi
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationCopy
import com.pfe.gestionetudiantmobile.ui.common.AcademicNotificationStatus
import com.pfe.gestionetudiantmobile.ui.common.AcademicStatisticsActivity
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUi
import com.pfe.gestionetudiantmobile.ui.common.CourseDocumentUiItem
import com.pfe.gestionetudiantmobile.ui.common.FeatureStateController
import com.pfe.gestionetudiantmobile.ui.common.NotificationCenterUi
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.common.ProfileUi
import com.pfe.gestionetudiantmobile.ui.common.UiRow
import com.pfe.gestionetudiantmobile.ui.common.UiRowAdapter
import com.pfe.gestionetudiantmobile.ui.home.TeacherHomeActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.AuthenticatedFileOpener
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import com.pfe.gestionetudiantmobile.util.NotificationReadStore
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class TeacherFeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding
    private val repository by lazy { TeacherRepository(this) }
    private val authRepository = AuthRepository()
    private val adapter = UiRowAdapter { row -> onRowClicked(row) }
    private lateinit var stateController: FeatureStateController
    private lateinit var sessionStore: SessionStore
    private lateinit var notificationReadStore: NotificationReadStore
    private var currentFeature: String = ""

    private var moduleRows: Map<Long, TeacherModuleItem> = emptyMap()
    private var noteRows: Map<Long, NoteItem> = emptyMap()
    private var absenceRows: Map<Long, AbsenceItem> = emptyMap()
    private var studentRows: Map<Long, StudentProfile> = emptyMap()
    private var assignmentRows: Map<Long, AssignmentItem> = emptyMap()
    private var courseRows: Map<Long, CourseItem> = emptyMap()
    private var announcementRows: Map<Long, AnnouncementItem> = emptyMap()
    private var notificationRows: Map<Long, NotificationItem> = emptyMap()
    private var timetableRows: Map<Long, TimetableItem> = emptyMap()
    private var historyRows: Map<Long, AcademicHistoryEvent> = emptyMap()

    private var modulesCache: List<TeacherModuleItem> = emptyList()
    private var classesCache: List<ClasseItem> = emptyList()
    private var filiereOptions: List<FilterOption> = emptyList()

    private var selectedModuleId: Long? = null
    private var selectedClasseId: Long? = null
    private var selectedFiliereId: Long? = null
    private var selectedQuery: String? = null
    private var selectedDate: LocalDate? = null
    private var suggestedSessionDate: String? = null
    private var suggestedSessionHours: Int? = null

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
        sessionStore = SessionStore(this)
        notificationReadStore = NotificationReadStore(this, sessionStore.getUser()?.id)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        stateController = FeatureStateController(binding) { loadFeature(currentFeature) }

        currentFeature = intent.getStringExtra(EXTRA_FEATURE)?.trim()?.lowercase().orEmpty()
        restoreFeatureFilters()
        intent.takeIf { it.hasExtra(EXTRA_MODULE_ID) }?.getLongExtra(EXTRA_MODULE_ID, -1L)
            ?.takeIf { it > 0 }
            ?.let { selectedModuleId = it }
        intent.takeIf { it.hasExtra(EXTRA_CLASSE_ID) }?.getLongExtra(EXTRA_CLASSE_ID, -1L)
            ?.takeIf { it > 0 }
            ?.let { selectedClasseId = it }
        intent.takeIf { it.hasExtra(EXTRA_FILIERE_ID) }?.getLongExtra(EXTRA_FILIERE_ID, -1L)
            ?.takeIf { it > 0 }
            ?.let { selectedFiliereId = it }
        suggestedSessionDate = intent.getStringExtra(EXTRA_SESSION_DATE)?.takeIf { it.isNotBlank() }
        suggestedSessionHours = intent.getIntExtra(EXTRA_SESSION_HOURS, -1).takeIf { it > 0 }
        if (currentFeature == "absences" || currentFeature == "history") {
            suggestedSessionDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { selectedDate = it }
        }
        saveFeatureFilters()

        binding.tvTitle.text = when (currentFeature) {
            "modules" -> "Mes modules"
            "students" -> "Etudiants"
            "notes" -> "Notes"
            "absences" -> "Absences"
            "courses" -> "Cours"
            "announcements" -> "Annonces"
            "assignments" -> "Devoirs"
            "timetable" -> "Emploi hebdomadaire"
            "history" -> "Historique"
            "notifications" -> "Notifications"
            "profile" -> "Mon profil"
            else -> "Liste"
        }

        binding.btnBack.setOnClickListener { goDashboard() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goDashboard()
            }
        })
        binding.swipeLayout.setOnRefreshListener { loadFeature(currentFeature) }
        configureHeaderButtons()
        configureNotificationHint()
        configureBottomNavigation()

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
        return feature in setOf("notes", "absences", "students", "courses", "assignments", "history")
    }

    private fun configureHeaderButtons() {
        binding.btnFilter.visibility = if (supportsFiltering(currentFeature)) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnFilter.setOnClickListener { openFilterDialog() }

        when (currentFeature) {
            "modules" -> {
                binding.btnAction.text = "Stats"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openStatistics() }
            }
            "courses" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startWithTransition(Intent(this, TeacherCreateCourseActivity::class.java)) }
            }
            "assignments" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startWithTransition(createAssignmentIntent()) }
            }
            "announcements" -> {
                binding.btnAction.text = "Nouveau"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { startWithTransition(createAnnouncementIntent()) }
            }
            "profile" -> {
                binding.btnAction.text = "Deconnexion"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { confirmLogout() }
            }
            "notes" -> {
                binding.btnAction.text = "Session"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openModuleChooserForWorkspace("notes") }
            }
            "absences" -> {
                binding.btnAction.text = "Session"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openModuleChooserForWorkspace("absences") }
            }
            "notifications" -> {
                binding.btnAction.text = "Tout lu"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { markVisibleNotificationsRead() }
            }
            "history" -> {
                binding.btnAction.text = "Module"
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.btnAction.setOnClickListener { openModuleChooserForFeature("history") }
            }
            else -> binding.btnAction.visibility = android.view.View.GONE
        }
    }

    private fun configureNotificationHint() {
        val supportsAcademicNotification = currentFeature in setOf(
            "notes",
            "absences",
            "courses",
            "announcements",
            "assignments",
            "notifications"
        )
        binding.tvNotificationHint.visibility =
            if (supportsAcademicNotification) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvNotificationHint.text = AcademicNotificationCopy.teacherHint
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.TEACHER,
            currentFeature = currentFeature.ifBlank { "dashboard" },
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
            Intent(this, TeacherFeatureListActivity::class.java)
                .putExtra(EXTRA_FEATURE, feature)
        )
        finishWithTransition()
    }

    private fun goDashboard() {
        startActivity(
            Intent(this, TeacherHomeActivity::class.java)
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
                Intent(this@TeacherFeatureListActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finishWithTransition()
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
            val refreshing = binding.swipeLayout.isRefreshing
            binding.swipeLayout.isRefreshing = true
            if (!refreshing) {
                stateController.showLoading("Chargement de ${binding.tvTitle.text.toString().lowercase()}...")
            }

            when (feature) {
                "modules" -> loadModules()
                "students" -> loadStudents()
                "notes" -> loadNotes()
                "absences" -> loadAbsences()
                "courses" -> loadCourses()
                "announcements" -> loadAnnouncements()
                "assignments" -> loadAssignments()
                "timetable" -> loadTimetable()
                "history" -> loadHistory()
                "notifications" -> loadNotifications()
                "profile" -> loadProfile()
                else -> stateController.showEmpty(
                    title = "Section indisponible",
                    message = "Cette section mobile n'est pas encore disponible.",
                    icon = "?"
                )
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
                submitRows(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.nom} (${it.code})",
                        subtitle = "Touchez pour charger les etudiants | Semestre ${it.semestre} | Filiere: ${it.filiereNom ?: "-"}",
                        badge = "Etudiants"
                    )
                })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadStudents() {
        when (val result = repository.students(selectedModuleId, selectedClasseId, selectedFiliereId, selectedQuery)) {
            is ApiResult.Success -> {
                studentRows = result.data.associateBy { it.id }
                submitRows(result.data.map {
                    UiRow(
                        id = it.id,
                        title = it.fullName,
                        subtitle = "Matricule: ${it.matricule} | ${it.classe ?: "-"} | ${it.filiere ?: "-"}",
                        badge = it.email ?: ""
                    )
                })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadNotes() {
        if (selectedModuleId == null) {
            showModuleSelectionRows("Choisir un module pour saisir les notes")
            return
        }

        when (val result = repository.notes(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> {
                noteRows = result.data.associateBy { it.id }
                submitRows(result.data.map {
                    UiRow(
                        id = it.id,
                        title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                        subtitle = "CC: ${it.noteCc ?: "-"} | EX: ${it.noteExamen ?: "-"} | Final: ${it.noteFinal ?: "-"}",
                        badge = it.statut
                    )
                })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadAbsences() {
        if (selectedModuleId == null) {
            showModuleSelectionRows("Choisir un module pour faire l'appel")
            return
        }

        when (val result = repository.absences(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> {
                val filtered = result.data.filter { absence ->
                    selectedDate == null || absence.dateAbsence == selectedDate
                }
                absenceRows = filtered.associateBy { it.id }
                submitRows(filtered.map {
                    UiRow(
                        id = it.id,
                        title = "${it.studentName ?: "Etudiant"} - ${it.moduleNom ?: "Module"}",
                        subtitle = "${it.dateAbsence} | ${it.nombreHeures}h",
                        badge = if (it.justifiee) "Justifiee" else "Non justifiee"
                    )
                })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private fun showModuleSelectionRows(subtitlePrefix: String) {
        moduleRows = modulesCache.associateBy { it.id }
        submitRows(modulesCache.map {
            UiRow(
                id = it.id,
                title = "${it.nom} (${it.code})",
                subtitle = "$subtitlePrefix | Filiere: ${it.filiereNom ?: "-"} | Semestre: ${it.semestre}",
                badge = "Session"
            )
        })
    }

    private suspend fun loadCourses() {
        when (val result = repository.courses(selectedModuleId)) {
            is ApiResult.Success -> {
                courseRows = result.data.associateBy { it.id }
                submitRows(CourseDocumentUi.rowsByModule(result.data))
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadAnnouncements() {
        when (val result = repository.announcements()) {
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
    }

    private suspend fun loadAssignments() {
        when (val result = repository.assignments(selectedModuleId)) {
            is ApiResult.Success -> {
                assignmentRows = result.data.associateBy { it.id }
                submitRows(result.data.map {
                    UiRow(
                        id = it.id,
                        title = it.title,
                        subtitle = "Deadline: ${it.dueDate} | Fichier: ${it.attachmentName}",
                        badge = "Gerer"
                    )
                })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadTimetable() {
        when (val result = repository.timetable()) {
            is ApiResult.Success -> {
                val sessions = result.data
                    .filter { it.valide }
                    .sortedWith(compareBy<TimetableItem> { dayOrder(it.jour) }.thenBy { it.heureDebut })
                timetableRows = sessions.associateBy { it.id }
                submitRows(weeklyTimetableRows(sessions))
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadHistory() {
        if (selectedModuleId == null) {
            showModuleSelectionRows("Choisir un module pour afficher l'historique")
            return
        }

        val actorName = sessionStore.getUser()?.fullName
        when (val result = repository.history(selectedModuleId, selectedClasseId, selectedQuery, actorName)) {
            is ApiResult.Success -> {
                val filtered = result.data.filter { event ->
                    selectedDate == null || event.occurredAt?.toLocalDate() == selectedDate
                }
                historyRows = filtered.mapIndexed { index, event -> index.toLong() to event }.toMap()
                binding.tvFilterSummary.visibility = android.view.View.VISIBLE
                binding.tvFilterSummary.text = AcademicHistoryTimelineUi.summary(filtered)
                submitRows(AcademicHistoryTimelineUi.rows(filtered) { index -> index.toLong() })
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
    }

    private suspend fun loadNotifications() {
        val items = mutableListOf<NotificationItem>()
        var firstError: String? = null

        when (val result = repository.notes(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> items += result.data.takeRecentNotes()
            is ApiResult.Error -> firstError = result.message
        }
        when (val result = repository.absences(selectedModuleId, selectedClasseId, selectedQuery)) {
            is ApiResult.Success -> items += result.data
                .filter { selectedDate == null || it.dateAbsence == selectedDate }
                .takeRecentAbsences()
            is ApiResult.Error -> firstError = firstError ?: result.message
        }
        when (val result = repository.assignments(selectedModuleId)) {
            is ApiResult.Success -> items += result.data.takeRecentAssignments()
            is ApiResult.Error -> firstError = firstError ?: result.message
        }
        when (val result = repository.courses(selectedModuleId)) {
            is ApiResult.Success -> items += result.data.takeRecentCourses()
            is ApiResult.Error -> firstError = firstError ?: result.message
        }
        when (val result = repository.announcements()) {
            is ApiResult.Success -> items += result.data.takeRecentAnnouncements()
            is ApiResult.Error -> firstError = firstError ?: result.message
        }

        if (items.isEmpty() && firstError != null) {
            showLoadError(firstError)
            return
        }

        renderNotifications(items)
    }

    private fun renderNotifications(items: List<NotificationItem>) {
        val sorted = NotificationCenterUi.sortedItems(items).take(30)
        notificationRows = sorted.mapIndexed { index, item -> index.toLong() to item }.toMap()
        updateNotificationSummary(sorted)
        submitRows(NotificationCenterUi.rows(sorted, notificationReadStore) { index -> index.toLong() })
    }

    private fun weeklyTimetableRows(sessions: List<TimetableItem>): List<UiRow> {
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val rows = mutableListOf<UiRow>()
        val grouped = sessions.groupBy { dayOrder(it.jour) }
        for (day in 1..7) {
            val daySessions = grouped[day].orEmpty()
            if (daySessions.isEmpty()) continue

            rows += UiRow(
                title = dayLabel(day),
                subtitle = "${daySessions.size} seance(s)",
                badge = "Jour",
                icon = "EDT"
            )
            rows += daySessions.map { session ->
                UiRow(
                    id = session.id,
                    title = session.moduleNom ?: session.moduleCode ?: "Module",
                    subtitle = timetableSessionSubtitle(session),
                    badge = session.classeNom ?: session.filiereNom ?: "Session",
                    icon = "EDT"
                )
            }
        }
        return rows
    }

    private fun timetableSessionSubtitle(session: TimetableItem): String {
        val audience = listOfNotNull(
            session.classeNom?.takeIf { it.isNotBlank() },
            session.filiereNom?.takeIf { it.isNotBlank() }
        ).joinToString(" | ").ifBlank { "Classe non precisee" }
        return "${formatTime(session.heureDebut)} - ${formatTime(session.heureFin)} | $audience | Salle ${session.salle}"
    }

    private fun dayOrder(day: String): Int {
        val normalized = day.trim().lowercase()
        return when {
            normalized.startsWith("lun") || normalized.startsWith("mon") -> 1
            normalized.startsWith("mar") || normalized.startsWith("tue") -> 2
            normalized.startsWith("mer") || normalized.startsWith("wed") -> 3
            normalized.startsWith("jeu") || normalized.startsWith("thu") -> 4
            normalized.startsWith("ven") || normalized.startsWith("fri") -> 5
            normalized.startsWith("sam") || normalized.startsWith("sat") -> 6
            normalized.startsWith("dim") || normalized.startsWith("sun") -> 7
            else -> 8
        }
    }

    private fun dayLabel(day: Int): String {
        return when (day) {
            1 -> "Lundi"
            2 -> "Mardi"
            3 -> "Mercredi"
            4 -> "Jeudi"
            5 -> "Vendredi"
            6 -> "Samedi"
            7 -> "Dimanche"
            else -> "Autre"
        }
    }

    private fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun sessionDate(session: TimetableItem): LocalDate {
        val sessionDay = dayOrder(session.jour).takeIf { it in 1..7 } ?: return LocalDate.now()
        val today = LocalDate.now()
        val distance = (sessionDay - today.dayOfWeek.value + 7) % 7
        return today.plusDays(distance.toLong())
    }

    private fun sessionHours(session: TimetableItem): Int {
        val minutes = Duration.between(session.heureDebut, session.heureFin).toMinutes().coerceAtLeast(1L)
        return ((minutes + 59L) / 60L).toInt().coerceAtLeast(1)
    }

    private fun sessionLabel(session: TimetableItem): String {
        return "${session.jour} ${formatTime(session.heureDebut)}-${formatTime(session.heureFin)}"
    }

    private fun selectSmartSession(items: List<TimetableItem>): TimetableItem? {
        val validSessions = items.filter { it.valide && it.moduleId != null }
        if (validSessions.isEmpty()) return null

        val today = LocalDate.now().dayOfWeek.value
        val now = LocalTime.now()
        val todaySessions = validSessions
            .filter { dayOrder(it.jour) == today }
            .sortedBy { it.heureDebut }

        todaySessions.firstOrNull { isCurrentSession(it, now) }?.let { return it }
        todaySessions.firstOrNull { !it.heureDebut.isBefore(now) }?.let { return it }

        return validSessions
            .sortedWith(compareBy<TimetableItem> { sessionDistance(it, today, now) }
                .thenBy { it.heureDebut })
            .firstOrNull()
    }

    private fun isCurrentSession(session: TimetableItem, now: LocalTime): Boolean {
        return !now.isBefore(session.heureDebut) && now.isBefore(session.heureFin)
    }

    private fun sessionDistance(session: TimetableItem, today: Int, now: LocalTime): Int {
        val sessionDay = dayOrder(session.jour).takeIf { it in 1..7 } ?: return 8
        val distance = (sessionDay - today + 7) % 7
        return if (distance == 0 && !session.heureFin.isAfter(now)) 7 else distance
    }

    private fun List<NoteItem>.takeRecentNotes(): List<NotificationItem> {
        return sortedByDescending { it.id }
            .take(8)
            .map {
                val actionDate = it.updatedAt ?: it.createdAt
                NotificationItem(
                    eventId = "teacher-note:${it.id}:${actionDate ?: ""}",
                    type = "NOTE",
                    title = "Note enregistree - ${it.moduleNom ?: "Module"}",
                    message = "Etudiant: ${it.studentName ?: "-"} | Finale: ${it.noteFinal ?: "-"} /20 | ${it.statut}",
                    createdAt = actionDate,
                    actionPath = "/teacher/notes",
                    emailRelated = false
                )
            }
    }

    private fun List<AbsenceItem>.takeRecentAbsences(): List<NotificationItem> {
        return sortedWith(compareByDescending<AbsenceItem> { it.dateAbsence }.thenByDescending { it.id })
            .take(8)
            .map {
                NotificationItem(
                    eventId = "teacher-absence:${it.id}:${it.createdAt ?: it.dateAbsence}",
                    type = "ABSENCE",
                    title = "Absence enregistree - ${it.moduleNom ?: "Module"}",
                    message = "Etudiant: ${it.studentName ?: "-"} | Date: ${it.dateAbsence} | ${it.nombreHeures}h | ${if (it.justifiee) "Justifiee" else "Non justifiee"}",
                    createdAt = it.createdAt ?: it.dateAbsence.atStartOfDay(),
                    actionPath = "/teacher/absences",
                    emailRelated = false
                )
            }
    }

    private fun List<AssignmentItem>.takeRecentAssignments(): List<NotificationItem> {
        return sortedWith(compareByDescending<AssignmentItem> { it.createdAt ?: LocalDateTime.MIN }.thenByDescending { it.id })
            .take(8)
            .map {
                NotificationItem(
                    eventId = "teacher-assignment:${it.id}:${it.createdAt ?: it.dueDate}",
                    type = "ASSIGNMENT",
                    title = it.title,
                    message = "Devoir ${if (it.published) "publie" else "brouillon"} | Deadline: ${it.dueDate}",
                    createdAt = it.createdAt,
                    actionPath = "/teacher/assignments/${it.id}",
                    emailRelated = it.published
                )
            }
    }

    private fun List<CourseItem>.takeRecentCourses(): List<NotificationItem> {
        return sortedWith(compareByDescending<CourseItem> { it.createdAt ?: LocalDateTime.MIN }.thenByDescending { it.id })
            .take(8)
            .map {
                NotificationItem(
                    eventId = "teacher-course:${it.id}:${it.createdAt ?: ""}",
                    type = "COURSE",
                    title = it.title,
                    message = "Cours publie | Module: ${it.moduleNom ?: "-"} | Fichier: ${it.fileName}",
                    createdAt = it.createdAt,
                    actionPath = "/teacher/courses",
                    emailRelated = true
                )
            }
    }

    private fun List<AnnouncementItem>.takeRecentAnnouncements(): List<NotificationItem> {
        return sortedWith(compareByDescending<AnnouncementItem> { it.createdAt ?: LocalDateTime.MIN }.thenByDescending { it.id })
            .take(8)
            .map {
                NotificationItem(
                    eventId = "teacher-announcement:${it.id}:${it.createdAt ?: ""}",
                    type = "ANNOUNCEMENT",
                    title = it.title,
                    message = it.message,
                    createdAt = it.createdAt,
                    actionPath = "/teacher/announcements",
                    emailRelated = true
                )
            }
    }

    private suspend fun loadProfile() {
        when (val result = repository.profile()) {
            is ApiResult.Success -> {
                val profile = result.data
                submitRows(ProfileUi.teacherRows(profile, sessionStore.getUser()))
            }
            is ApiResult.Error -> showLoadError(result.message)
        }
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
            "students" -> "Aucun etudiant"
            "notes" -> if (selectedModuleId == null) "Choisissez un module" else "Aucune note"
            "absences" -> if (selectedModuleId == null) "Choisissez un module" else "Aucune absence"
            "courses" -> "Aucun cours"
            "announcements" -> "Aucune annonce"
            "assignments" -> "Aucun devoir"
            "timetable" -> "Aucune seance"
            "history" -> if (selectedModuleId == null) "Choisissez un module" else "Aucun historique"
            "notifications" -> "Aucune activite recente"
            "profile" -> "Profil indisponible"
            else -> "Aucune donnee"
        }
    }

    private fun emptyMessageForFeature(): String {
        return when (currentFeature) {
            "students" -> "Aucun etudiant ne correspond aux filtres selectionnes."
            "notes" -> if (selectedModuleId == null) {
                "Selectionnez un module pour ouvrir une session de notes."
            } else {
                "Aucune note ne correspond aux filtres selectionnes."
            }
            "absences" -> if (selectedModuleId == null) {
                "Selectionnez un module pour faire l'appel."
            } else {
                "Aucune absence ne correspond aux filtres selectionnes."
            }
            "courses" -> "Publiez un cours pour le voir apparaitre ici."
            "assignments" -> "Aucun devoir ne correspond au module selectionne."
            "timetable" -> "Les seances validees de votre emploi du temps apparaitront ici."
            "history" -> if (selectedModuleId == null) {
                "Selectionnez un module pour voir les notes et absences recentes."
            } else {
                "Aucune action de note ou d'absence ne correspond aux filtres selectionnes."
            }
            "notifications" -> "Les notes, absences, devoirs, cours et annonces recents apparaitront ici."
            else -> "Tirez vers le bas pour actualiser ou modifiez vos filtres."
        }
    }

    private fun emptyIconForFeature(): String {
        return when (currentFeature) {
            "students" -> "ETU"
            "notes" -> "20"
            "absences" -> "ABS"
            "courses" -> "CRS"
            "assignments" -> "DEV"
            "timetable" -> "EDT"
            "history" -> "HIS"
            "notifications" -> "NOT"
            "profile" -> "PRO"
            else -> "VID"
        }
    }

    private fun updateFilterSummary() {
        if (currentFeature == "profile") {
            binding.tvFilterSummary.visibility = android.view.View.VISIBLE
            binding.tvFilterSummary.text = "Compte, role et coordonnees"
            return
        }

        if (!supportsFiltering(currentFeature)) {
            binding.tvFilterSummary.visibility = android.view.View.GONE
            return
        }

        val moduleLabel = modulesCache.firstOrNull { it.id == selectedModuleId }?.nom
        val classeLabel = classesCache.firstOrNull { it.id == selectedClasseId }?.nom
        val filiereLabel = filiereOptions.firstOrNull { it.id == selectedFiliereId }?.label

        val parts = mutableListOf<String>()
        if (!moduleLabel.isNullOrBlank()) parts += "Module: $moduleLabel"
        if (allowsTeacherClassFilter(currentFeature) && !classeLabel.isNullOrBlank()) parts += "Classe: $classeLabel"
        if (allowsTeacherFiliereFilter(currentFeature) && !filiereLabel.isNullOrBlank()) parts += "Filiere: $filiereLabel"
        if (allowsTeacherDateFilter(currentFeature) && selectedDate != null) parts += "Date: $selectedDate"
        if (allowsTeacherSearch(currentFeature) && !selectedQuery.isNullOrBlank()) parts += "Recherche: ${selectedQuery!!.trim()}"

        binding.tvFilterSummary.visibility = android.view.View.VISIBLE
        binding.tvFilterSummary.text = if (parts.isEmpty()) "Aucun filtre actif" else parts.joinToString(" | ")
    }

    private fun openFilterDialog() {
        if (!supportsFiltering(currentFeature)) return

        lifecycleScope.launch {
            if (modulesCache.isEmpty() || classesCache.isEmpty()) loadFilterSources()
            val showModule = allowsTeacherModuleFilter(currentFeature)
            val showFiliere = allowsTeacherFiliereFilter(currentFeature)
            val showClass = allowsTeacherClassFilter(currentFeature)
            val showSearch = allowsTeacherSearch(currentFeature)
            val showDate = allowsTeacherDateFilter(currentFeature)

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
                isSingleLine = true
            }
            val dateInput = EditText(this@TeacherFeatureListActivity).apply {
                hint = "Date absence (yyyy-MM-dd)"
                setText(selectedDate?.toString().orEmpty())
                isSingleLine = true
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

            if (showModule) {
                addFilterLabel(root, "Module")
                root.addView(moduleSpinner)
            }
            if (showFiliere) {
                addFilterLabel(root, "Filiere")
                root.addView(filiereSpinner)
            }
            if (showClass) {
                addFilterLabel(root, "Classe")
                root.addView(classeSpinner)
            }
            if (showDate) {
                addFilterLabel(root, "Date")
                root.addView(dateInput)
            }
            if (showSearch) {
                addFilterLabel(root, "Recherche")
                root.addView(searchInput)
            }

            AlertDialog.Builder(this@TeacherFeatureListActivity)
                .setTitle("Filtrer")
                .setView(root)
                .setNegativeButton("Annuler", null)
                .setNeutralButton("Reinitialiser") { _, _ ->
                    selectedModuleId = null
                    selectedClasseId = null
                    selectedFiliereId = null
                    selectedQuery = null
                    selectedDate = null
                    saveFeatureFilters()
                    loadFeature(currentFeature)
                }
                .setPositiveButton("Appliquer") { _, _ ->
                    val selectedModule = moduleOptions[moduleSpinner.selectedItemPosition]
                    val selectedFiliere = filieres[filiereSpinner.selectedItemPosition]
                    val selectedClasse = classOptions[classeSpinner.selectedItemPosition]

                    selectedModuleId = if (showModule) selectedModule.id else null
                    selectedFiliereId = if (showFiliere) (selectedModule.filiereId ?: selectedFiliere.id) else null
                    selectedClasseId = if (showClass) selectedClasse.id else null
                    selectedQuery = if (showSearch) searchInput.text?.toString()?.trim()?.ifBlank { null } else null
                    selectedDate = if (showDate) {
                        parseOptionalDate(dateInput.text?.toString()?.trim().orEmpty())
                    } else {
                        null
                    }
                    saveFeatureFilters()
                    loadFeature(currentFeature)
                }
                .show()
        }
    }

    private fun allowsTeacherModuleFilter(feature: String): Boolean {
        return feature in setOf("students", "notes", "absences", "courses", "assignments", "history")
    }

    private fun allowsTeacherClassFilter(feature: String): Boolean {
        return feature in setOf("students", "notes", "absences", "history")
    }

    private fun allowsTeacherFiliereFilter(feature: String): Boolean {
        return feature == "students"
    }

    private fun allowsTeacherSearch(feature: String): Boolean {
        return feature in setOf("students", "notes", "absences", "history")
    }

    private fun allowsTeacherDateFilter(feature: String): Boolean {
        return feature == "absences" || feature == "history"
    }

    private fun parseOptionalDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return runCatching { LocalDate.parse(value) }
            .getOrElse {
                showError("Date ignoree. Format attendu: yyyy-MM-dd.")
                null
            }
    }

    private fun restoreFeatureFilters() {
        if (!supportsFiltering(currentFeature)) return
        val prefs = getSharedPreferences(FILTER_PREFS, MODE_PRIVATE)
        selectedModuleId = prefs.getLongOrNull(filterKey("moduleId"))
        selectedClasseId = prefs.getLongOrNull(filterKey("classeId"))
        selectedFiliereId = prefs.getLongOrNull(filterKey("filiereId"))
        selectedQuery = prefs.getString(filterKey("query"), null)?.takeIf { it.isNotBlank() }
        selectedDate = prefs.getString(filterKey("date"), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    private fun saveFeatureFilters() {
        if (!supportsFiltering(currentFeature)) return
        val editor = getSharedPreferences(FILTER_PREFS, MODE_PRIVATE).edit()
        editor.putLongOrRemove(filterKey("moduleId"), selectedModuleId)
        editor.putLongOrRemove(filterKey("classeId"), selectedClasseId)
        editor.putLongOrRemove(filterKey("filiereId"), selectedFiliereId)
        editor.putStringOrRemove(filterKey("query"), selectedQuery?.trim()?.takeIf { it.isNotBlank() })
        editor.putStringOrRemove(filterKey("date"), selectedDate?.toString())
        editor.apply()
    }

    private fun filterKey(name: String): String = "$currentFeature.$name"

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

    private fun addFilterLabel(root: LinearLayout, label: String) {
        root.addView(TextView(this@TeacherFeatureListActivity).apply {
            text = label
            textSize = 12f
            setTextColor(getColor(R.color.textSecondary))
            setPadding(0, if (root.childCount == 0) 0 else 14, 0, 4)
        })
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
            "notes" -> {
                if (selectedModuleId == null) {
                    row.id?.let { moduleRows[it] }?.let { openModuleWorkspace(it, "notes") }
                } else {
                    onNoteRowClicked(row)
                }
            }
            "absences" -> {
                if (selectedModuleId == null) {
                    row.id?.let { moduleRows[it] }?.let { openModuleWorkspace(it, "absences") }
                } else {
                    onAbsenceRowClicked(row)
                }
            }
            "courses" -> onCourseRowClicked(row)
            "announcements" -> onAnnouncementRowClicked(row)
            "assignments" -> onAssignmentRowClicked(row)
            "timetable" -> onTimetableRowClicked(row)
            "history" -> {
                if (selectedModuleId == null) {
                    row.id?.let { moduleRows[it] }?.let { openFeatureWithModule("history", it) }
                } else {
                    onHistoryRowClicked(row)
                }
            }
            "notifications" -> onNotificationRowClicked(row)
        }
    }

    private fun onModuleRowClicked(row: UiRow) {
        val module = row.id?.let { moduleRows[it] } ?: return
        openFeatureWithModule("students", module)
    }

    private fun onNotificationRowClicked(row: UiRow) {
        val notification = row.id?.let { notificationRows[it] } ?: return
        notificationReadStore.markRead(notification)
        val feature = AcademicNotificationStatus.actionFeature(notification)
        if (feature != "notifications") {
            navigateToFeature(feature)
        } else {
            Toast.makeText(this, notification.message, Toast.LENGTH_LONG).show()
            renderNotifications(notificationRows.values.toList())
        }
    }

    private fun onHistoryRowClicked(row: UiRow) {
        val event = row.id?.let { historyRows[it] } ?: return
        AlertDialog.Builder(this)
            .setTitle(event.title)
            .setMessage(AcademicHistoryTimelineUi.details(event))
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun onTimetableRowClicked(row: UiRow) {
        val session = row.id?.let { timetableRows[it] } ?: return
        val options = arrayOf(
            "Ouvrir la liste des etudiants",
            "Faire l'appel",
            "Entrer les notes"
        )
        AlertDialog.Builder(this)
            .setTitle(session.moduleNom ?: "Seance")
            .setMessage(timetableSessionSubtitle(session))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSessionStudents(session)
                    1 -> openSessionWorkspace(session, "absences")
                    2 -> openSessionWorkspace(session, "notes")
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
        startWithTransition(intent)
    }

    private fun createAssignmentIntent(): Intent {
        val intent = Intent(this, TeacherCreateAssignmentActivity::class.java)
        selectedModuleId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_MODULE_ID, it) }
        selectedClasseId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_CLASSE_ID, it) }
        selectedFiliereId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_FILIERE_ID, it) }
        return intent
    }

    private fun createAnnouncementIntent(): Intent {
        val intent = Intent(this, TeacherCreateAnnouncementActivity::class.java)
        selectedModuleId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_MODULE_ID, it) }
        selectedClasseId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_CLASSE_ID, it) }
        selectedFiliereId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_FILIERE_ID, it) }
        return intent
    }

    private fun openModuleChooserForFeature(feature: String) {
        lifecycleScope.launch {
            if (modulesCache.isEmpty()) loadFilterSources()
            val selectedModule = selectedModuleId?.let { moduleRows[it] ?: modulesCache.firstOrNull { module -> module.id == it } }
            if (selectedModule != null) {
                openFeatureWithModule(feature, selectedModule)
                return@launch
            }
            if (modulesCache.isEmpty()) {
                showError("Aucun module disponible.")
                return@launch
            }

            AlertDialog.Builder(this@TeacherFeatureListActivity)
                .setTitle("Choisir un module")
                .setItems(modulesCache.map { "${it.nom} (${it.code})" }.toTypedArray()) { _, which ->
                    openFeatureWithModule(feature, modulesCache[which])
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun openSessionStudents(session: TimetableItem) {
        val intent = Intent(this, TeacherFeatureListActivity::class.java)
            .putExtra(EXTRA_FEATURE, "students")
            .putExtra(EXTRA_SESSION_DATE, sessionDate(session).toString())
            .putExtra(EXTRA_SESSION_HOURS, sessionHours(session))
        session.moduleId?.let { intent.putExtra(EXTRA_MODULE_ID, it) }
        session.classeId?.let { intent.putExtra(EXTRA_CLASSE_ID, it) }
        session.filiereId?.let { intent.putExtra(EXTRA_FILIERE_ID, it) }
        startWithTransition(intent)
    }

    private fun openSessionWorkspace(session: TimetableItem, initialMode: String) {
        val module = session.moduleId?.let { moduleRows[it] ?: modulesCache.firstOrNull { item -> item.id == it } }
        val moduleId = session.moduleId ?: module?.id
        if (moduleId == null) {
            showError("Module introuvable pour cette seance.")
            return
        }

        val intent = Intent(this, TeacherModuleWorkspaceActivity::class.java)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_ID, moduleId)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_NAME, module?.nom ?: session.moduleNom ?: "Module")
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_CODE, module?.code ?: session.moduleCode.orEmpty())
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_SEMESTRE, module?.semestre ?: "S1")
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_INITIAL_MODE, initialMode)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_DATE, sessionDate(session).toString())
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_HOURS, sessionHours(session))
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_LABEL, sessionLabel(session))
        session.classeId?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_CLASSE_ID, it) }
        session.classeNom?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_CLASSE_NAME, it) }
        session.filiereId?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_ID, it) }
        session.filiereNom?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_NAME, it) }
        startWithTransition(intent)
    }

    private fun openModuleChooserForWorkspace(initialMode: String) {
        lifecycleScope.launch {
            if (modulesCache.isEmpty()) loadFilterSources()
            val selectedModule = selectedModuleId?.let { moduleRows[it] ?: modulesCache.firstOrNull { module -> module.id == it } }
            if (selectedModule != null) {
                openModuleWorkspace(selectedModule, initialMode)
                return@launch
            }
            when (val timetableResult = repository.timetable()) {
                is ApiResult.Success -> {
                    val session = selectSmartSession(timetableResult.data)
                    if (session != null) {
                        Toast.makeText(
                            this@TeacherFeatureListActivity,
                            "Session suggeree depuis l'emploi du temps.",
                            Toast.LENGTH_SHORT
                        ).show()
                        openSessionWorkspace(session, initialMode)
                        return@launch
                    }
                }
                is ApiResult.Error -> Unit
            }
            if (modulesCache.isEmpty()) {
                showError("Aucun module disponible.")
                return@launch
            }

            AlertDialog.Builder(this@TeacherFeatureListActivity)
                .setTitle("Choisir un module")
                .setItems(modulesCache.map { "${it.nom} (${it.code})" }.toTypedArray()) { _, which ->
                    openModuleWorkspace(modulesCache[which], initialMode)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun openModuleWorkspace(module: TeacherModuleItem, initialMode: String) {
        val intent = Intent(this, TeacherModuleWorkspaceActivity::class.java)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_ID, module.id)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_NAME, module.nom)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_CODE, module.code)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_SEMESTRE, module.semestre)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_INITIAL_MODE, initialMode)
        module.filiereId?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_ID, it) }
        module.filiereNom?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_NAME, it) }
        startWithTransition(intent)
    }

    private fun openStatistics(module: TeacherModuleItem? = null) {
        val intent = Intent(this, AcademicStatisticsActivity::class.java)
            .putExtra(AcademicStatisticsActivity.EXTRA_ROLE, AcademicStatisticsActivity.ROLE_TEACHER)
        (module?.id ?: selectedModuleId)?.let { intent.putExtra(AcademicStatisticsActivity.EXTRA_MODULE_ID, it) }
        startWithTransition(intent)
    }

    private fun selectedModule(): TeacherModuleItem? {
        val moduleId = selectedModuleId ?: return null
        return moduleRows[moduleId] ?: modulesCache.firstOrNull { it.id == moduleId }
    }

    private fun defaultModuleSemestre(): String {
        return selectedModule()?.semestre?.takeIf { it.isNotBlank() } ?: "S1"
    }

    private fun defaultAcademicYear(): String {
        val today = LocalDate.now()
        val startYear = if (today.monthValue >= 9) today.year else today.year - 1
        return "$startYear-${startYear + 1}"
    }

    private fun defaultSessionDateText(): String {
        return selectedDate?.toString() ?: suggestedSessionDate ?: LocalDate.now().toString()
    }

    private fun defaultSessionHoursText(): String {
        return suggestedSessionHours?.toString() ?: "2"
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
                    annee = anneeInput.text?.toString().orEmpty().ifBlank { defaultAcademicYear() },
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
                    val semestreInput = EditText(this@TeacherFeatureListActivity).apply {
                        hint = "Semestre (S1/S2)"
                        setText(defaultModuleSemestre())
                    }
                    val anneeInput = EditText(this@TeacherFeatureListActivity).apply {
                        hint = "Annee academique"
                        setText(defaultAcademicYear())
                    }
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
                                annee = anneeInput.text?.toString().orEmpty().ifBlank { defaultAcademicYear() },
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
            when (val result = repository.upsertNote(
                studentId,
                moduleId,
                semestre,
                annee,
                noteCc,
                noteExamen,
                cacheClasseId = selectedClasseId
            )) {
                is ApiResult.Success -> Toast.makeText(
                    this@TeacherFeatureListActivity,
                    AcademicNotificationCopy.success("Note enregistree"),
                    Toast.LENGTH_LONG
                ).show()
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
                is ApiResult.Success -> showSuccess(result.data.message)
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
                        setText(defaultSessionDateText())
                    }
                    val hoursInput = EditText(this@TeacherFeatureListActivity).apply {
                        hint = "Nombre d'heures"
                        setText(defaultSessionHoursText())
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
            when (val result = repository.createAbsence(
                studentId,
                moduleId,
                date,
                hours,
                cacheClasseId = selectedClasseId
            )) {
                is ApiResult.Success -> Toast.makeText(
                    this@TeacherFeatureListActivity,
                    AcademicNotificationCopy.success("Absence enregistree"),
                    Toast.LENGTH_LONG
                ).show()
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
                is ApiResult.Success -> showSuccess("Absence justifiee.")
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
                is ApiResult.Success -> showSuccess(result.data.message)
                is ApiResult.Error -> showError(result.message)
            }
            loadFeature(currentFeature)
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun onCourseRowClicked(row: UiRow) {
        val course = row.id?.let { courseRows[it] } ?: return
        val documents = CourseDocumentUi.documentsFor(course)
        val options = mutableListOf<String>()
        options += "Voir le cours"
        when (documents.size) {
            0 -> Unit
            1 -> options += "Voir / Telecharger"
            else -> options += "Voir les fichiers (${documents.size})"
        }
        options += "Ajouter un document"
        if (documents.isNotEmpty()) {
            options += "Remplacer tous les documents"
            options += "Supprimer tous les documents"
        }
        options += "Supprimer le cours"

        AlertDialog.Builder(this)
            .setTitle(course.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Voir le cours" -> showCourseDetails(course)
                    "Voir / Telecharger" -> openCourseDocument(documents.first())
                    "Voir les fichiers (${documents.size})" -> showCourseDocumentsDialog(course, documents)
                    "Ajouter un document" -> {
                        pendingReplaceAction = PendingReplaceAction(ReplaceType.COURSE_ADD_FILE, course.id)
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Remplacer tous les documents" -> {
                        pendingReplaceAction = PendingReplaceAction(ReplaceType.COURSE_FILE, course.id)
                        filePicker.launch(arrayOf("*/*"))
                    }
                    "Supprimer tous les documents" -> confirmAction(
                        title = "Supprimer les documents",
                        message = "Voulez-vous supprimer tous les documents de ce cours ?"
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
            ReplaceType.COURSE_ADD_FILE -> "files"
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
                ReplaceType.COURSE_ADD_FILE -> when (val result = repository.addCourseFiles(action.entityId, listOf(filePart))) {
                    is ApiResult.Success -> Toast.makeText(
                        this@TeacherFeatureListActivity,
                        AcademicNotificationCopy.success("Document ajoute au cours"),
                        Toast.LENGTH_LONG
                    ).show()
                    is ApiResult.Error -> showError(result.message)
                }
                ReplaceType.COURSE_FILE -> when (val result = repository.replaceCourseFile(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(
                        this@TeacherFeatureListActivity,
                        AcademicNotificationCopy.success("Document du cours remplace"),
                        Toast.LENGTH_LONG
                    ).show()
                    is ApiResult.Error -> showError(result.message)
                }
                ReplaceType.ASSIGNMENT_ATTACHMENT -> when (val result = repository.replaceAssignmentAttachment(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(
                        this@TeacherFeatureListActivity,
                        AcademicNotificationCopy.success("Piece jointe du devoir remplacee"),
                        Toast.LENGTH_LONG
                    ).show()
                    is ApiResult.Error -> showError(result.message)
                }
                ReplaceType.ANNOUNCEMENT_ATTACHMENT -> when (val result = repository.replaceAnnouncementAttachment(action.entityId, filePart)) {
                    is ApiResult.Success -> Toast.makeText(
                        this@TeacherFeatureListActivity,
                        AcademicNotificationCopy.success("Document de l'annonce remplace"),
                        Toast.LENGTH_LONG
                    ).show()
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
                is ApiResult.Success -> showSuccess("Document du cours supprime.")
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
                is ApiResult.Success -> showSuccess(result.data.message)
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
                is ApiResult.Success -> showSuccess("Piece jointe du devoir supprimee.")
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
                is ApiResult.Success -> showSuccess("Document de l'annonce supprime.")
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
                is ApiResult.Success -> showSuccess(result.data.message)
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
                is ApiResult.Success -> showSuccess(result.data.message)
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
        startWithTransition(intent)
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
        binding.tvFilterSummary.visibility = android.view.View.VISIBLE
        binding.tvFilterSummary.text = NotificationCenterUi.summary(items, notificationReadStore)
    }

    private fun confirmAction(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Confirmer") { _, _ -> onConfirm() }
            .show()
    }

    private fun openExternal(url: String, suggestedFileName: String? = null) {
        Toast.makeText(this, "Telechargement du document...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = AuthenticatedFileOpener.downloadAndOpen(
                this@TeacherFeatureListActivity,
                url,
                suggestedFileName
            )) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> showError(result.message)
            }
        }
    }

    private fun startWithTransition(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_FEATURE = "extra_feature"
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_CLASSE_ID = "extra_classe_id"
        const val EXTRA_FILIERE_ID = "extra_filiere_id"
        const val EXTRA_SESSION_DATE = "extra_session_date"
        const val EXTRA_SESSION_HOURS = "extra_session_hours"
        private const val FILTER_PREFS = "teacher_feature_filters"
    }
}

private data class PendingReplaceAction(
    val type: ReplaceType,
    val entityId: Long
)

private enum class ReplaceType {
    COURSE_ADD_FILE,
    COURSE_FILE,
    ASSIGNMENT_ATTACHMENT,
    ANNOUNCEMENT_ATTACHMENT
}

private data class FilterOption(
    val id: Long?,
    val label: String,
    val filiereId: Long? = null
)
