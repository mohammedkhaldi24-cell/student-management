package com.pfe.gestionetudiantmobile.ui.common

import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import java.time.format.DateTimeFormatter
import java.util.Locale

object AcademicNotificationStatus {
    private val timeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.FRENCH)

    fun label(type: String): String {
        return when (type.normalizedType()) {
            "NOTE" -> "Note"
            "ABSENCE" -> "Absence"
            "ASSIGNMENT" -> "Devoir"
            "COURSE" -> "Cours"
            "ANNOUNCEMENT" -> "Annonce"
            "FEEDBACK" -> "Retour"
            else -> "Notification"
        }
    }

    fun status(type: String): String {
        return when (type.normalizedType()) {
            "NOTE" -> "Note ajoutee"
            "ABSENCE" -> "Absence ajoutee"
            "ASSIGNMENT" -> "Devoir cree"
            "COURSE" -> "Cours publie"
            "ANNOUNCEMENT" -> "Annonce publiee"
            "FEEDBACK" -> "Evaluation disponible"
            else -> "Evenement publie"
        }
    }

    fun badge(type: String): String {
        return when (type.normalizedType()) {
            "NOTE" -> "NOTE"
            "ABSENCE" -> "ABS"
            "ASSIGNMENT" -> "DEVOIR"
            "COURSE" -> "COURS"
            "ANNOUNCEMENT" -> "INFO"
            "FEEDBACK" -> "RETOUR"
            else -> "NEW"
        }
    }

    fun readBadge(isRead: Boolean): String {
        return if (isRead) "Lu" else "Non lu"
    }

    fun rowTitle(item: NotificationItem): String {
        return "${label(item.type)} - ${item.title}"
    }

    fun rowSubtitle(item: NotificationItem, isRead: Boolean): String {
        return buildString {
            append(shortMessage(item.message))
            val created = item.createdAt
            if (created != null) {
                append("\nDate: ")
                append(created.format(timeFormatter))
            }
            append("\nType: ")
            append(status(item.type))
            append("\nEtat: ")
            append(if (isRead) "lu" else "non lu")
            append("\nCanal: ")
            append(deliveryStatus(item))
        }
    }

    fun deliveryStatus(item: NotificationItem): String {
        return if (item.emailRelated) {
            "application + email si active cote backend"
        } else {
            when (item.type.normalizedType()) {
                "NOTE" -> "note visible dans l'app"
                "ABSENCE" -> "absence visible dans l'app"
                "FEEDBACK" -> "retour visible dans l'app"
                else -> "evenement visible dans l'app"
            }
        }
    }

    fun dashboardValue(notifications: List<NotificationItem>): String {
        return notifications.size.toString()
    }

    fun dashboardContext(notifications: List<NotificationItem>): String {
        val latest = notifications.firstOrNull()
        return if (latest == null) {
            "Aucune notification recente"
        } else {
            "${status(latest.type)}: ${latest.title}"
        }
    }

    fun actionFeature(item: NotificationItem): String {
        val actionPath = item.actionPath.lowercase(Locale.ROOT)
        return when {
            item.type.normalizedType() == "NOTE" || actionPath.contains("notes") -> "notes"
            item.type.normalizedType() == "ABSENCE" || actionPath.contains("absences") -> "absences"
            item.type.normalizedType() == "ASSIGNMENT" || actionPath.contains("assignments") -> "assignments"
            item.type.normalizedType() == "COURSE" || actionPath.contains("courses") -> "courses"
            item.type.normalizedType() == "ANNOUNCEMENT" || actionPath.contains("announcements") -> "announcements"
            else -> "notifications"
        }
    }

    private fun shortMessage(message: String): String {
        val clean = message.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= 150) clean else clean.take(147).trimEnd() + "..."
    }

    private fun String.normalizedType(): String = trim().uppercase(Locale.ROOT)
}
