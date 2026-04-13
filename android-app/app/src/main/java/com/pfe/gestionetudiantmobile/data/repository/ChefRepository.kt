package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ChefDashboard
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.util.ApiResult

class ChefRepository {

    private val api = RetrofitClient.api

    suspend fun dashboard(): ApiResult<ChefDashboard> {
        return runCatching {
            val response = api.chefDashboard()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(it.message ?: "Erreur reseau")
        }
    }

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
