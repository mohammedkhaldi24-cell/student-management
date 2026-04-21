package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AdminTimetableUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.ChefDashboard
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.util.ApiResult

class ChefRepository {

    private val api get() = RetrofitClient.api

    suspend fun dashboard(): ApiResult<ChefDashboard> {
        return runCatching {
            val response = api.chefDashboard()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(RepositoryUtils.networkError(it))
        }
    }

    suspend fun classes(): ApiResult<List<ClasseItem>> =
        call { api.chefClasses() }

    suspend fun modules(): ApiResult<List<TeacherModuleItem>> =
        call { api.chefModules() }

    suspend fun students(classeId: Long? = null): ApiResult<List<StudentProfile>> =
        call { api.chefStudents(classeId) }

    suspend fun notes(classeId: Long? = null): ApiResult<List<NoteItem>> =
        call { api.chefNotes(classeId) }

    suspend fun absences(classeId: Long? = null): ApiResult<List<AbsenceItem>> =
        call { api.chefAbsences(classeId) }

    suspend fun courses(): ApiResult<List<CourseItem>> =
        call { api.chefCourses() }

    suspend fun announcements(): ApiResult<List<AnnouncementItem>> =
        call { api.chefAnnouncements() }

    suspend fun timetable(): ApiResult<List<TimetableItem>> =
        call { api.chefTimetable() }

    suspend fun createTimetable(request: AdminTimetableUpsertRequest): ApiResult<TimetableItem> =
        call { api.createChefTimetable(request) }

    suspend fun updateTimetable(timetableId: Long, request: AdminTimetableUpsertRequest): ApiResult<TimetableItem> =
        call { api.updateChefTimetable(timetableId, request) }

    suspend fun deleteTimetable(timetableId: Long): ApiResult<ApiMessage> =
        call { api.deleteChefTimetable(timetableId) }

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
}
