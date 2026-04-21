package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.repository.AdminRepository
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.model.TopStudentItem
import com.pfe.gestionetudiantmobile.databinding.ActivityAdminHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.common.PrimaryBottomNav
import com.pfe.gestionetudiantmobile.ui.common.ProfileUi
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding
    private val adminRepository = AdminRepository()
    private val authRepository = AuthRepository()
    private lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.adminPrimary)

        sessionStore = SessionStore(this)
        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }

        binding.tvWelcome.text = "Bienvenue, ${user.fullName}"
        binding.btnUsers.setOnClickListener { openFeature("users") }
        binding.btnFilieres.setOnClickListener { openFeature("filieres") }
        binding.btnClasses.setOnClickListener { openFeature("classes") }
        binding.btnModules.setOnClickListener { openFeature("modules") }
        binding.btnTimetable.setOnClickListener { openFeature("timetable") }
        binding.btnProfile.setOnClickListener { showProfileDialog() }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }

        configureBottomNavigation()
        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            when (val result = adminRepository.dashboard()) {
                is ApiResult.Success -> {
                    val dashboard = result.data
                    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))
                    binding.tvStats.text = "Plateforme academique - $dateText\nNotes: ${dashboard.totalNotes} - Absences: ${dashboard.totalAbsences}"
                    binding.tvTotalUsers.text = dashboard.totalUsers.toString()
                    binding.tvTotalStudents.text = dashboard.totalStudents.toString()
                    binding.tvTotalTeachers.text = dashboard.totalTeachers.toString()
                    binding.tvTotalFilieres.text = dashboard.totalFilieres.toString()
                    binding.tvTotalClasses.text = dashboard.totalClasses.toString()
                    binding.tvTotalModules.text = dashboard.totalModules.toString()
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@AdminHomeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }

            when (val topResult = adminRepository.topStudents(limit = 5)) {
                is ApiResult.Success -> renderTopStudents(topResult.data)
                is ApiResult.Error -> renderTopStudentsUnavailable()
            }
        }
    }

    private fun renderTopStudents(students: List<TopStudentItem>) {
        binding.tvAdminTopStudentsSubtitle.text = if (students.isEmpty()) {
            "Top 5 selon la moyenne finale"
        } else {
            "Top ${students.size} selon la moyenne finale"
        }
        binding.tvAdminTopStudentsEmpty.visibility = if (students.isEmpty()) View.VISIBLE else View.GONE
        binding.tvAdminTopStudentsEmpty.text = "Aucune note finale disponible."

        setTopStudentRow(
            binding.rowAdminTopStudent1,
            binding.tvAdminTopStudentName1,
            binding.tvAdminTopStudentAverage1,
            students.getOrNull(0)
        )
        setTopStudentRow(
            binding.rowAdminTopStudent2,
            binding.tvAdminTopStudentName2,
            binding.tvAdminTopStudentAverage2,
            students.getOrNull(1)
        )
        setTopStudentRow(
            binding.rowAdminTopStudent3,
            binding.tvAdminTopStudentName3,
            binding.tvAdminTopStudentAverage3,
            students.getOrNull(2)
        )
        setTopStudentRow(
            binding.rowAdminTopStudent4,
            binding.tvAdminTopStudentName4,
            binding.tvAdminTopStudentAverage4,
            students.getOrNull(3)
        )
        setTopStudentRow(
            binding.rowAdminTopStudent5,
            binding.tvAdminTopStudentName5,
            binding.tvAdminTopStudentAverage5,
            students.getOrNull(4)
        )
    }

    private fun renderTopStudentsUnavailable() {
        binding.tvAdminTopStudentsSubtitle.text = "Top 5 selon la moyenne finale"
        binding.tvAdminTopStudentsEmpty.visibility = View.VISIBLE
        binding.tvAdminTopStudentsEmpty.text = "Classement indisponible pour le moment."
        setTopStudentRow(binding.rowAdminTopStudent1, binding.tvAdminTopStudentName1, binding.tvAdminTopStudentAverage1, null)
        setTopStudentRow(binding.rowAdminTopStudent2, binding.tvAdminTopStudentName2, binding.tvAdminTopStudentAverage2, null)
        setTopStudentRow(binding.rowAdminTopStudent3, binding.tvAdminTopStudentName3, binding.tvAdminTopStudentAverage3, null)
        setTopStudentRow(binding.rowAdminTopStudent4, binding.tvAdminTopStudentName4, binding.tvAdminTopStudentAverage4, null)
        setTopStudentRow(binding.rowAdminTopStudent5, binding.tvAdminTopStudentName5, binding.tvAdminTopStudentAverage5, null)
    }

    private fun setTopStudentRow(
        row: View,
        nameView: TextView,
        averageView: TextView,
        student: TopStudentItem?
    ) {
        row.visibility = if (student == null) View.GONE else View.VISIBLE
        if (student == null) return
        nameView.text = student.name
        averageView.text = "${formatDecimal(student.average)}/20"
    }

    private fun formatDecimal(value: Double): String {
        return String.format(Locale.ROOT, "%.1f", value)
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, AdminFeatureListActivity::class.java)
        intent.putExtra(AdminFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun configureBottomNavigation() {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = PrimaryBottomNav.Role.ADMIN,
            currentFeature = "dashboard",
            onDashboard = { },
            onFeature = { openFeature(it) },
            onProfile = { showProfileDialog() }
        )
    }

    private fun showProfileDialog() {
        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }
        ProfileUi.showSessionProfileDialog(this, user) {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }
    }
}
