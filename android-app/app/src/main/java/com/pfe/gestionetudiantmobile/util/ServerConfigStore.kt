package com.pfe.gestionetudiantmobile.util

import android.content.Context

class ServerConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String? {
        return prefs.getString(KEY_BASE_URL, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, normalize(url)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    private fun normalize(url: String): String {
        var value = url.trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        if (!value.endsWith("/")) {
            value += "/"
        }
        return value
    }

    companion object {
        private const val PREF_NAME = "server_config_store"
        private const val KEY_BASE_URL = "base_url"
    }
}
