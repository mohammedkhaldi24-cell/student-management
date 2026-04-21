package com.pfe.gestionetudiantmobile.data.repository

import android.content.Context
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import com.pfe.gestionetudiantmobile.data.model.AcademicStatPoint
import com.pfe.gestionetudiantmobile.data.model.AcademicStatistics
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import com.pfe.gestionetudiantmobile.data.model.StudentDashboard
import com.pfe.gestionetudiantmobile.data.model.StudentModuleItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.offline.OfflineStore
import com.pfe.gestionetudiantmobile.data.offline.OfflineSyncManager
import com.pfe.gestionetudiantmobile.util.ApiResult
import java.text.DecimalFormat
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class StudentRepository(context: Context? = null) {

    private val api get() = RetrofitClient.api
    private val appContext = context?.applicationContext

    init {
        appContext?.let { OfflineSyncManager.register(it) }
    }

    suspend fun profile(): ApiResult<StudentProfile> = call { api.studentProfile() }

    suspend fun dashboard(): ApiResult<StudentDashboard> = call { api.studentDashboard() }

    suspend fun modules(): ApiResult<List<StudentModuleItem>> = call { api.studentModules() }

    suspend fun notes(moduleId: Long? = null): ApiResult<List<NoteItem>> {
        val scope = OfflineStore.scope("student.notes", "moduleId" to moduleId)
        val result = call { api.studentNotes(moduleId) }
        return when (result) {
            is ApiResult.Success -> {
                appContext?.let { OfflineStore(it).cacheNotes(scope, result.data) }
                result
            }
            is ApiResult.Error -> {
                val cached = appContext?.let { OfflineStore(it).cachedNotes(scope) }
                if (cached != null) ApiResult.Success(cached) else result
            }
        }
    }

    suspend fun absences(moduleId: Long? = null): ApiResult<List<AbsenceItem>> {
        val scope = OfflineStore.scope("student.absences", "moduleId" to moduleId)
        val result = call { api.studentAbsences(moduleId) }
        return when (result) {
            is ApiResult.Success -> {
                appContext?.let { OfflineStore(it).cacheAbsences(scope, result.data) }
                result
            }
            is ApiResult.Error -> {
                val cached = appContext?.let { OfflineStore(it).cachedAbsences(scope) }
                if (cached != null) ApiResult.Success(cached) else result
            }
        }
    }

    suspend fun history(moduleId: Long? = null): ApiResult<List<AcademicHistoryEvent>> {
        val noteResult = notes(moduleId)
        val absenceResult = absences(moduleId)
        val events = mutableListOf<AcademicHistoryEvent>()
        val errors = mutableListOf<String>()

        when (noteResult) {
            is ApiResult.Success -> events += AcademicHistoryFactory.fromNotes(noteResult.data)
            is ApiResult.Error -> errors += noteResult.message
        }
        when (absenceResult) {
            is ApiResult.Success -> events += AcademicHistoryFactory.fromAbsences(absenceResult.data)
            is ApiResult.Error -> errors += absenceResult.message
        }

        return if (events.isNotEmpty() || errors.isEmpty()) {
            ApiResult.Success(AcademicHistoryFactory.sorted(events))
        } else {
            ApiResult.Error(errors.first())
        }
    }

    suspend fun statistics(preferredModuleId: Long? = null): ApiResult<AcademicStatistics> {
        val modulesResult = modules()
        if (modulesResult is ApiResult.Error) {
            return ApiResult.Error(modulesResult.message)
        }
        val modules = (modulesResult as ApiResult.Success).data.sortedBy { it.nom.lowercase(Locale.ROOT) }
        val noteResult = notes()
        val absenceResult = absences()
        val assignmentResult = assignments("all")
        val notes = (noteResult as? ApiResult.Success)?.data.orEmpty()
        val absences = (absenceResult as? ApiResult.Success)?.data.orEmpty()
        val assignments = (assignmentResult as? ApiResult.Success)?.data.orEmpty()

        val warnings = listOfNotNull(
            (noteResult as? ApiResult.Error)?.message?.let { "notes indisponibles" },
            (absenceResult as? ApiResult.Error)?.message?.let { "absences indisponibles" },
            (assignmentResult as? ApiResult.Error)?.message?.let { "devoirs indisponibles" }
        )
        return ApiResult.Success(
            AcademicStatistics(
                averageByModule = studentAveragePoints(modules, notes, preferredModuleId),
                absenceRateByModule = studentAbsenceRatePoints(modules, absences, preferredModuleId),
                assignmentCompletionByModule = studentAssignmentCompletionPoints(modules, assignments, preferredModuleId),
                highlightedModuleId = preferredModuleId,
                summary = if (warnings.isEmpty()) {
                    "Statistiques calculees depuis vos notes, absences et devoirs."
                } else {
                    "Vue partielle: ${warnings.distinct().joinToString(", ")}."
                }
            )
        )
    }

    suspend fun timetable(): ApiResult<List<TimetableItem>> = call { api.studentTimetable() }

    suspend fun courses(moduleId: Long? = null): ApiResult<List<CourseItem>> {
        return when (val result = call { api.studentCourses(moduleId) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.filter { course -> moduleId == null || course.moduleId == moduleId }
            )
            is ApiResult.Error -> result
        }
    }

    suspend fun announcements(): ApiResult<List<AnnouncementItem>> = call { api.studentAnnouncements() }

    suspend fun notifications(): ApiResult<List<NotificationItem>> = call { api.studentNotifications() }

    suspend fun assignments(filter: String = "all", moduleId: Long? = null): ApiResult<List<AssignmentItem>> {
        return when (val result = call { api.studentAssignments(filter, moduleId) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.filter { assignment -> moduleId == null || assignment.moduleId == moduleId }
            )
            is ApiResult.Error -> result
        }
    }

    suspend fun assignmentSubmission(assignmentId: Long): ApiResult<AssignmentSubmissionItem> =
        call { api.studentAssignmentSubmission(assignmentId) }

    suspend fun deleteSubmissionFile(assignmentId: Long, fileId: Long): ApiResult<AssignmentSubmissionItem> =
        call { api.deleteStudentSubmissionFile(assignmentId, fileId) }

    suspend fun submitAssignmentText(assignmentId: Long, submissionText: String): ApiResult<AssignmentSubmissionItem> {
        val textBody = submissionText.trim().toRequestBody("text/plain".toMediaType())
        return call { api.submitStudentAssignment(assignmentId, textBody, null) }
    }

    suspend fun submitAssignment(
        assignmentId: Long,
        submissionText: String? = null,
        fileParts: List<MultipartBody.Part>? = null
    ): ApiResult<AssignmentSubmissionItem> {
        val textBody = submissionText
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.toRequestBody("text/plain".toMediaType())

        val normalizedParts = fileParts?.filter { it.body.contentLength() != 0L }?.takeIf { it.isNotEmpty() }
        return call { api.submitStudentAssignment(assignmentId, textBody, normalizedParts) }
    }

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): ApiResult<T> {
        return runCatching {
            val response = block()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(RepositoryUtils.networkError(it))
        }
    }

    private fun studentAveragePoints(
        modules: List<StudentModuleItem>,
        notes: List<NoteItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val scoresByModule = notes
            .mapNotNull { note -> note.moduleId?.let { id -> note.noteFinal?.let { score -> id to score } } }
            .groupBy({ it.first }, { it.second })
        return modules.map { module ->
            val scores = scoresByModule[module.id].orEmpty()
            val average = scores.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = average,
                valueLabel = if (scores.isEmpty()) "--" else "${formatStatDecimal(average)}/20",
                detail = if (scores.isEmpty()) "Aucune note publiee" else "${scores.size} note(s)"
            )
        }.prioritize(preferredModuleId)
    }

    private fun studentAbsenceRatePoints(
        modules: List<StudentModuleItem>,
        absences: List<AbsenceItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val absencesByModule = absences.filter { it.moduleId != null }.groupBy { it.moduleId!! }
        return modules.map { module ->
            val moduleAbsences = absencesByModule[module.id].orEmpty()
            val absenceHours = moduleAbsences.sumOf { it.nombreHeures }
            val moduleHours = (module.volumeHoraire ?: DEFAULT_MODULE_HOURS).coerceAtLeast(1)
            val rate = absenceHours.toDouble() * 100.0 / moduleHours.toDouble()
            AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = rate.coerceIn(0.0, 100.0),
                valueLabel = "${formatStatDecimal(rate.coerceIn(0.0, 100.0))}%",
                detail = "$absenceHours h d'absence / $moduleHours h module"
            )
        }.prioritize(preferredModuleId)
    }

    private fun studentAssignmentCompletionPoints(
        modules: List<StudentModuleItem>,
        assignments: List<AssignmentItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val assignmentsByModule = assignments.filter { it.moduleId != null }.groupBy { it.moduleId!! }
        return modules.map { module ->
            val moduleAssignments = assignmentsByModule[module.id].orEmpty()
            val completed = moduleAssignments.count { it.isCompletedByStudent() }
            val total = moduleAssignments.size
            val rate = if (total > 0) completed.toDouble() * 100.0 / total.toDouble() else 0.0
            AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = rate,
                valueLabel = "${formatStatDecimal(rate)}%",
                detail = if (total == 0) "Aucun devoir" else "$completed/$total devoir(s)"
            )
        }.prioritize(preferredModuleId)
    }

    private fun AssignmentItem.isCompletedByStudent(): Boolean {
        val status = submissionStatus.trim().uppercase(Locale.ROOT)
        return submittedAt != null ||
            lateSubmission ||
            score != null ||
            status in setOf("SUBMITTED", "TURNED_IN", "SENT", "REVIEWED", "GRADED", "LATE")
    }

    private fun List<AcademicStatPoint>.prioritize(moduleId: Long?): List<AcademicStatPoint> {
        return sortedWith(compareBy<AcademicStatPoint> { if (it.moduleId == moduleId) 0 else 1 }
            .thenBy { it.label.lowercase(Locale.ROOT) })
    }

    private fun moduleLabel(name: String?, code: String?): String {
        return listOfNotNull(name?.takeIf { it.isNotBlank() }, code?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "Module" }
    }

    private fun formatStatDecimal(value: Double): String {
        return DecimalFormat("0.#").format(value)
    }

    private companion object {
        private const val DEFAULT_MODULE_HOURS = 30
    }
}
