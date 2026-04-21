package com.pfe.gestionetudiantmobile.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pfe.gestionetudiantmobile.R
import com.pfe.gestionetudiantmobile.data.model.AcademicStatPoint
import com.pfe.gestionetudiantmobile.data.model.AcademicStatistics
import com.pfe.gestionetudiantmobile.data.repository.StudentRepository
import com.pfe.gestionetudiantmobile.data.repository.TeacherRepository
import com.pfe.gestionetudiantmobile.databinding.ActivityAcademicStatisticsBinding
import com.pfe.gestionetudiantmobile.ui.home.StudentHomeActivity
import com.pfe.gestionetudiantmobile.ui.home.TeacherHomeActivity
import com.pfe.gestionetudiantmobile.ui.student.StudentFeatureListActivity
import com.pfe.gestionetudiantmobile.ui.teacher.TeacherFeatureListActivity
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.SessionStore
import java.util.Locale
import kotlinx.coroutines.launch

class AcademicStatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAcademicStatisticsBinding
    private lateinit var sessionStore: SessionStore
    private val teacherRepository by lazy { TeacherRepository(this) }
    private val studentRepository by lazy { StudentRepository(this) }
    private val selectedModuleId: Long?
        get() = intent.getLongExtra(EXTRA_MODULE_ID, -1L).takeIf { it > 0L }
    private val role: String
        get() = intent.getStringExtra(EXTRA_ROLE)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: sessionStore.getUser()?.role?.trim()?.lowercase(Locale.ROOT).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAcademicStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sessionStore = SessionStore(this)

        val isTeacher = role.contains("teacher") || role.contains("enseignant")
        window.statusBarColor = ContextCompat.getColor(
            this,
            if (isTeacher) R.color.teacherPrimary else R.color.studentPrimary
        )
        binding.tvTitle.text = if (isTeacher) "Statistiques enseignant" else "Mes statistiques"
        binding.tvSubtitle.text = if (isTeacher) {
            "Moyennes, absences et devoirs par module."
        } else {
            "Moyennes, absences et devoirs a suivre."
        }
        binding.btnBack.setOnClickListener { finishWithTransition() }
        binding.btnStateRetry.setOnClickListener { loadStatistics() }
        configureBottomNavigation(isTeacher)
        loadStatistics()
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            showLoading()
            val result = if (role.contains("teacher") || role.contains("enseignant")) {
                teacherRepository.statistics(selectedModuleId)
            } else {
                studentRepository.statistics(selectedModuleId)
            }
            when (result) {
                is ApiResult.Success -> renderStatistics(result.data)
                is ApiResult.Error -> showError(result.message)
            }
        }
    }

    private fun renderStatistics(statistics: AcademicStatistics) {
        binding.layoutState.visibility = View.GONE
        binding.contentScroll.visibility = View.VISIBLE
        binding.tvSubtitle.text = statistics.summary.ifBlank { binding.tvSubtitle.text }

        binding.tvAverageSummary.text = summarizeAverage(statistics.averageByModule)
        binding.tvAbsenceSummary.text = summarizeAbsence(statistics.absenceRateByModule)
        binding.tvAssignmentSummary.text = summarizeCompletion(statistics.assignmentCompletionByModule)

        binding.chartAverage.setEntries(
            nextEntries = statistics.averageByModule.toChartEntries(R.color.statBlue),
            maxValue = 20.0,
            emptyLabel = "Aucune note disponible"
        )
        binding.chartAbsence.setEntries(
            nextEntries = statistics.absenceRateByModule.toChartEntries(R.color.statRed),
            maxValue = 100.0,
            emptyLabel = "Aucune absence disponible"
        )
        binding.chartAssignments.setEntries(
            nextEntries = statistics.assignmentCompletionByModule.toChartEntries(R.color.statGreen),
            maxValue = 100.0,
            emptyLabel = "Aucun devoir disponible"
        )
    }

    private fun showLoading() {
        binding.contentScroll.visibility = View.GONE
        binding.layoutState.visibility = View.VISIBLE
        binding.progressState.visibility = View.VISIBLE
        binding.btnStateRetry.visibility = View.GONE
        binding.tvStateTitle.text = "Chargement"
        binding.tvStateMessage.text = "Calcul des statistiques mobiles..."
    }

    private fun showError(message: String) {
        binding.contentScroll.visibility = View.GONE
        binding.layoutState.visibility = View.VISIBLE
        binding.progressState.visibility = View.GONE
        binding.btnStateRetry.visibility = View.VISIBLE
        binding.tvStateTitle.text = "Statistiques indisponibles"
        binding.tvStateMessage.text = message
    }

    private fun summarizeAverage(points: List<AcademicStatPoint>): String {
        val scored = points.filter { it.value > 0.0 }
        val best = scored.maxByOrNull { it.value }
        return when {
            points.isEmpty() -> "Aucun module a afficher."
            best == null -> "Aucune note finale exploitable pour le moment."
            else -> "Meilleure moyenne: ${best.label} (${best.valueLabel})."
        }
    }

    private fun summarizeAbsence(points: List<AcademicStatPoint>): String {
        val highest = points.maxByOrNull { it.value }
        return when {
            points.isEmpty() -> "Aucun module a afficher."
            highest == null || highest.value == 0.0 -> "Aucune absence calculee."
            else -> "Taux le plus eleve: ${highest.label} (${highest.valueLabel})."
        }
    }

    private fun summarizeCompletion(points: List<AcademicStatPoint>): String {
        val active = points.filter { it.detail != "Aucun devoir" }
        val average = active.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        return when {
            points.isEmpty() -> "Aucun module a afficher."
            average == null -> "Aucun devoir publie ou attribue."
            else -> "Completion moyenne: ${String.format(Locale.ROOT, "%.1f", average)}%."
        }
    }

    private fun List<AcademicStatPoint>.toChartEntries(colorRes: Int): List<SimpleChartEntry> {
        val color = ContextCompat.getColor(this@AcademicStatisticsActivity, colorRes)
        return map { point ->
            SimpleChartEntry(
                label = point.label,
                value = point.value,
                valueLabel = point.valueLabel,
                detail = point.detail,
                color = color
            )
        }
    }

    private fun configureBottomNavigation(isTeacher: Boolean) {
        PrimaryBottomNav.bind(
            root = binding.root,
            role = if (isTeacher) PrimaryBottomNav.Role.TEACHER else PrimaryBottomNav.Role.STUDENT,
            currentFeature = "dashboard",
            onDashboard = {
                startActivity(
                    Intent(
                        this,
                        if (isTeacher) TeacherHomeActivity::class.java else StudentHomeActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finishWithTransition()
            },
            onFeature = { feature ->
                startActivity(
                    Intent(
                        this,
                        if (isTeacher) TeacherFeatureListActivity::class.java else StudentFeatureListActivity::class.java
                    ).putExtra(
                        if (isTeacher) TeacherFeatureListActivity.EXTRA_FEATURE else StudentFeatureListActivity.EXTRA_FEATURE,
                        feature
                    )
                )
                finishWithTransition()
            },
            onProfile = {
                startActivity(
                    Intent(
                        this,
                        if (isTeacher) TeacherFeatureListActivity::class.java else StudentFeatureListActivity::class.java
                    ).putExtra(
                        if (isTeacher) TeacherFeatureListActivity.EXTRA_FEATURE else StudentFeatureListActivity.EXTRA_FEATURE,
                        "profile"
                    )
                )
                finishWithTransition()
            }
        )
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_ROLE = "extra_role"
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val ROLE_TEACHER = "teacher"
        const val ROLE_STUDENT = "student"
    }
}
