package com.makeramen.ouround

import android.app.Application
import logcat.AndroidLogcatLogger
import logcat.LogPriority

class OuroundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.DEBUG)
    }
}
