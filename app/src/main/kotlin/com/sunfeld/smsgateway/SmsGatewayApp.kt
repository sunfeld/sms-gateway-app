package com.sunfeld.smsgateway

import android.app.Application
import android.util.Log

class SmsGatewayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.installGlobalHandler(this)
        CrashLogger.log(this, "APP", "SMS Gateway started — SDK ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.MODEL}")
        Log.d("SmsGatewayApp", "Global crash handler installed")
    }
}
