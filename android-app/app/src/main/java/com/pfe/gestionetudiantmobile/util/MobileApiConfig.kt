package com.pfe.gestionetudiantmobile.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object MobileApiConfig {
    const val LOGIN_PATH = "api/mobile/auth/login"

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8081/"
    private const val SPRING_BOOT_PORT = 8081
    private const val LEGACY_WEB_PORT = 8080

    fun normalizeBaseUrl(rawUrl: String?, fallbackUrl: String = DEFAULT_BASE_URL): String {
        var candidate = rawUrl?.trim().orEmpty().ifBlank { fallbackUrl.trim() }
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "http://$candidate"
        }

        val parsed = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("URL serveur invalide: ${rawUrl.orEmpty()}")

        val host = when (parsed.host.lowercase()) {
            "localhost", "127.0.0.1" -> "10.0.2.2"
            else -> parsed.host
        }

        val port = if (parsed.port == LEGACY_WEB_PORT) SPRING_BOOT_PORT else parsed.port

        return parsed.newBuilder()
            .host(host)
            .port(port)
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    fun loginUrl(baseUrl: String): String {
        return normalizeBaseUrl(baseUrl).trimEnd('/') + "/$LOGIN_PATH"
    }
}
