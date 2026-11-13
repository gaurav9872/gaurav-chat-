package com.example

import android.app.Application
import com.example.notifications.NotificationHelper

class ChatGauravApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
