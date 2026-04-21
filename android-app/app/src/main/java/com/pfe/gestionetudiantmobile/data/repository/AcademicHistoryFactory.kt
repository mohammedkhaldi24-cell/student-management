package com.pfe.gestionetudiantmobile.data.repository

import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.offline.PendingOfflineAction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

object AcademicHistoryFactory {

    fun fromNotes(notes: List<NoteItem>, fallbackActorName: String? = null): List<AcademicHistoryEvent> {
        return notes.map { note ->
            val occurredAt = note.updatedAt ?: note.createdAt
            val student = note.studentName?.takeIf { it.isNotBlank() } ?: "Etudiant"
            val module = note.moduleNom?.takeIf { it.isNotBlank() } ?: note.moduleCode ?: "Module"
            AcademicHistoryEvent(
                key = "note:${note.id}:${occurredAt ?: note.noteFinal ?: note.statut}",
                type = TYPE_NOTE,
                title = "Note - $student",
                message = listOf(
                    "Module: $module",
                    "CC: ${note.noteCc?.compact() ?: "-"}",
                    "Examen: ${note.noteExamen?.compact() ?: "-"}",
                    "Final: ${note.noteFinal?.compact() ?: "-"} /20",
                    "Statut: ${note.statut}"
                ).joinToString(" | "),
                moduleId = note.moduleId,
                moduleName = module,
                studentId = note.studentId,
                studentName = note.studentName,
                actorName = actor(note.actorName, fallbackActorName),
                occurredAt = occurredAt,
                dateLabel = if (occurredAt == null) "Date serveur indisponible" else "Action ${occurredAt.toReadableDateTime()}",
                badge = if (note.statut.contains("attente", ignoreCase = true)) "En attente" else "Note",
                pending = note.statut.contains("attente", ignoreCase = true)
            )
        }
    }

    fun fromAbsences(absences: List<AbsenceItem>, fallbackActorName: String? = null): List<AcademicHistoryEvent> {
        return absences.map { absence ->
            val occurredAt = absence.createdAt ?: absence.dateAbsence.atStartOfDay()
            val student = absence.studentName?.takeIf { it.isNotBlank() } ?: "Etudiant"
            val module = absence.moduleNom?.takeIf { it.isNotBlank() } ?: absence.moduleCode ?: "Module"
            AcademicHistoryEvent(
                key = "absence:${absence.id}:${absence.createdAt ?: absence.dateAbsence}",
                type = TYPE_ABSENCE,
                title = "Absence - $student",
                message = listOf(
                    "Module: $module",
                    "Date absence: ${absence.dateAbsence}",
                    "Duree: ${absence.nombreHeures}h",
                    if (absence.justifiee) "Justifiee" else "Non justifiee",
                    absence.motif?.takeIf { it.isNotBlank() }?.let { "Motif: $it" }
                ).filterNotNull().joinToString(" | "),
                moduleId = absence.moduleId,
                moduleName = module,
                studentId = absence.studentId,
                studentName = absence.studentName,
                actorName = actor(absence.actorName, fallbackActorName),
                occurredAt = occurredAt,
                dateLabel = if (absence.createdAt != null) {
                    "Action ${occurredAt.toReadableDateTime()}"
                } else {
                    "Date absence ${absence.dateAbsence}"
                },
                badge = if (absence.motif?.contains("attente", ignoreCase = true) == true) {
                    "En attente"
                } else if (absence.justifiee) {
                    "Justifiee"
                } else {
                    "Absence"
                },
                pending = absence.motif?.contains("attente", ignoreCase = true) == true
            )
        }
    }

