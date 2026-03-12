package com.sunfeld.smsgateway

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.util.Log

sealed class SmsResult {
    data class Success(val phoneNumber: String) : SmsResult()
    data class InvalidNumber(val phoneNumber: String) : SmsResult()
    data class EmptyMessage(val phoneNumber: String) : SmsResult()
    data class PermissionDenied(val error: SecurityException) : SmsResult()
    data class SendFailed(val error: Exception) : SmsResult()
}

/**
 * Utility class that encapsulates Android SmsManager logic for sending
 * SMS messages and tracking delivery status.
 */
class SmsService(private val context: Context) {

    companion object {
        private const val TAG = "SmsService"
        const val ACTION_SMS_SENT = "com.sunfeld.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.sunfeld.smsgateway.SMS_DELIVERED"
        private val PHONE_PATTERN = Regex("^\\+?[1-9]\\d{6,14}$")
    }

    private val smsManager: SmsManager
        get() = context.getSystemService(SmsManager::class.java)

    /**
     * Validate that a phone number is well-formed.
     * Accepts E.164 format and common digit-only formats (7-15 digits).
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val stripped = phoneNumber.replace(Regex("[\\s\\-().]+"), "")
        return stripped.matches(PHONE_PATTERN) || PhoneNumberUtils.isGlobalPhoneNumber(stripped)
    }

    /**
     * Send an SMS message to the specified phone number.
     *
     * @param phoneNumber The destination phone number
     * @param message The text message to send
     * @return SmsResult indicating success or the specific failure reason
     */
    fun sendDirectSms(phoneNumber: String, message: String): SmsResult {
        val stripped = phoneNumber.replace(Regex("[\\s\\-().]+"), "")

        if (!isValidPhoneNumber(stripped)) {
            Log.w(TAG, "Invalid phone number format: $phoneNumber")
            return SmsResult.InvalidNumber(phoneNumber)
        }

        if (message.isBlank()) {
            Log.w(TAG, "Empty message body for $phoneNumber")
            return SmsResult.EmptyMessage(phoneNumber)
        }

        return try {
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
                stripped,
                null,
                message,
                sentIntent,
                deliveredIntent
            )

            Log.d(TAG, "SMS queued to $stripped")
            SmsResult.Success(stripped)
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission denied: ${e.message}", e)
            SmsResult.PermissionDenied(e)
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}", e)
            SmsResult.SendFailed(e)
        }
    }
}
