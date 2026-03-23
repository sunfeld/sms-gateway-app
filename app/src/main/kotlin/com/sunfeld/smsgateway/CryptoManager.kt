package com.sunfeld.smsgateway

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Ed25519 key management for the SMS relay client.
 *
 * - Generates Ed25519 keypair stored in EncryptedSharedPreferences
 * - Signs outbound messages (receipts to server)
 * - Verifies inbound messages (SMS commands from server)
 * - Stores server public key after pairing
 * - Key rotation: generate new keypair + re-register
 */
object CryptoManager {

    private const val PREFS_NAME = "sms_relay_crypto"
    private const val KEY_PRIVATE_SEED = "ed25519_private_seed"
    private const val KEY_PUBLIC = "ed25519_public"
    private const val KEY_SERVER_PUBLIC = "server_public_key"
    private const val KEY_RELAY_URL = "relay_url"

    private val ED25519_SPEC = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasKeyPair(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_PRIVATE_SEED) && prefs.contains(KEY_PUBLIC)
    }

    fun generateKeyPair(context: Context) {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)

        val privateKeySpec = EdDSAPrivateKeySpec(seed, ED25519_SPEC)
        val publicKeySpec = EdDSAPublicKeySpec(privateKeySpec.a, ED25519_SPEC)

        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_PRIVATE_SEED, Base64.encodeToString(seed, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, Base64.encodeToString(publicKeySpec.a.toByteArray(), Base64.NO_WRAP))
            .apply()
    }

    fun getPublicKeyBase64(context: Context): String? {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_PUBLIC, null)
    }

    private fun getPrivateKey(context: Context): EdDSAPrivateKey? {
        val prefs = getPrefs(context)
        val seedBase64 = prefs.getString(KEY_PRIVATE_SEED, null) ?: return null
        val seed = Base64.decode(seedBase64, Base64.NO_WRAP)
        val spec = EdDSAPrivateKeySpec(seed, ED25519_SPEC)
        return EdDSAPrivateKey(spec)
    }

    fun sign(context: Context, message: String): String? {
        val privateKey = getPrivateKey(context) ?: return null
        val engine = EdDSAEngine(MessageDigest.getInstance(ED25519_SPEC.hashAlgorithm))
        engine.initSign(privateKey)
        engine.update(message.toByteArray(Charsets.UTF_8))
        val signature = engine.sign()
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun verify(message: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val signature = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val publicKeySpec = EdDSAPublicKeySpec(publicKeyBytes, ED25519_SPEC)
            val publicKey = EdDSAPublicKey(publicKeySpec)

            val engine = EdDSAEngine(MessageDigest.getInstance(ED25519_SPEC.hashAlgorithm))
            engine.initVerify(publicKey)
            engine.update(message.toByteArray(Charsets.UTF_8))
            engine.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    // Server public key management
    fun setServerPublicKey(context: Context, base64Key: String) {
        getPrefs(context).edit().putString(KEY_SERVER_PUBLIC, base64Key).apply()
    }

    fun getServerPublicKey(context: Context): String? {
        return getPrefs(context).getString(KEY_SERVER_PUBLIC, null)
    }

    fun isPaired(context: Context): Boolean {
        return hasKeyPair(context) && getServerPublicKey(context) != null
    }

    // Relay URL
    fun setRelayUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_RELAY_URL, url).apply()
    }

    fun getRelayUrl(context: Context): String {
        return getPrefs(context).getString(KEY_RELAY_URL, null) ?: "wss://sms.sunfeld.nl/ws"
    }

    // Key fingerprint (matches server format)
    fun getFingerprint(base64Key: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(base64Key.toByteArray(Charsets.UTF_8))
        return hash.take(8).joinToString(":") { "%02x".format(it) }.take(19) // 4 groups of 4 hex chars
    }

    // Key rotation: wipe all keys
    fun rotateKeys(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_PRIVATE_SEED)
            .remove(KEY_PUBLIC)
            .remove(KEY_SERVER_PUBLIC)
            .apply()
        generateKeyPair(context)
    }

    fun generateNonce(): String {
        val random = ByteArray(8)
        SecureRandom().nextBytes(random)
        return "${System.currentTimeMillis()}-${random.joinToString("") { "%02x".format(it) }}"
    }
}
