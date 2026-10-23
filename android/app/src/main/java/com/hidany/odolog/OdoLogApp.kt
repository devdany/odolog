package com.hidany.odolog

import android.app.Application
import com.hidany.odolog.record.Notifier

class OdoLogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannels(this)
    }
}
