package com.pfe.gestionetudiantmobile.data.repository

import com.google.gson.Gson
import com.pfe.gestionetudiantmobile.BuildConfig
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.AuthResponse
import com.pfe.gestionetudiantmobile.data.model.LoginRequest
import com.pfe.gestionetudiantmobile.util.ApiResult
import com.pfe.gestionetudiantmobile.util.MobileApiConfig
import retrofit2.Response

class AuthRepository {

    private val api get() = RetrofitClient.api
    private val gson = Gson()

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

                val parsed = parseLoginError(response)
                val code = response.code()

                if (code == 401 || code == 403) {
                    // Credentials/access issue: no need to test other ports.
                    return ApiResult.Error(parsed)
                }

                lastError = parsed
            } catch (ex: Exception) {
                val raw = RepositoryUtils.networkError(ex)
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
            ApiResult.Error(RepositoryUtils.networkError(it))
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
            ApiResult.Error(RepositoryUtils.networkError(it))
        }
    }

    private fun buildCandidateBaseUrls(current: String): List<String> {
        val ordered = linkedSetOf<String>()
        val lan = BuildConfig.LAN_BASE_URL.trim()
        val configured = BuildConfig.BASE_URL.trim()
        ordered += MobileApiConfig.normalizeBaseUrl(current, configured)
        if (configured.isNotBlank()) {
            ordered += MobileApiConfig.normalizeBaseUrl(configured, configured)
        }
        if (lan.isNotBlank()) {
            ordered += MobileApiConfig.normalizeBaseUrl(lan, configured)
        }
        return ordered.toList()
    }

    private fun parseLoginError(response: Response<AuthResponse>): String {
        val requestUrl = response.raw().request.url.toString()
        if (response.code() == 404) {
            return "API mobile introuvable (404): $requestUrl. Verifiez que Spring Boot est lance sur 8081."
        }
        if (response.code() == 405) {
            return "Methode non autorisee (405): $requestUrl. Le login mobile doit appeler POST /api/mobile/auth/login."
        }

        val body = response.errorBody()?.string()
        if (body.isNullOrBlank()) {
            return "Erreur login (${response.code()})"
        }

        parseAuthResponseMessage(body)?.let { return it }
        parseApiMessage(body)?.let { return it }
        return "Erreur login (${response.code()})"
    }

    private fun parseAuthResponseMessage(body: String): String? {
        return runCatching {
            gson.fromJson(body, AuthResponse::class.java)?.message?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseApiMessage(body: String): String? {
        return runCatching {
            gson.fromJson(body, ApiMessage::class.java)?.message?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
