package com.example.slatelauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Exposes active notifications to the launcher's notification row. */
class HubService : NotificationListenerService() {
    companion object {
        @Volatile var instance: HubService? = null
        @Volatile var onChange: (() -> Unit)? = null

        fun current(): List<StatusBarNotification> = try {
            instance?.activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    override fun onListenerConnected() { instance = this; onChange?.invoke() }
    override fun onListenerDisconnected() { instance = null; onChange?.invoke() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { onChange?.invoke() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { onChange?.invoke() }
}
