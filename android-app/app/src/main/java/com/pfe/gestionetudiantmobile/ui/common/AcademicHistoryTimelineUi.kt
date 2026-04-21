package com.pfe.gestionetudiantmobile.ui.common

import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AcademicHistoryTimelineUi {

    fun rows(
        events: List<AcademicHistoryEvent>,
        idForIndex: (Int) -> Long = { it.toLong() }
    ): List<UiRow> {
        return events.mapIndexed { index, event ->
            UiRow(
                id = idForIndex(index),
                title = event.title,
                subtitle = listOf(
                    event.dateLabel,
                    "Par: ${event.actorName}",
                    event.message
                ).joinToString("\n"),
                badge = event.badge,
                icon = if (event.type == "NOTE") "20" else "ABS"
            )
        }
    }

    fun summary(events: List<AcademicHistoryEvent>): String {
        val notes = events.count { it.type == "NOTE" }
        val absences = events.count { it.type == "ABSENCE" }
        val pending = events.count { it.pending }
        val latest = events.firstOrNull()?.occurredAt?.let { "Derniere action: ${formatDateTime(it)}" }
            ?: "Derniere action: date indisponible"
        return buildString {
            append("$notes note(s) | $absences absence(s)")
            if (pending > 0) append(" | $pending en attente")
            append(" | ")
            append(latest)
        }
    }

    fun details(event: AcademicHistoryEvent): String {
        return listOf(
            "Type: ${if (event.type == "NOTE") "Note" else "Absence"}",
            "Module: ${event.moduleName ?: "-"}",
            "Etudiant: ${event.studentName ?: "-"}",
            "Par: ${event.actorName}",
            "Quand: ${event.occurredAt?.let { formatDateTime(it) } ?: event.dateLabel}",
            "Statut: ${event.badge}",
            "",
            event.message
        ).joinToString("\n")
    }

    private fun formatDateTime(value: LocalDateTime): String {
        return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}
