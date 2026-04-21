package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AdminClasseUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminDashboard
import com.pfe.gestionetudiantmobile.data.model.AdminFiliereUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminModuleUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminTimetableUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminUserUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.model.TopStudentItem
import com.pfe.gestionetudiantmobile.data.model.UserSummary
import com.pfe.gestionetudiantmobile.util.ApiResult

class AdminRepository {

    private val api get() = RetrofitClient.api

    suspend fun dashboard(): ApiResult<AdminDashboard> {
        return runCatching {
            val response = api.adminDashboard()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(RepositoryUtils.networkError(it))
        }
    }

    suspend fun topStudents(limit: Int = 5): ApiResult<List<TopStudentItem>> =
        call { api.adminTopStudents(limit) }

    suspend fun users(
        role: String? = null,
        query: String? = null,
        enabled: Boolean? = null
    ): ApiResult<List<UserSummary>> = call { api.adminUsers(role, query, enabled) }

    suspend fun createUser(request: AdminUserUpsertRequest): ApiResult<UserSummary> =
        call { api.createAdminUser(request) }

    suspend fun updateUser(userId: Long, request: AdminUserUpsertRequest): ApiResult<UserSummary> =
        call { api.updateAdminUser(userId, request) }

    suspend fun deleteUser(userId: Long): ApiResult<ApiMessage> =
        call { api.deleteAdminUser(userId) }

    suspend fun toggleUser(userId: Long): ApiResult<UserSummary> =
        call { api.toggleAdminUser(userId) }

    suspend fun filieres(query: String? = null): ApiResult<List<Map<String, Any?>>> =
        call { api.adminFilieres(query) }

    suspend fun createFiliere(request: AdminFiliereUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.createAdminFiliere(request) }

    suspend fun updateFiliere(filiereId: Long, request: AdminFiliereUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.updateAdminFiliere(filiereId, request) }

    suspend fun deleteFiliere(filiereId: Long): ApiResult<ApiMessage> =
        call { api.deleteAdminFiliere(filiereId) }

    suspend fun classes(
        filiereId: Long? = null,
        query: String? = null
    ): ApiResult<List<Map<String, Any?>>> =
        call { api.adminClasses(filiereId, query) }

    suspend fun createClasse(request: AdminClasseUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.createAdminClasse(request) }

    suspend fun updateClasse(classeId: Long, request: AdminClasseUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.updateAdminClasse(classeId, request) }

    suspend fun deleteClasse(classeId: Long): ApiResult<ApiMessage> =
        call { api.deleteAdminClasse(classeId) }

    suspend fun modules(
        filiereId: Long? = null,
        teacherId: Long? = null,
        query: String? = null
    ): ApiResult<List<Map<String, Any?>>> =
        call { api.adminModules(filiereId, teacherId, query) }

    suspend fun createModule(request: AdminModuleUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.createAdminModule(request) }

    suspend fun updateModule(moduleId: Long, request: AdminModuleUpsertRequest): ApiResult<Map<String, Any?>> =
        call { api.updateAdminModule(moduleId, request) }

    suspend fun deleteModule(moduleId: Long): ApiResult<ApiMessage> =
        call { api.deleteAdminModule(moduleId) }

    suspend fun timetable(
        filiereId: Long? = null,
        classeId: Long? = null,
        query: String? = null
    ): ApiResult<List<TimetableItem>> =
        call { api.adminTimetable(filiereId, classeId, query) }

    suspend fun createTimetable(request: AdminTimetableUpsertRequest): ApiResult<TimetableItem> =
        call { api.createAdminTimetable(request) }

    suspend fun updateTimetable(timetableId: Long, request: AdminTimetableUpsertRequest): ApiResult<TimetableItem> =
        call { api.updateAdminTimetable(timetableId, request) }

    suspend fun deleteTimetable(timetableId: Long): ApiResult<ApiMessage> =
        call { api.deleteAdminTimetable(timetableId) }

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
