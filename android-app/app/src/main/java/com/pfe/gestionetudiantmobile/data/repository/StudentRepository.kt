package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentDashboard
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.util.ApiResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class StudentRepository {

    private val api = RetrofitClient.api

    suspend fun profile(): ApiResult<StudentProfile> = call { api.studentProfile() }

    suspend fun dashboard(): ApiResult<StudentDashboard> = call { api.studentDashboard() }

    suspend fun notes(): ApiResult<List<NoteItem>> = call { api.studentNotes() }

    suspend fun absences(): ApiResult<List<AbsenceItem>> = call { api.studentAbsences() }

    suspend fun timetable(): ApiResult<List<TimetableItem>> = call { api.studentTimetable() }

    suspend fun courses(): ApiResult<List<CourseItem>> = call { api.studentCourses() }

    suspend fun announcements(): ApiResult<List<AnnouncementItem>> = call { api.studentAnnouncements() }

    suspend fun assignments(filter: String = "all"): ApiResult<List<AssignmentItem>> = call { api.studentAssignments(filter) }

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
            ApiResult.Error(it.message ?: "Erreur reseau")
        }
    }
}
