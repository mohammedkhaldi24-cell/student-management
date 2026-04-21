package com.pfe.gestionetudiantmobile.data.offline

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.pfe.gestionetudiantmobile.data.api.LocalDateAdapter
import com.pfe.gestionetudiantmobile.data.api.LocalDateTimeAdapter
import com.pfe.gestionetudiantmobile.data.api.LocalTimeAdapter
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class OfflineStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .create()

    @Synchronized
    fun cacheNotes(scope: String, notes: List<NoteItem>) {
        prefs.edit().putString(notesKey(scope), gson.toJson(notes)).apply()
    }

    @Synchronized
    fun cachedNotes(scope: String): List<NoteItem>? {
        return prefs.getString(notesKey(scope), null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<NoteItem>>() {}.type
                gson.fromJson<List<NoteItem>>(json, type)
            }.getOrNull()
        }
    }

    @Synchronized
    fun upsertCachedNote(scope: String, note: NoteItem) {
        val updated = cachedNotes(scope).orEmpty()
            .filterNot { it.studentId == note.studentId && it.moduleId == note.moduleId && it.semestre == note.semestre }
            .plus(note)
        cacheNotes(scope, updated)
    }

    @Synchronized
    fun cacheAbsences(scope: String, absences: List<AbsenceItem>) {
        prefs.edit().putString(absencesKey(scope), gson.toJson(absences)).apply()
    }

    @Synchronized
    fun cachedAbsences(scope: String): List<AbsenceItem>? {
        return prefs.getString(absencesKey(scope), null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<AbsenceItem>>() {}.type
                gson.fromJson<List<AbsenceItem>>(json, type)
            }.getOrNull()
        }
    }

    @Synchronized
    fun upsertCachedAbsence(scope: String, absence: AbsenceItem) {
        val updated = cachedAbsences(scope).orEmpty()
            .filterNot {
                it.id == absence.id ||
                    (it.studentId == absence.studentId && it.moduleId == absence.moduleId && it.dateAbsence == absence.dateAbsence)
            }
            .plus(absence)
        cacheAbsences(scope, updated)
    }

    @Synchronized
    fun removeCachedAbsenceById(absenceId: Long) {
        allKeysWithPrefix(ABSENCES_PREFIX).forEach { key ->
            val scope = key.removePrefix(ABSENCES_PREFIX)
            val updated = cachedAbsences(scope).orEmpty().filterNot { it.id == absenceId }
            cacheAbsences(scope, updated)
        }
    }

    @Synchronized
    fun enqueue(action: PendingOfflineAction) {
        val actions = pendingActions().toMutableList()
        val replacementIndex = actions.indexOfFirst { it.replacementKey == action.replacementKey }
        if (replacementIndex >= 0 && action.replacementKey.isNotBlank()) {
            actions[replacementIndex] = action.copy(id = actions[replacementIndex].id)
        } else {
            actions += action
        }
        saveActions(actions)
    }

    @Synchronized
    fun pendingActions(): List<PendingOfflineAction> {
        return prefs.getString(ACTIONS_KEY, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<List<PendingOfflineAction>>() {}.type
                gson.fromJson<List<PendingOfflineAction>>(json, type)
            }.getOrNull()
        }.orEmpty()
    }

    @Synchronized
    fun removeAction(actionId: String) {
        saveActions(pendingActions().filterNot { it.id == actionId })
    }

    @Synchronized
    fun removeQueuedCreateAbsence(localEntityId: Long): Boolean {
        val before = pendingActions()
        val after = before.filterNot {
            it.type == PendingOfflineAction.TYPE_CREATE_ABSENCE && it.localEntityId == localEntityId
        }
        if (after.size == before.size) return false
        saveActions(after)
        removeCachedAbsenceById(localEntityId)
        return true
    }

    fun pendingCount(): Int = pendingActions().size

    private fun saveActions(actions: List<PendingOfflineAction>) {
        prefs.edit().putString(ACTIONS_KEY, gson.toJson(actions.sortedBy { it.createdAtMillis })).apply()
    }

    private fun allKeysWithPrefix(prefix: String): List<String> {
        return prefs.all.keys.filter { it.startsWith(prefix) }
    }

    private fun notesKey(scope: String): String = "$NOTES_PREFIX$scope"

    private fun absencesKey(scope: String): String = "$ABSENCES_PREFIX$scope"

    companion object {
        private const val PREFS = "offline_recent_academic_data"
        private const val NOTES_PREFIX = "notes."
        private const val ABSENCES_PREFIX = "absences."
        private const val ACTIONS_KEY = "pending_actions"

        fun scope(prefix: String, vararg parts: Pair<String, Any?>): String {
            return buildString {
                append(prefix)
                parts.forEach { (key, value) ->
                    append('|')
                    append(key)
                    append('=')
                    append(value?.toString()?.trim()?.lowercase(Locale.ROOT)?.ifBlank { "all" } ?: "all")
                }
            }
        }
    }
}

data class PendingOfflineAction(
    val id: String,
    val type: String,
    val createdAtMillis: Long,
    val replacementKey: String,
    val cacheScope: String? = null,
    val localEntityId: Long? = null,
    val studentId: Long? = null,
    val moduleId: Long? = null,
    val semestre: String? = null,
    val anneeAcademique: String? = null,
    val noteCc: Double? = null,
    val noteExamen: Double? = null,
    val dateAbsence: String? = null,
    val nombreHeures: Int? = null,
    val absenceId: Long? = null
) {
    companion object {
        const val TYPE_UPSERT_NOTE = "UPSERT_NOTE"
        const val TYPE_CREATE_ABSENCE = "CREATE_ABSENCE"
        const val TYPE_DELETE_ABSENCE = "DELETE_ABSENCE"
    }
}
