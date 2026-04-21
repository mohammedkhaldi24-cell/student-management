package com.pfe.gestionetudiantmobile.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.Response

object RepositoryUtils {

    fun <T> parseError(response: Response<T>): String {
        val requestUrl = response.raw().request.url.toString()
        if (response.code() == 404) {
            return "API mobile introuvable (404): $requestUrl. Verifiez que Spring Boot est lance sur 8081 et que l'app appelle /api/mobile/auth/login."
        }

        if (response.code() == 405) {
            return "Methode non autorisee (405): $requestUrl. Le login mobile doit appeler POST /api/mobile/auth/login, pas la page web /login."
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

    fun networkError(throwable: Throwable): String {
        return when (throwable) {
            is UnknownHostException -> "Serveur introuvable. Verifiez l'URL backend et votre connexion."
            is SocketTimeoutException -> "Le serveur ne repond pas. Reessayez dans quelques instants."
            is ConnectException -> "Connexion impossible. Verifiez que le backend est demarre et que le port est correct."
            is IllegalArgumentException -> throwable.message ?: "Configuration serveur invalide."
            else -> throwable.message ?: "Erreur reseau"
        }
    }
}
