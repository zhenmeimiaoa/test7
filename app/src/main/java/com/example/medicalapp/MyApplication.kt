package com.example.medicalapp

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRASH", "Uncaught exception in thread " + thread.name, throwable)
            LogActivity.addLog("CRASH", "Thread: " + thread.name)
            LogActivity.addLog("CRASH", "Exception: " + throwable.javaClass.simpleName)
            LogActivity.addLog("CRASH", "Message: " + throwable.message)
        }
        
        LogActivity.addLog("Application", "App started")
    }
}