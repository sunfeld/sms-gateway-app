package com.sunfeld.smsgateway

/**
 * Centralized configuration for API targeting.
 * All network clients should reference this object
 * instead of hardcoding URLs.
 */
object Config {
    /**
     * Base URL for the Mission Control API.
     * Points to the host machine (10.0.0.2) on the gateway port.
     * Cleartext traffic to this host is permitted via network_security_config.xml.
     */
    const val BASE_URL = "http://10.0.0.2:8080"
}
