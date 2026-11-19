package com.example.util

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Senha guardada no aparelho para o login offline: somente o resultado do
 * PBKDF2 (nunca o texto da senha). Formato: pbkdf2$<algoritmo>$<iterações>$<sal>$<hash>.
 * É gravada após um login online bem-sucedido no Supabase Auth.
 */
object PasswordHasher {

    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    // PBKDF2WithHmacSHA256 só existe a partir do Android 8 (API 26)
    private val defaultAlgorithm: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    fun isHashed(stored: String): Boolean = stored.startsWith("$PREFIX$")

    fun hash(password: String): String {
        val algorithm = defaultAlgorithm
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(algorithm, password, salt, ITERATIONS)
        return listOf(PREFIX, algorithm, ITERATIONS.toString(), encode(salt), encode(derived)).joinToString("$")
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 5 || parts[0] != PREFIX) return false
        return try {
            val salt = Base64.decode(parts[3], Base64.NO_WRAP)
            val expected = Base64.decode(parts[4], Base64.NO_WRAP)
            MessageDigest.isEqual(derive(parts[1], password, salt, parts[2].toInt()), expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(algorithm: String, password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
