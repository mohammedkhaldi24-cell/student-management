package com.pfe.gestionetudiantmobile.data.api

import com.google.gson.GsonBuilder
import com.pfe.gestionetudiantmobile.BuildConfig
import com.pfe.gestionetudiantmobile.util.MobileApiConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val cookieJar = SessionCookieJar()

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .create()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentBaseUrl: String = normalizeBaseUrl(BuildConfig.BASE_URL)

    @Volatile
    private var retrofit: Retrofit = createRetrofit(currentBaseUrl)

    @Volatile
    var api: ApiService = retrofit.create(ApiService::class.java)
        private set

    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun currentBaseUrl(): String = currentBaseUrl

    fun apiFor(baseUrl: String): ApiService {
        return createRetrofit(normalizeBaseUrl(baseUrl)).create(ApiService::class.java)
    }

    fun switchBaseUrl(baseUrl: String) {
        val normalized = normalizeBaseUrl(baseUrl)
        if (normalized == currentBaseUrl) {
            return
        }
        currentBaseUrl = normalized
        clearSession()
        retrofit = createRetrofit(currentBaseUrl)
        api = retrofit.create(ApiService::class.java)
    }

    fun clearSession() {
        cookieJar.clear()
    }

    private fun normalizeBaseUrl(url: String): String {
        return MobileApiConfig.normalizeBaseUrl(url, BuildConfig.BASE_URL)
    }
}
