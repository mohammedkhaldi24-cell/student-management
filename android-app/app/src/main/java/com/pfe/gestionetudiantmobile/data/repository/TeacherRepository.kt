package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.SubmissionReviewRequest
import com.pfe.gestionetudiantmobile.data.model.TeacherDashboard
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TeacherProfile
import com.pfe.gestionetudiantmobile.util.ApiResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class TeacherRepository {

    private val api = RetrofitClient.api

    suspend fun profile(): ApiResult<TeacherProfile> = call { api.teacherProfile() }

    suspend fun dashboard(): ApiResult<TeacherDashboard> = call { api.teacherDashboard() }

    suspend fun modules(): ApiResult<List<TeacherModuleItem>> = call { api.teacherModules() }

    suspend fun classes(moduleId: Long? = null, filiereId: Long? = null): ApiResult<List<ClasseItem>> =
        call { api.teacherClasses(moduleId, filiereId) }

    suspend fun students(
        moduleId: Long? = null,
        classeId: Long? = null,
        filiereId: Long? = null,
        query: String? = null
    ): ApiResult<List<StudentProfile>> = call { api.teacherStudents(moduleId, classeId, filiereId, query) }

    suspend fun notes(
        moduleId: Long? = null,
        classeId: Long? = null,
        query: String? = null
    ): ApiResult<List<NoteItem>> = call { api.teacherNotes(moduleId, classeId, query) }

    suspend fun absences(
        moduleId: Long? = null,
        classeId: Long? = null,
        query: String? = null
    ): ApiResult<List<AbsenceItem>> = call { api.teacherAbsences(moduleId, classeId, query) }

    suspend fun courses(moduleId: Long? = null): ApiResult<List<CourseItem>> =
        call { api.teacherCourses(moduleId) }

    suspend fun announcements(): ApiResult<List<AnnouncementItem>> = call { api.teacherAnnouncements() }

    suspend fun assignments(moduleId: Long? = null): ApiResult<List<AssignmentItem>> =
        call { api.teacherAssignments(moduleId) }

    suspend fun submissions(assignmentId: Long): ApiResult<List<AssignmentSubmissionItem>> =
        call { api.teacherAssignmentSubmissions(assignmentId) }

    suspend fun createCourse(
        title: String,
        description: String?,
        moduleId: Long,
        classeId: Long?,
        filiereId: Long?,
        filePart: MultipartBody.Part?
    ): ApiResult<CourseItem> = call {
        api.createTeacherCourse(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            description = description?.takeIf { it.isNotBlank() }?.trim()?.toRequestBody(TEXT_MEDIA),
            moduleId = moduleId.toString().toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            file = filePart
        )
    }

    suspend fun replaceCourseFile(
        courseId: Long,
        filePart: MultipartBody.Part
    ): ApiResult<CourseItem> = call {
        api.replaceTeacherCourseFile(courseId, filePart)
    }

    suspend fun removeCourseFile(courseId: Long): ApiResult<CourseItem> = call {
        api.removeTeacherCourseFile(courseId)
    }

    suspend fun deleteCourse(courseId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherCourse(courseId)
    }

    suspend fun createAnnouncement(
        title: String,
        message: String,
        classeId: Long?,
        filiereId: Long?,
        attachment: MultipartBody.Part?
    ): ApiResult<AnnouncementItem> = call {
        api.createTeacherAnnouncement(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            message = message.trim().toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            attachment = attachment
        )
    }

    suspend fun replaceAnnouncementAttachment(
        announcementId: Long,
        attachment: MultipartBody.Part
    ): ApiResult<AnnouncementItem> = call {
        api.replaceTeacherAnnouncementAttachment(announcementId, attachment)
    }

    suspend fun removeAnnouncementAttachment(announcementId: Long): ApiResult<AnnouncementItem> = call {
        api.removeTeacherAnnouncementAttachment(announcementId)
    }

    suspend fun deleteAnnouncement(announcementId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherAnnouncement(announcementId)
    }

    suspend fun createAssignment(
        title: String,
        description: String,
        dueDate: String,
        moduleId: Long?,
        classeId: Long?,
        filiereId: Long?,
        published: Boolean,
        attachment: MultipartBody.Part?
    ): ApiResult<AssignmentItem> = call {
        api.createTeacherAssignment(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            description = description.trim().toRequestBody(TEXT_MEDIA),
            dueDate = dueDate.trim().toRequestBody(TEXT_MEDIA),
            moduleId = moduleId?.toString()?.toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            published = published.toString().toRequestBody(TEXT_MEDIA),
            attachment = attachment
        )
    }

    suspend fun replaceAssignmentAttachment(
        assignmentId: Long,
        attachment: MultipartBody.Part
    ): ApiResult<AssignmentItem> = call {
        api.replaceTeacherAssignmentAttachment(assignmentId, attachment)
    }

    suspend fun removeAssignmentAttachment(assignmentId: Long): ApiResult<AssignmentItem> = call {
        api.removeTeacherAssignmentAttachment(assignmentId)
    }

    suspend fun deleteAssignment(assignmentId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherAssignment(assignmentId)
    }

    suspend fun reviewSubmission(
        assignmentId: Long,
        submissionId: Long,
        score: Double?,
        feedback: String?,
        status: String?
    ): ApiResult<AssignmentSubmissionItem> = call {
        api.reviewSubmission(
            assignmentId,
            submissionId,
            SubmissionReviewRequest(score = score, feedback = feedback, status = status)
        )
    }

    suspend fun upsertNote(
        studentId: Long,
        moduleId: Long,
        semestre: String,
        anneeAcademique: String,
        noteCc: Double?,
        noteExamen: Double?
    ): ApiResult<NoteItem> = call {
        api.upsertTeacherNote(
            com.pfe.gestionetudiantmobile.data.model.NoteUpsertRequest(
                studentId = studentId,
                moduleId = moduleId,
                semestre = semestre,
                anneeAcademique = anneeAcademique,
                noteCc = noteCc,
                noteExamen = noteExamen
            )
        )
    }

    suspend fun deleteNote(noteId: Long): ApiResult<ApiMessage> = call { api.deleteTeacherNote(noteId) }

    suspend fun createAbsence(
        studentId: Long,
        moduleId: Long,
        dateAbsence: String?,
        nombreHeures: Int?
    ): ApiResult<AbsenceItem> = call {
        api.createTeacherAbsence(
            com.pfe.gestionetudiantmobile.data.model.AbsenceCreateRequest(
                studentId = studentId,
                moduleId = moduleId,
                dateAbsence = dateAbsence,
                nombreHeures = nombreHeures
            )
        )
    }

    suspend fun justifyAbsence(absenceId: Long, motif: String?): ApiResult<AbsenceItem> =
        call { api.justifyTeacherAbsence(absenceId, motif) }

    suspend fun deleteAbsence(absenceId: Long): ApiResult<ApiMessage> =
        call { api.deleteTeacherAbsence(absenceId) }

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

    companion object {
        private val TEXT_MEDIA = "text/plain".toMediaType()
    }
}
