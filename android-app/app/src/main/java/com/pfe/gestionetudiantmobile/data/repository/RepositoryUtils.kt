package com.pfe.gestionetudiantmobile.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import retrofit2.Response

object RepositoryUtils {

    fun <T> parseError(response: Response<T>): String {
        if (response.code() == 404) {
            return "API introuvable (404). Verifiez le port backend (8081/8080)."
        }

        val body = response.errorBody()?.string()
        if (body.isNullOrBlank()) {
            return "Erreur reseau (${response.code()})"
        }

        return runCatching {
            val type = object : TypeToken<ApiMessage>() {}.type
            Gson().fromJson<ApiMessage>(body, type).message
        }.getOrElse {
            "Erreur reseau (${response.code()})"
        }
    }
}
