package com.pfe.gestionetudiantmobile.util

import com.pfe.gestionetudiantmobile.data.api.RetrofitClient

object AppUrlUtils {

    fun toAbsolute(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        val base = RetrofitClient.currentBaseUrl().removeSuffix("/")
        val suffix = if (url.startsWith("/")) url else "/$url"
        return "$base$suffix"
    }
}
