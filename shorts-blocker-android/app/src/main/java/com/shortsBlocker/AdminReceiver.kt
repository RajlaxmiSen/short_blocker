package com.shortsBlocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Shorts Blocker: Uninstall protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "You must enter the Permanent Password before disabling Shorts Blocker admin."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Shorts Blocker: Uninstall protection disabled", Toast.LENGTH_SHORT).show()
    }
}
