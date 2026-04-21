package com.pfe.gestionetudiantmobile.util

import android.content.Context
import com.pfe.gestionetudiantmobile.data.model.NotificationItem

class NotificationReadStore(
    context: Context,
    userId: Long?
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyPrefix = "user_${userId ?: 0}_"

    fun isRead(item: NotificationItem): Boolean {
        return prefs.getBoolean(storageKey(item), false)
    }

    fun unreadCount(items: List<NotificationItem>): Int {
        return items.count { !isRead(it) }
    }

    fun markRead(item: NotificationItem) {
        prefs.edit().putBoolean(storageKey(item), true).apply()
    }

    fun markAllRead(items: List<NotificationItem>) {
        val editor = prefs.edit()
        items.forEach { editor.putBoolean(storageKey(it), true) }
        editor.apply()
    }

    private fun storageKey(item: NotificationItem): String {
        return keyPrefix + item.stableKey()
    }

    private companion object {
        const val PREFS_NAME = "gestionetu_notification_reads"
    }
}
