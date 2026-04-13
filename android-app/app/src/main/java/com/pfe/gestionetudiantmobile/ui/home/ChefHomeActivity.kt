package com.pfe.gestionetudiantmobile.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.repository.AuthRepository
import com.pfe.gestionetudiantmobile.data.repository.ChefRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityChefHomeBinding
import com.pfe.gestionetudiantmobile.ui.auth.LoginActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class ChefHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChefHomeBinding
    private val chefRepository = ChefRepository()
    private val authRepository = AuthRepository()
    private lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChefHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.chefPrimary)

        sessionStore = SessionStore(this)
        val user = sessionStore.getUser()
        if (user == null) {
            goLogin()
            return
        }

        binding.tvWelcome.text = "Bienvenue, ${user.fullName}"
        binding.btnStudents.setOnClickListener { openFeature("students") }
        binding.btnNotes.setOnClickListener { openFeature("notes") }
        binding.btnAbsences.setOnClickListener { openFeature("absences") }
        binding.btnCourses.setOnClickListener { openFeature("courses") }
        binding.btnAnnouncements.setOnClickListener { openFeature("announcements") }
        binding.btnTimetable.setOnClickListener { openFeature("timetable") }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                sessionStore.clear()
                goLogin()
            }
        }

        lifecycleScope.launch {
            when (val result = chefRepository.dashboard()) {
                is ApiResult.Success -> {
                    val dashboard = result.data
                    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))
                    binding.tvStats.text = "Filiere ${dashboard.filiereNom} • $dateText\n" +
                            "Classes: ${dashboard.totalClasses} • Etudiants: ${dashboard.totalStudents}\n" +
                            "Cours: ${dashboard.totalCourses} • Annonces: ${dashboard.totalAnnouncements}"
                }
                is ApiResult.Error -> {
                    Toast.makeText(this@ChefHomeActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun openFeature(feature: String) {
        val intent = Intent(this, ChefFeatureListActivity::class.java)
        intent.putExtra(ChefFeatureListActivity.EXTRA_FEATURE, feature)
        startActivity(intent)
    }
}
