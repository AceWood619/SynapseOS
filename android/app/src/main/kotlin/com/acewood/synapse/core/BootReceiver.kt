package com.acewood.synapse.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the node service after boot or after an app update. The screen comes up as the home app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, NodeService::class.java))
    }
}
