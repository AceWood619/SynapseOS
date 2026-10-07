package com.acewood.synapse.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NODE, "Synapse node", NotificationManager.IMPORTANCE_MIN)
        )
        Kiosk.applyPolicies(this)
    }

    companion object {
        const val CHANNEL_NODE = "synapse_node"
        const val TAG = "Synapse"
    }
}
