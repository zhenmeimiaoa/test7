package com.example.medicalapp

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRASH", "Uncaught exception in thread " + thread.name, throwable)
            LogActivity.addLog("CRASH", "Thread: " + thread.name)
            LogActivity.addLog("CRASH", "Exception: " + throwable.javaClass.simpleName)
            LogActivity.addLog("CRASH", "Message: " + throwable.message)
            LogActivity.addLog("CRASH", "Stack: " + Log.getStackTraceString(throwable))
            
            // 重新抛出，让系统处理
            throw throwable
        }
        
        LogActivity.addLog("Application", "App started")
    }
}