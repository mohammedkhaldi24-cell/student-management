package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.TeacherDashboard
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.AcademicStatisticsActivity
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherCreateAnnouncementActivity
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherCreateAssignmentActivity
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherFeatureListActivity
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherModuleWorkspaceActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class TeacherHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherHomeBinding
    private val teacherRepository = TeacherRepository()
    private val authRepository = AuthRepository()
    private lateinit var sessionStore: SessionStore
    private var smartSession: TimetableItem? = null
    private var smartModule: TeacherModuleItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.teacherPrimary)

        sessionStore = SessionStore(this)

        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }

        binding.tvWelcome.text = "Bonjour, ${user.fullName}"
        val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))

        binding.tvRole.text = "Aujourd'hui: etudiants, appel et devoirs - $dateText"

        binding.cardNotes.setOnClickListener { openSmartStudents() }
        binding.cardAbsences.setOnClickListener { openFeature("absences") }
        binding.cardAssignments.setOnClickListener { openFeature("assignments") }
        binding.cardAnnouncements.setOnClickListener { openFeature("announcements") }
        binding.cardCourses.setOnClickListener { openFeature("courses") }
        binding.cardTimetable.setOnClickListener { openFeature("timetable") }
        binding.cardQuickNotes.setOnClickListener { openSmartWorkspace("notes") }
        binding.cardQuickAbsences.setOnClickListener { openSmartWorkspace("absences") }
        binding.cardQuickAssignment.setOnClickListener { openSmartAssignment() }
        binding.cardQuickAnnouncement.setOnClickListener { openSmartAnnouncement() }
        binding.cardSummaryAverage.setOnClickListener { openStatistics() }
        binding.cardSummaryAbsence.setOnClickListener { openStatistics() }
        binding.cardSummaryAssignments.setOnClickListener { openStatistics() }
        binding.cardSummaryTop.setOnClickListener { openStatistics() }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }

        binding.swipeLayout.setOnRefreshListener { loadDashboard() }
        configureBottomNavigation()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            val dashboardDeferred = async { teacherRepository.dashboard() }
            val notesDeferred = async { teacherRepository.notes() }
            val absencesDeferred = async { teacherRepository.absences() }
            val assignmentsDeferred = async { teacherRepository.assignments() }
            val timetableDeferred = async { teacherRepository.timetable() }
            val modulesDeferred = async { teacherRepository.modules() }

            try {
                when (val result = dashboardDeferred.await()) {
                    is ApiResult.Success -> {
                        val dashboard = result.data
                        renderDashboard(dashboard)
                        renderSmartQuickActions(timetableDeferred.await(), modulesDeferred.await())
                        renderSummary(
                            dashboard = dashboard,
                            notesResult = notesDeferred.await(),
                            absencesResult = absencesDeferred.await(),
                            assignmentsResult = assignmentsDeferred.await()
                        )
                    }

                    is ApiResult.Error -> {
                        renderSmartQuickActions(timetableDeferred.await(), modulesDeferred.await())
                        renderSummaryUnavailable()
                        Toast.makeText(this@TeacherHomeActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                binding.swipeLayout.isRefreshing = false
            }
        }
    }

    private fun renderDashboard(dashboard: TeacherDashboard) {
        binding.tvNotesValue.text = dashboard.totalStudents.toString()
        binding.tvNotesContext.text = "Recherche et filtres"
        binding.tvAbsencesValue.text = dashboard.totalModules.toString()
        binding.tvAbsencesContext.text = "Modules pour l'appel"
        binding.tvAssignmentsValue.text = dashboard.totalAssignments.toString()
        binding.tvAssignmentsContext.text = if (dashboard.pendingSubmissions > 0) {
            "${dashboard.pendingSubmissions} soumissions en attente"
        } else {
            "Aucune soumission en attente"
        }
        binding.tvAnnouncementsValue.text = dashboard.totalAnnouncements.toString()
        binding.tvAnnouncementsContext.text = dashboard.recentAnnouncements.firstOrNull()?.title
            ?: "Aucune annonce recente"
        binding.tvCoursesValue.text = dashboard.totalCourses.toString()
        binding.tvCoursesContext.text = dashboard.recentCourses.firstOrNull()?.title
            ?: "Aucun cours publie"
        binding.tvTimetableValue.text = dashboard.totalModules.toString()
        binding.tvTimetableContext.text = "Emploi hebdomadaire"
    }

    private fun renderSummary(
        dashboard: TeacherDashboard,
        notesResult: ApiResult<List<NoteItem>>,
        absencesResult: ApiResult<List<AbsenceItem>>,
        assignmentsResult: ApiResult<List<AssignmentItem>>
    ) {
        renderClassAverage(notesResult)
        renderWeeklyAbsenceRate(dashboard, absencesResult)
        renderAssignmentSummary(dashboard, assignmentsResult)
        renderTopStudents(notesResult)
    }

    private fun renderClassAverage(notesResult: ApiResult<List<NoteItem>>) {
        when (notesResult) {
            is ApiResult.Success -> {
                val scores = notesResult.data.mapNotNull { it.noteFinal }
                if (scores.isEmpty()) {
                    binding.tvSummaryAverageValue.text = "--"
                    binding.tvSummaryAverageContext.text = "Aucune note finale"
                    return
                }
                binding.tvSummaryAverageValue.text = "${formatDecimal(scores.average())}/20"
                binding.tvSummaryAverageContext.text = "${scores.size} notes finales"
            }

            is ApiResult.Error -> {
                binding.tvSummaryAverageValue.text = "--"
                binding.tvSummaryAverageContext.text = "Notes indisponibles"
            }
        }
    }

    private fun renderWeeklyAbsenceRate(
        dashboard: TeacherDashboard,
        absencesResult: ApiResult<List<AbsenceItem>>
    ) {
        when (absencesResult) {
            is ApiResult.Success -> {
                val weekStart = LocalDate.now().minusDays(6)
                val weeklyAbsences = absencesResult.data.filter { !it.dateAbsence.isBefore(weekStart) }
                val absentStudents = weeklyAbsences.mapNotNull { absence ->
                    absence.studentId?.toString() ?: absence.matricule?.takeIf { it.isNotBlank() }
                }.distinct().size
                val weeklyHours = weeklyAbsences.sumOf { it.nombreHeures }

                if (weeklyAbsences.isEmpty()) {
                    binding.tvSummaryAbsenceValue.text = "0%"
                    binding.tvSummaryAbsenceContext.text = "Aucune absence cette semaine"
                    return
                }

                val rate = if (dashboard.totalStudents > 0) {
                    absentStudents.toDouble() * 100.0 / dashboard.totalStudents.toDouble()
                } else {
                    null
                }
                binding.tvSummaryAbsenceValue.text = rate?.let { "${formatDecimal(it)}%" } ?: "${weeklyHours}h"
                binding.tvSummaryAbsenceContext.text = "$absentStudents etudiant(s), $weeklyHours h"
            }

            is ApiResult.Error -> {
                binding.tvSummaryAbsenceValue.text = "--"
                binding.tvSummaryAbsenceContext.text = "Absences indisponibles"
            }
        }
    }

    private fun renderAssignmentSummary(
        dashboard: TeacherDashboard,
        assignmentsResult: ApiResult<List<AssignmentItem>>
    ) {
        val lateAssignments = when (assignmentsResult) {
            is ApiResult.Success -> assignmentsResult.data.count { it.overdue }
            is ApiResult.Error -> dashboard.recentAssignments.count { it.overdue }
        }
        binding.tvSummaryAssignmentsValue.text = "${dashboard.pendingSubmissions}/$lateAssignments"
        binding.tvSummaryAssignmentsContext.text = when (assignmentsResult) {
            is ApiResult.Success -> "soumissions attente / devoirs retard"
            is ApiResult.Error -> "retards sur devoirs recents"
        }
    }

    private fun renderTopStudents(notesResult: ApiResult<List<NoteItem>>) {
        when (notesResult) {
            is ApiResult.Success -> {
                val topStudents = topStudentAverages(notesResult.data)
                if (topStudents.isEmpty()) {
                    binding.tvSummaryTopValue.text = "--"
                    binding.tvSummaryTopContext.text = "Aucune note exploitable"
                    renderTopStudentsSection(emptyList())
                    return
                }
                binding.tvSummaryTopValue.text = "${formatDecimal(topStudents.first().average)}/20"
                binding.tvSummaryTopContext.text = topStudents.joinToString(" | ") {
                    "${it.name} ${formatDecimal(it.average)}"
                }
                renderTopStudentsSection(topStudents)
            }

            is ApiResult.Error -> {
                binding.tvSummaryTopValue.text = "--"
                binding.tvSummaryTopContext.text = "Top indisponible"
                renderTopStudentsUnavailable()
            }
        }
    }

    private fun renderTopStudentsSection(topStudents: List<StudentAverage>) {
        binding.tvTopStudentsSubtitle.text = if (topStudents.isEmpty()) {
            "Top 3 selon la moyenne finale"
        } else {
            "Top ${topStudents.size} selon la moyenne finale"
        }
        binding.tvTopStudentsEmpty.visibility = if (topStudents.isEmpty()) View.VISIBLE else View.GONE
        binding.tvTopStudentsEmpty.text = "Aucune note finale disponible."

        setTopStudentRow(
            binding.rowTopStudent1,
            binding.tvTopStudentName1,
            binding.tvTopStudentAverage1,
            topStudents.getOrNull(0)
        )
        setTopStudentRow(
            binding.rowTopStudent2,
            binding.tvTopStudentName2,
            binding.tvTopStudentAverage2,
            topStudents.getOrNull(1)
        )
        setTopStudentRow(
            binding.rowTopStudent3,
            binding.tvTopStudentName3,
            binding.tvTopStudentAverage3,
            topStudents.getOrNull(2)
        )
    }

    private fun renderTopStudentsUnavailable() {
        binding.tvTopStudentsSubtitle.text = "Top 3 selon la moyenne finale"
        binding.tvTopStudentsEmpty.visibility = View.VISIBLE
        binding.tvTopStudentsEmpty.text = "Classement indisponible pour le moment."
        setTopStudentRow(binding.rowTopStudent1, binding.tvTopStudentName1, binding.tvTopStudentAverage1, null)
        setTopStudentRow(binding.rowTopStudent2, binding.tvTopStudentName2, binding.tvTopStudentAverage2, null)
        setTopStudentRow(binding.rowTopStudent3, binding.tvTopStudentName3, binding.tvTopStudentAverage3, null)
    }

    private fun setTopStudentRow(
        row: View,
        nameView: TextView,
        averageView: TextView,
        student: StudentAverage?
    ) {
        row.visibility = if (student == null) View.GONE else View.VISIBLE
        if (student == null) return
        nameView.text = student.name
        averageView.text = "${formatDecimal(student.average)}/20"
    }

    private fun renderSummaryUnavailable() {
        binding.tvSummaryAverageValue.text = "--"
        binding.tvSummaryAverageContext.text = "Dashboard indisponible"
        binding.tvSummaryAbsenceValue.text = "--"
        binding.tvSummaryAbsenceContext.text = "Dashboard indisponible"
        binding.tvSummaryAssignmentsValue.text = "--"
        binding.tvSummaryAssignmentsContext.text = "Dashboard indisponible"
        binding.tvSummaryTopValue.text = "--"
        binding.tvSummaryTopContext.text = "Dashboard indisponible"
        renderTopStudentsUnavailable()
    }

    private fun renderSmartQuickActions(
        timetableResult: ApiResult<List<TimetableItem>>,
        modulesResult: ApiResult<List<TeacherModuleItem>>
    ) {
        when (timetableResult) {
            is ApiResult.Success -> {
                val session = selectSmartSession(timetableResult.data)
                smartSession = session
                smartModule = when (modulesResult) {
                    is ApiResult.Success -> modulesResult.data.firstOrNull { it.id == session?.moduleId }
                    is ApiResult.Error -> null
                }
                if (session == null) {
                    renderNoSmartSession("Aucune session valide dans l'emploi du temps.")
                    return
                }

                val moduleLabel = smartModule?.nom?.takeIf { it.isNotBlank() }
                    ?: session.moduleNom?.takeIf { it.isNotBlank() }
                    ?: session.moduleCode?.takeIf { it.isNotBlank() }
                    ?: "Module detecte"
                val audience = listOfNotNull(
                    session.classeNom?.takeIf { it.isNotBlank() },
                    session.filiereNom?.takeIf { it.isNotBlank() }
                ).joinToString(" | ")

                binding.tvSmartSessionContext.text = "${smartSessionStatus(session)}: $moduleLabel - ${timeRange(session)}" +
                    if (audience.isNotBlank()) " - $audience" else ""
                binding.tvQuickNotesContext.text = moduleLabel
                binding.tvQuickAbsencesContext.text = timeRange(session)
                binding.tvQuickAssignmentContext.text = moduleLabel
                binding.tvQuickAnnouncementContext.text = audience.ifBlank { "Audience detectee" }
                loadSmartStudentCount(session)
            }

            is ApiResult.Error -> {
                smartSession = null
                smartModule = null
                renderNoSmartSession("Emploi du temps indisponible. Actions disponibles sans pre-remplissage.")
            }
        }
    }

    private fun renderNoSmartSession(message: String) {
        binding.tvSmartSessionContext.text = message
        binding.tvQuickNotesContext.text = "Choisir module"
        binding.tvQuickAbsencesContext.text = "Choisir session"
        binding.tvQuickAssignmentContext.text = "Formulaire simple"
        binding.tvQuickAnnouncementContext.text = "Choisir audience"
    }

    private fun loadSmartStudentCount(session: TimetableItem) {
        val moduleId = session.moduleId ?: return
        lifecycleScope.launch {
            when (val result = teacherRepository.students(moduleId, session.classeId, session.filiereId, null)) {
                is ApiResult.Success -> {
                    if (smartSession?.id != session.id) return@launch
                    val countLabel = if (result.data.isEmpty()) {
                        "Aucun etudiant trouve"
                    } else {
                        "${result.data.size} etudiants charges"
                    }
                    binding.tvQuickNotesContext.text = countLabel
                    binding.tvQuickAbsencesContext.text = "$countLabel - ${timeRange(session)}"
                }

                is ApiResult.Error -> {
                    if (smartSession?.id != session.id) return@launch
                    binding.tvQuickNotesContext.text = "Etudiants a charger"
                }
            }
        }
    }

    private fun selectSmartSession(items: List<TimetableItem>): TimetableItem? {
        val validSessions = items.filter { it.valide && it.moduleId != null }
        if (validSessions.isEmpty()) return null

        val today = LocalDate.now().dayOfWeek.value
        val now = LocalTime.now()
        val todaySessions = validSessions
            .filter { dayIndex(it.jour) == today }
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
        val sessionDay = dayIndex(session.jour) ?: return 8
        val distance = (sessionDay - today + 7) % 7
        return if (distance == 0 && !session.heureFin.isAfter(now)) 7 else distance
    }

    private fun dayIndex(day: String): Int? {
        val normalized = day.trim().lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("lun") || normalized.startsWith("mon") -> 1
            normalized.startsWith("mar") || normalized.startsWith("tue") -> 2
            normalized.startsWith("mer") || normalized.startsWith("wed") -> 3
            normalized.startsWith("jeu") || normalized.startsWith("thu") -> 4
            normalized.startsWith("ven") || normalized.startsWith("fri") -> 5
            normalized.startsWith("sam") || normalized.startsWith("sat") -> 6
            normalized.startsWith("dim") || normalized.startsWith("sun") -> 7
            else -> null
        }
    }

    private fun smartSessionStatus(session: TimetableItem): String {
        val today = LocalDate.now().dayOfWeek.value
        val now = LocalTime.now()
        return when {
            dayIndex(session.jour) == today && isCurrentSession(session, now) -> "Session en cours"
            dayIndex(session.jour) == today -> "Prochaine aujourd'hui"
            else -> "Prochaine session"
        }
    }

    private fun timeRange(session: TimetableItem): String {
        return "${session.jour} ${formatTime(session.heureDebut)}-${formatTime(session.heureFin)}"
    }

    private fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun sessionDate(session: TimetableItem): LocalDate {
        val sessionDay = dayIndex(session.jour) ?: return LocalDate.now()
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

    private fun topStudentAverages(notes: List<NoteItem>): List<StudentAverage> {
        return notes.mapNotNull { note ->
            val score = note.noteFinal ?: return@mapNotNull null
            val name = note.studentName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StudentScore(
                key = note.studentId?.toString() ?: note.matricule?.takeIf { it.isNotBlank() } ?: name,
                name = shortStudentName(name),
                score = score
            )
        }
            .groupBy { it.key }
            .map { (_, scores) ->
                StudentAverage(
                    name = scores.first().name,
                    average = scores.map { it.score }.average()
                )
            }
            .sortedByDescending { it.average }
            .take(3)
    }

    private fun shortStudentName(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> name
            parts.size == 1 -> parts.first()
            else -> "${parts.first()} ${parts.last()}"
        }
    }

    private fun formatDecimal(value: Double): String {
        return String.format(Locale.ROOT, "%.1f", value)
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, TeacherFeatureListActivity::class.java)
        intent.putExtra(TeacherFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSmartStudents() {
        val session = smartSession
        val moduleId = session?.moduleId
        if (session == null || moduleId == null) {
            openFeature("students")
            return
        }

        val intent = Intent(this, TeacherFeatureListActivity::class.java)
            .putExtra(TeacherFeatureListActivity.EXTRA_FEATURE, "students")
            .putExtra(TeacherFeatureListActivity.EXTRA_MODULE_ID, moduleId)
            .putExtra(TeacherFeatureListActivity.EXTRA_SESSION_DATE, sessionDate(session).toString())
            .putExtra(TeacherFeatureListActivity.EXTRA_SESSION_HOURS, sessionHours(session))
        session.classeId?.let { intent.putExtra(TeacherFeatureListActivity.EXTRA_CLASSE_ID, it) }
        session.filiereId?.let { intent.putExtra(TeacherFeatureListActivity.EXTRA_FILIERE_ID, it) }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSmartWorkspace(mode: String) {
        val session = smartSession
        val moduleId = session?.moduleId
        if (session == null || moduleId == null) {
            openFeature(if (mode == "absences") "absences" else "notes")
            return
        }

        val intent = Intent(this, TeacherModuleWorkspaceActivity::class.java)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_ID, moduleId)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_NAME, smartModule?.nom ?: session.moduleNom ?: "Module")
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_CODE, smartModule?.code ?: session.moduleCode.orEmpty())
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_MODULE_SEMESTRE, smartModule?.semestre ?: "S1")
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_INITIAL_MODE, mode)
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_DATE, sessionDate(session).toString())
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_HOURS, sessionHours(session))
            .putExtra(TeacherModuleWorkspaceActivity.EXTRA_SESSION_LABEL, sessionLabel(session))
        session.classeId?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_CLASSE_ID, it) }
        session.classeNom?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_CLASSE_NAME, it) }
        session.filiereId?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_ID, it) }
        session.filiereNom?.let { intent.putExtra(TeacherModuleWorkspaceActivity.EXTRA_FILIERE_NAME, it) }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSmartAssignment() {
        val intent = Intent(this, TeacherCreateAssignmentActivity::class.java)
        smartSession?.let { session ->
            session.moduleId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_MODULE_ID, it) }
            session.classeId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_CLASSE_ID, it) }
            session.filiereId?.let { intent.putExtra(TeacherCreateAssignmentActivity.EXTRA_FILIERE_ID, it) }
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openSmartAnnouncement() {
        val intent = Intent(this, TeacherCreateAnnouncementActivity::class.java)
        smartSession?.let { session ->
            session.moduleId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_MODULE_ID, it) }
            session.classeId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_CLASSE_ID, it) }
            session.filiereId?.let { intent.putExtra(TeacherCreateAnnouncementActivity.EXTRA_FILIERE_ID, it) }
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun openStatistics() {
        val intent = Intent(this, AcademicStatisticsActivity::class.java)
            .putExtra(AcademicStatisticsActivity.EXTRA_ROLE, AcademicStatisticsActivity.ROLE_TEACHER)
        smartModule?.id?.let { intent.putExtra(AcademicStatisticsActivity.EXTRA_MODULE_ID, it) }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.TEACHER,
            currentFeature = "dashboard",
            onDashboard = { loadDashboard() },
            onFeature = { openFeature(it) },
            onProfile = { openFeature("profile") }
        )
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

private data class StudentScore(
    val key: String,
    val name: String,
    val score: Double
)

private data class StudentAverage(
    val name: String,
    val average: Double
)
