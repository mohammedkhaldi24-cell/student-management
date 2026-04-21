package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.StudentRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityStudentHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.student.StudentFeatureListActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.NotificationReadStore
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class StudentHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentHomeBinding
    private val studentRepository = StudentRepository()
    private val authRepository = AuthRepository()
    private lateinit var sessionStore: SessionStore
    private lateinit var notificationReadStore: NotificationReadStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.studentPrimary)

        sessionStore = SessionStore(this)

        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }
        notificationReadStore = NotificationReadStore(this, user.id)

        binding.tvWelcome.text = user.fullName.uppercase(Locale.ROOT)
        val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))

        binding.tvRole.text = "Aujourd'hui: devoirs, cours et notifications - $dateText"

        binding.cardNotes.setOnClickListener { openFeature("notes") }
        binding.cardAbsences.setOnClickListener { openFeature("absences") }
        binding.cardAssignments.setOnClickListener { openFeature("assignments") }
        binding.cardAnnouncements.setOnClickListener { openFeature("announcements") }
        binding.cardTimetable.setOnClickListener { openFeature("timetable") }
        binding.cardCourses.setOnClickListener { openFeature("courses") }
        binding.cardNotifications.setOnClickListener { openFeature("notifications") }

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
            when (val result = studentRepository.dashboard()) {
                is ApiResult.Success -> {
                    val dashboard = result.data
                    binding.tvNotesValue.text = String.format(Locale.ROOT, "%.2f", dashboard.moyenneGenerale)
                    binding.tvNotesContext.text = "Moyenne generale /20"
                    binding.tvAbsencesValue.text = "${dashboard.totalAbsenceHours} h"
                    binding.tvAbsencesContext.text = if (dashboard.totalAbsenceHours == 0) {
                        "Aucune absence"
                    } else {
                        "${dashboard.totalNonJustifiedHours} h non justifiees"
                    }
                    binding.tvAssignmentsValue.text = dashboard.upcomingAssignments.size.toString()
                    binding.tvAssignmentsContext.text = when {
                        dashboard.upcomingAssignments.isEmpty() -> "Aucun devoir urgent"
                        dashboard.overdueAssignmentsCount > 0 -> "${dashboard.overdueAssignmentsCount} en retard"
                        else -> "A rendre prochainement"
                    }
                    binding.tvAnnouncementsValue.text = dashboard.recentAnnouncements.size.toString()
                    binding.tvAnnouncementsContext.text = dashboard.recentAnnouncements.firstOrNull()?.title
                        ?: "Aucune annonce recente"

                    val nextSession = dashboard.upcomingSessions.firstOrNull()
                    binding.tvTimetableValue.text = dashboard.upcomingSessions.size.toString()
                    binding.tvTimetableContext.text = if (nextSession != null) {
                        "${nextSession.jour} ${nextSession.heureDebut} - ${nextSession.moduleNom ?: "Module"}"
                    } else {
                        "Aucune seance a venir"
                    }

                    binding.tvCoursesValue.text = dashboard.recentCourses.size.toString()
                    binding.tvCoursesContext.text = dashboard.recentCourses.firstOrNull()?.title
                        ?: "Aucun cours publie"

                    val unreadNotifications = notificationReadStore.unreadCount(dashboard.notifications)
                    binding.tvNotificationsValue.text = unreadNotifications.toString()
                    binding.tvNotificationsContext.text = when {
                        dashboard.notifications.isEmpty() -> "Aucun evenement recent"
                        unreadNotifications > 0 -> "$unreadNotifications non lu(s)"
                        else -> "Tout est lu"
                    }
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@StudentHomeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
            binding.swipeLayout.isRefreshing = false
        }
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, StudentFeatureListActivity::class.java)
        intent.putExtra(StudentFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.STUDENT,
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
