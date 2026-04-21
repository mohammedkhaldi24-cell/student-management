package com.pfe.gestionetudiantmobile.util

import android.content.Context

class ServerConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String? {
        val saved = prefs.getString(KEY_BASE_URL, null)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            val normalized = normalize(saved)
            if (normalized != saved) {
                prefs.edit().putString(KEY_BASE_URL, normalized).apply()
            }
            normalized
        }.getOrElse {
            clear()
            null
        }
    }

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, normalize(url)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    private fun normalize(url: String): String {
        return MobileApiConfig.normalizeBaseUrl(url)
    }

    companion object {
        private const val PREF_NAME = "server_config_store"
        private const val KEY_BASE_URL = "base_url"
    }
}
