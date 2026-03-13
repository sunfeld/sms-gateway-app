package com.sunfeld.smsgateway

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE
import android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE
import android.telephony.SmsManager.RESULT_ERROR_NULL_PDU
import android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

sealed class SmsResult {
    data class Success(val phoneNumber: String) : SmsResult()
    data class InvalidNumber(val phoneNumber: String) : SmsResult()
    data class EmptyMessage(val phoneNumber: String) : SmsResult()
    data class PermissionDenied(val error: SecurityException) : SmsResult()
    data class SendFailed(val error: Exception) : SmsResult()
}

interface SmsStatusListener {
    fun onSmsSent(phoneNumber: String, success: Boolean, errorMessage: String?)
    fun onSmsDelivered(phoneNumber: String, delivered: Boolean)
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
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private val PHONE_PATTERN = Regex("^\\+?[1-9]\\d{6,14}$")
        private val requestCodeCounter = AtomicInteger(0)
    }

    private val smsManager: SmsManager
        get() = context.getSystemService(SmsManager::class.java)

    var statusListener: SmsStatusListener? = null

    private var sentReceiver: BroadcastReceiver? = null
    private var deliveredReceiver: BroadcastReceiver? = null

    fun registerStatusCallbacks() {
        sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "unknown"
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.d(TAG, "SMS sent successfully to $phone")
                        statusListener?.onSmsSent(phone, true, null)
                    }
                    RESULT_ERROR_GENERIC_FAILURE -> {
                        Log.e(TAG, "SMS send failed (generic) to $phone")
                        statusListener?.onSmsSent(phone, false, "Generic failure")
                    }
                    RESULT_ERROR_NO_SERVICE -> {
                        Log.e(TAG, "SMS send failed (no service) to $phone")
                        statusListener?.onSmsSent(phone, false, "No service")
                    }
                    RESULT_ERROR_NULL_PDU -> {
                        Log.e(TAG, "SMS send failed (null PDU) to $phone")
                        statusListener?.onSmsSent(phone, false, "Null PDU")
                    }
                    RESULT_ERROR_RADIO_OFF -> {
                        Log.e(TAG, "SMS send failed (radio off) to $phone")
                        statusListener?.onSmsSent(phone, false, "Radio off")
                    }
                    else -> {
                        Log.e(TAG, "SMS send failed (code=$resultCode) to $phone")
                        statusListener?.onSmsSent(phone, false, "Error code: $resultCode")
                    }
                }
            }
        }

        deliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "unknown"
                val delivered = resultCode == Activity.RESULT_OK
                Log.d(TAG, "SMS delivery status for $phone: ${if (delivered) "delivered" else "failed (code=$resultCode)"}")
                statusListener?.onSmsDelivered(phone, delivered)
            }
        }

        val exportFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }

        context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), exportFlag)
        context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), exportFlag)
        Log.d(TAG, "SMS status callbacks registered")
    }

    fun unregisterStatusCallbacks() {
        sentReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
            sentReceiver = null
        }
        deliveredReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
            deliveredReceiver = null
        }
        Log.d(TAG, "SMS status callbacks unregistered")
    }

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
                requestCodeCounter.getAndIncrement(),
                Intent(ACTION_SMS_SENT).putExtra(EXTRA_PHONE_NUMBER, stripped),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                requestCodeCounter.getAndIncrement(),
                Intent(ACTION_SMS_DELIVERED).putExtra(EXTRA_PHONE_NUMBER, stripped),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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
