package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.repository.AdminRepository
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityAdminHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
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

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }

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
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, AdminFeatureListActivity::class.java)
        intent.putExtra(AdminFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
    }
}
