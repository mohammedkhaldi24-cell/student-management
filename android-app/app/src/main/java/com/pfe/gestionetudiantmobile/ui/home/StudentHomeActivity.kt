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
import com.pfe.gestionetudiantmobile.ui.student.StudentFeatureListActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
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

        binding.tvWelcome.text = user.fullName.uppercase(Locale.ROOT)
        val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))
        binding.tvRole.text = "Mon espace etudiant • $dateText"

        binding.btnNotes.setOnClickListener { openFeature("notes") }
        binding.btnAbsences.setOnClickListener { openFeature("absences") }
        binding.btnTimetable.setOnClickListener { openFeature("timetable") }
        binding.btnCourses.setOnClickListener { openFeature("courses") }
        binding.btnAnnouncements.setOnClickListener { openFeature("announcements") }
        binding.btnAssignments.setOnClickListener { openFeature("assignments") }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }

        loadDashboard()
    }

    private fun loadDashboard() {
        lifecycleScope.launch {
            binding.swipeLayout.isRefreshing = true
            when (val result = studentRepository.dashboard()) {
                is ApiResult.Success -> {
                    val dashboard = result.data
                    binding.tvAverage.text = String.format(Locale.ROOT, "Moyenne generale\n%.2f /20", dashboard.moyenneGenerale)
                    binding.tvAbsenceHours.text = "Absences totales\n${dashboard.totalAbsenceHours} h"
                    binding.tvUpcoming.text = "Devoirs a venir\n${dashboard.upcomingAssignments.size}"
                    binding.tvOverdue.text = "En retard\n${dashboard.overdueAssignmentsCount}"
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@StudentHomeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.swipeLayout.setOnRefreshListener { loadDashboard() }
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, StudentFeatureListActivity::class.java)
        intent.putExtra(StudentFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
