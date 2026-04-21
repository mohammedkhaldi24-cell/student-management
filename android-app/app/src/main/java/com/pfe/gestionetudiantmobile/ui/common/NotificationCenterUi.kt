package com.pfe.gestionetudiantmobile.ui.common

import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import com.pfe.gestionetudiantmobile.util.NotificationReadStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object NotificationCenterUi {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH)

    fun rows(
        items: List<NotificationItem>,
        readStore: NotificationReadStore,
        rowIdFor: (Int) -> Long
    ): List<UiRow> {
        if (items.isEmpty()) return emptyList()

        val sorted = sort(items)
        val indexed = sorted.mapIndexed { index, item -> IndexedNotification(index, item) }
        val rows = mutableListOf<UiRow>()
        rows += digestRow(sorted, readStore)

        indexed.groupBy { dayKey(it.item.createdAt) }
            .forEach { (day, dayItems) ->
                val notifications = dayItems.map { it.item }
                rows += groupRow(day, notifications, readStore)
                rows += dayItems.map { indexedItem ->
                    val item = indexedItem.item
                    val isRead = readStore.isRead(item)
                    UiRow(
                        id = rowIdFor(indexedItem.index),
                        title = AcademicNotificationStatus.rowTitle(item),
                        subtitle = AcademicNotificationStatus.rowSubtitle(item, isRead),
                        badge = AcademicNotificationStatus.readBadge(isRead),
                        icon = AcademicNotificationStatus.badge(item.type)
                    )
                }
            }

        return rows
    }

    fun sortedItems(items: List<NotificationItem>): List<NotificationItem> = sort(items)

    fun summary(items: List<NotificationItem>, readStore: NotificationReadStore): String {
        if (items.isEmpty()) {
            return "Aucun evenement academique recent"
        }
        val unread = readStore.unreadCount(items)
        val digest = typeDigest(items)
        val email = items.count { it.emailRelated }
        val emailText = if (email > 0) " | $email lies a l'email" else ""
        return if (unread == 0) {
            "${items.size} evenement(s) | tout est lu$emailText | $digest"
        } else {
            "$unread non lu(s) sur ${items.size}$emailText | $digest"
        }
    }

    private fun digestRow(items: List<NotificationItem>, readStore: NotificationReadStore): UiRow {
        val unread = readStore.unreadCount(items)
        val email = items.count { it.emailRelated }
        val latest = items.firstOrNull()?.createdAt?.let { "Dernier: ${formatDateTime(it)}" } ?: "Aucune date"
        val unreadText = if (unread == 0) "Tout est lu" else "$unread non lu(s)"
        val emailText = if (email > 0) "$email evenement(s) peuvent aussi avoir un email backend" else "Canal app uniquement"
        return UiRow(
            title = "Digest notifications",
            subtitle = "$unreadText | ${items.size} evenement(s)\n${typeDigest(items)}\n$emailText | $latest",
            badge = "Digest",
            icon = "NOT"
        )
    }

    private fun groupRow(
        day: LocalDate?,
        items: List<NotificationItem>,
        readStore: NotificationReadStore
    ): UiRow {
        val unread = readStore.unreadCount(items)
        val title = when (day) {
            LocalDate.now() -> "Aujourd'hui"
            LocalDate.now().minusDays(1) -> "Hier"
            null -> "Sans date"
            else -> day.format(dateFormatter)
        }
        val status = if (unread == 0) "tout lu" else "$unread non lu(s)"
        return UiRow(
            title = title,
            subtitle = "${items.size} evenement(s) | $status",
            badge = "Groupe",
            icon = "NOT"
        )
    }

    private fun sort(items: List<NotificationItem>): List<NotificationItem> {
        return items.sortedWith(
            compareByDescending<NotificationItem> { it.createdAt ?: LocalDateTime.MIN }
                .thenBy { it.type }
                .thenBy { it.title }
        )
    }

    private fun dayKey(createdAt: LocalDateTime?): LocalDate? {
        return createdAt?.toLocalDate()
    }

    private fun typeDigest(items: List<NotificationItem>): String {
        val order = listOf("NOTE", "ABSENCE", "ASSIGNMENT", "ANNOUNCEMENT", "COURSE", "FEEDBACK")
        val counts = items.groupingBy { it.type.trim().uppercase(Locale.ROOT) }.eachCount()
        return order.mapNotNull { type ->
            val count = counts[type] ?: return@mapNotNull null
            "${AcademicNotificationStatus.label(type)} $count"
        }.joinToString(" | ").ifBlank { "${items.size} evenement(s)" }
    }

    private fun formatDateTime(value: LocalDateTime): String {
        return value.format(DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.FRENCH))
    }

    private data class IndexedNotification(
        val index: Int,
        val item: NotificationItem
    )
}
