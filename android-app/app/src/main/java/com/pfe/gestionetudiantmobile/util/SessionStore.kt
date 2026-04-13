package com.pfe.gestionetudiantmobile.util

import android.content.Context
import com.google.gson.Gson
import com.pfe.gestionetudiantmobile.data.model.UserSummary

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("gestionetu_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUser(user: UserSummary) {
        prefs.edit().putString(KEY_USER, gson.toJson(user)).apply()
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
    }
}
