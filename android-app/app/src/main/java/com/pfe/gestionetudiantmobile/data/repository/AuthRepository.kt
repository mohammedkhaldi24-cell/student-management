package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.BuildConfig
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AuthResponse
import com.pfe.gestionetudiantmobile.data.model.LoginRequest
import com.pfe.gestionetudiantmobile.util.ApiResult

class AuthRepository {

    private val api get() = RetrofitClient.api

    suspend fun login(username: String, password: String): ApiResult<AuthResponse> {
        val normalizedUsername = username.trim()
        val candidateUrls = buildCandidateBaseUrls(RetrofitClient.currentBaseUrl())
        var lastError = "Erreur de connexion"

        for (candidate in candidateUrls) {
            try {
                val attemptApi = if (candidate == RetrofitClient.currentBaseUrl()) {
                    api
                } else {
                    RetrofitClient.apiFor(candidate)
                }

                val response = attemptApi.login(LoginRequest(normalizedUsername, password))
                if (response.isSuccessful && response.body() != null) {
                    RetrofitClient.switchBaseUrl(candidate)
                    return ApiResult.Success(response.body()!!)
                }

                val parsed = RepositoryUtils.parseError(response)
                val code = response.code()

                if (code == 401 || code == 403) {
                    // Credentials/access issue: no need to test other ports.
                    return ApiResult.Error(parsed)
                }

                lastError = parsed
            } catch (ex: Exception) {
                val raw = ex.message ?: "Erreur de connexion"
                lastError = if (raw.contains("10.0.2.2")) {
                    "Connexion impossible. Sur telephone reel, utilisez l'IP locale du PC (ex: 192.168.x.x:8081)."
                } else {
                    raw
                }
            }
        }

        return ApiResult.Error(lastError)
    }

    suspend fun me(): ApiResult<AuthResponse> {
        return runCatching {
            val response = api.me()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(it.message ?: "Erreur de session")
        }
    }

    suspend fun logout(): ApiResult<String> {
        return runCatching {
            val response = api.logout()
            RetrofitClient.clearSession()
            if (response.isSuccessful) {
                ApiResult.Success(response.body()?.message ?: "Deconnexion effectuee")
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(it.message ?: "Erreur de deconnexion")
        }
    }

    private fun buildCandidateBaseUrls(current: String): List<String> {
        val ordered = linkedSetOf<String>()
        val lan = BuildConfig.LAN_BASE_URL.trim()
        val configured = BuildConfig.BASE_URL.trim()
        ordered += current
        if (configured.isNotBlank()) {
            ordered += configured
            ordered += configured.replace(":8080/", ":8081/")
            ordered += configured.replace(":8081/", ":8080/")
        }
        if (lan.isNotBlank()) {
            ordered += lan
            ordered += lan.replace(":8080/", ":8081/")
            ordered += lan.replace(":8081/", ":8080/")
        }
        ordered += current.replace(":8080/", ":8081/")
        ordered += current.replace(":8081/", ":8080/")
        return ordered.toList()
    }
}
