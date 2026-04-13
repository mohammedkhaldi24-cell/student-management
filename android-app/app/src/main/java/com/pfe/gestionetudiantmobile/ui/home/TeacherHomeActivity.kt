package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityTeacherHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherFeatureListActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class TeacherHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherHomeBinding
    private val teacherRepository = TeacherRepository()
    private val authRepository = AuthRepository()
    private lateinit var sessionStore: SessionStore

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
        binding.tvRole.text = "Tableau de bord enseignant • $dateText"

        binding.btnModules.setOnClickListener { openFeature("modules") }
        binding.btnStudents.setOnClickListener { openFeature("students") }
        binding.btnNotes.setOnClickListener { openFeature("notes") }
        binding.btnAbsences.setOnClickListener { openFeature("absences") }
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
            when (val result = teacherRepository.dashboard()) {
                is ApiResult.Success -> {
                    val dashboard = result.data
                    binding.tvModules.text = "Modules enseignes\n${dashboard.totalModules}"
                    binding.tvStudents.text = "Etudiants concernes\n${dashboard.totalStudents}"
                    binding.tvAssignments.text = "Devoirs publies\n${dashboard.totalAssignments}"
                    binding.tvPending.text = "Soumissions en attente\n${dashboard.pendingSubmissions}"
                }

                is ApiResult.Error -> {
                    Toast.makeText(this@TeacherHomeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.swipeLayout.setOnRefreshListener { loadDashboard() }
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, TeacherFeatureListActivity::class.java)
        intent.putExtra(TeacherFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
