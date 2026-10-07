package com.pashe.app.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pashe.app.Graph
import com.pashe.app.alarm.Notifications
import com.pashe.app.alarm.SyncWorker
import com.pashe.app.domain.AppMode
import kotlinx.coroutines.launch

/**
 * Child phones receive missed-dose alerts; parent phones receive silent "sync" pings when the
 * schedule changes. Alarms themselves never depend on push.
 */
class PasheMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Graph.appScope.launch {
            runCatching {
                when (Graph.store.appMode) {
                    AppMode.CHILD -> if (Graph.auth.isChildSignedIn) Graph.childRepo.registerFcmToken(token)
                    AppMode.PARENT -> Graph.parentRepo.updateDeviceToken(token)
                    null -> Unit
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "sync" -> if (Graph.store.appMode == AppMode.PARENT) SyncWorker.runNow(this)
            "missed" -> {
                // Background messages with a notification payload are shown by the system; this
                // path covers the app being in the foreground.
                val notification = message.notification ?: return
                Notifications.showMissed(
                    this, notification.title.orEmpty(), notification.body.orEmpty(), message.data["parentId"],
                )
            }
        }
    }
}
