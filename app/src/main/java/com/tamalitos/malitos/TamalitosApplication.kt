package com.tamalitos.malitos

import android.app.Application

class TamalitosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // WorkManager persists requests; KEEP avoids duplicate jobs on every app launch.
        DriveRuntime.schedule(this)
    }
}