    fun fromPendingActions(
        actions: List<PendingOfflineAction>,
        moduleId: Long?,
        fallbackActorName: String? = null,
        cachedNoteResolver: (PendingOfflineAction) -> NoteItem? = { null },
        cachedAbsenceResolver: (PendingOfflineAction) -> AbsenceItem? = { null }
    ): List<AcademicHistoryEvent> {
        return actions
            .filter { action -> moduleId == null || action.moduleId == moduleId }
            .mapNotNull { action ->
                val occurredAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(action.createdAtMillis),
                    ZoneId.systemDefault()
                )
                when (action.type) {
                    PendingOfflineAction.TYPE_UPSERT_NOTE -> pendingNoteEvent(
                        action = action,
                        cachedNote = cachedNoteResolver(action),
                        occurredAt = occurredAt,
                        fallbackActorName = fallbackActorName
                    )
                    PendingOfflineAction.TYPE_CREATE_ABSENCE -> pendingAbsenceEvent(
                        action = action,
                        cachedAbsence = cachedAbsenceResolver(action),
                        occurredAt = occurredAt,
                        fallbackActorName = fallbackActorName
                    )
                    PendingOfflineAction.TYPE_DELETE_ABSENCE -> AcademicHistoryEvent(
                        key = "pending-absence-delete:${action.id}",
                        type = TYPE_ABSENCE,
                        title = "Suppression d'absence en attente",
                        message = "Suppression locale a synchroniser avec le serveur.",
                        moduleId = action.moduleId,
                        moduleName = null,
                        studentId = action.studentId,
                        studentName = null,
                        actorName = actor(null, fallbackActorName),
                        occurredAt = occurredAt,
                        dateLabel = "Action locale ${occurredAt.toReadableDateTime()}",
                        badge = "En attente",
                        pending = true
                    )
                    else -> null
                }
            }
    }

    fun sorted(events: List<AcademicHistoryEvent>): List<AcademicHistoryEvent> {
        return events.sortedWith(
            compareByDescending<AcademicHistoryEvent> { it.occurredAt ?: LocalDateTime.MIN }
                .thenByDescending { it.key }
        )
    }

    private fun pendingNoteEvent(
        action: PendingOfflineAction,
        cachedNote: NoteItem?,
        occurredAt: LocalDateTime,
        fallbackActorName: String?
    ): AcademicHistoryEvent {
        val student = cachedNote?.studentName?.takeIf { it.isNotBlank() }
            ?: action.studentId?.let { "Etudiant #$it" }
            ?: "Etudiant"
        val module = cachedNote?.moduleNom?.takeIf { it.isNotBlank() }
            ?: cachedNote?.moduleCode
            ?: action.moduleId?.let { "Module #$it" }
            ?: "Module"
        return AcademicHistoryEvent(
            key = "pending-note:${action.id}",
            type = TYPE_NOTE,
            title = "Note en attente - $student",
            message = listOf(
                "Module: $module",
                "CC: ${action.noteCc?.compact() ?: "-"}",
                "Examen: ${action.noteExamen?.compact() ?: "-"}",
                "Synchronisation automatique des que la connexion revient"
            ).joinToString(" | "),
            moduleId = action.moduleId,
            moduleName = module,
            studentId = action.studentId,
            studentName = cachedNote?.studentName,
            actorName = actor(cachedNote?.actorName, fallbackActorName),
            occurredAt = occurredAt,
            dateLabel = "Action locale ${occurredAt.toReadableDateTime()}",
            badge = "En attente",
            pending = true
        )
    }

    private fun pendingAbsenceEvent(
        action: PendingOfflineAction,
        cachedAbsence: AbsenceItem?,
        occurredAt: LocalDateTime,
        fallbackActorName: String?
    ): AcademicHistoryEvent {
        val absenceDate = action.dateAbsence?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: cachedAbsence?.dateAbsence
        val student = cachedAbsence?.studentName?.takeIf { it.isNotBlank() }
            ?: action.studentId?.let { "Etudiant #$it" }
            ?: "Etudiant"
        val module = cachedAbsence?.moduleNom?.takeIf { it.isNotBlank() }
            ?: cachedAbsence?.moduleCode
            ?: action.moduleId?.let { "Module #$it" }
            ?: "Module"
        return AcademicHistoryEvent(
            key = "pending-absence:${action.id}",
            type = TYPE_ABSENCE,
            title = "Absence en attente - $student",
            message = listOf(
                "Module: $module",
                absenceDate?.let { "Date absence: $it" },
                "Duree: ${action.nombreHeures ?: cachedAbsence?.nombreHeures ?: 1}h",
                "Synchronisation automatique des que la connexion revient"
            ).filterNotNull().joinToString(" | "),
            moduleId = action.moduleId,
            moduleName = module,
            studentId = action.studentId,
            studentName = cachedAbsence?.studentName,
            actorName = actor(cachedAbsence?.actorName, fallbackActorName),
            occurredAt = occurredAt,
            dateLabel = "Action locale ${occurredAt.toReadableDateTime()}",
            badge = "En attente",
            pending = true
        )
    }

    private fun actor(primary: String?, fallback: String?): String {
        return primary?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: "Equipe pedagogique"
    }

    private fun Double.compact(): String {
        return String.format(Locale.ROOT, "%.2f", this)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun LocalDateTime.toReadableDateTime(): String {
        return "${toLocalDate()} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    const val TYPE_NOTE = "NOTE"
    const val TYPE_ABSENCE = "ABSENCE"
}
