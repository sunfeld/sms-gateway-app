package com.sunfeld.smsgateway

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.util.Log

/**
 * Utility class that encapsulates Android SmsManager logic for sending
 * SMS messages and tracking delivery status.
 */
class SmsService(private val context: Context) {

    companion object {
        private const val TAG = "SmsService"
        const val ACTION_SMS_SENT = "com.sunfeld.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.sunfeld.smsgateway.SMS_DELIVERED"
    }

    private val smsManager: SmsManager
        get() = context.getSystemService(SmsManager::class.java)

    /**
     * Send an SMS message to the specified phone number.
     *
     * @param phoneNumber The destination phone number
     * @param message The text message to send
     */
    fun sendDirectSms(phoneNumber: String, message: String) {
        val sentIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_SMS_SENT),
            PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_SMS_DELIVERED),
            PendingIntent.FLAG_IMMUTABLE
        )

        smsManager.sendTextMessage(
            phoneNumber,
            null,
            message,
            sentIntent,
            deliveredIntent
        )

        Log.d(TAG, "SMS queued to $phoneNumber")
    }
}
