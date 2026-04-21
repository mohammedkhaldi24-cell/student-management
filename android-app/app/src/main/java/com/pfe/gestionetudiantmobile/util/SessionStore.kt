package com.pfe.gestionetudiantmobile.util

import android.content.Context
import com.google.gson.Gson
import com.pfe.gestionetudiantmobile.data.model.UserSummary

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("gestionetu_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUser(user: UserSummary) {
        saveAuthenticatedSession(user, null)
    }

    fun saveAuthenticatedSession(user: UserSummary, baseUrl: String?) {
        prefs.edit()
            .putString(KEY_USER, gson.toJson(user))
            .putLong(KEY_AUTHENTICATED_AT, System.currentTimeMillis())
            .apply {
                if (!baseUrl.isNullOrBlank()) {
                    putString(KEY_BASE_URL, baseUrl)
                }
            }
            .apply()
    }

    fun getUser(): UserSummary? {
        val raw = prefs.getString(KEY_USER, null) ?: return null
        return runCatching { gson.fromJson(raw, UserSummary::class.java) }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USER = "key_user"
        private const val KEY_AUTHENTICATED_AT = "key_authenticated_at"
        private const val KEY_BASE_URL = "key_base_url"
    }
}
