package com.sunfeld.smsgateway

/**
 * Centralized configuration for API targeting.
 * All network clients should reference this object
 * instead of hardcoding URLs.
 */
object Config {
    /**
     * Base URL for the SMS relay server.
     * Public endpoint with Ed25519 mutual authentication.
     * WebSocket URL: wss://sms.sunfeld.nl/ws
     */
    const val RELAY_BASE_URL = "https://sms.sunfeld.nl"
    const val RELAY_WS_URL = "wss://sms.sunfeld.nl/ws"

    /**
     * @deprecated Use RELAY_BASE_URL instead. Kept for legacy API client compatibility.
     */
    @Deprecated("Use RELAY_BASE_URL for the public relay endpoint")
    const val BASE_URL = "https://sms.sunfeld.nl"
}
