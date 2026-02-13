package com.makeramen.around

import android.app.Application
import logcat.AndroidLogcatLogger
import logcat.LogPriority

class AroundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.DEBUG)
    }
}
