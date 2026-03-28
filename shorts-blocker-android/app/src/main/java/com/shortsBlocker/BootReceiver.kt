package com.shortsBlocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Start the blocker foreground service to keep monitoring alive
            val serviceIntent = Intent(context, BlockerForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
