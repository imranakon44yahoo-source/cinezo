package com.pashe.app

import android.app.Application
import com.pashe.app.alarm.Notifications
import com.pashe.app.alarm.SyncWorker
import com.pashe.app.domain.AppMode

class PasheApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannels(this)
        if (Graph.store.appMode == AppMode.PARENT && Graph.store.parentId != null) {
            SyncWorker.schedulePeriodic(this)
        }
    }
}
