package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.pref.UserPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val preferences = UserPreferences(context)
            val profile = preferences.userProfile.value
            if (profile.startOnBoot || profile.runForegroundService) {
                AppForegroundService.start(context)
            }
        }
    }
}
