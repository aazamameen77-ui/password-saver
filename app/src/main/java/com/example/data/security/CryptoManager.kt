package com.example.data.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Robust Cryptographic Engine providing True End-to-End Encryption for accounts.
 *
 * - Master Key Derivation: PBKDF2WithHmacSHA256 with 65,536 iterations.
 * - Cipher: AES-256 in Galois/Counter Mode (AES/GCM/NoPadding) with 128-bit authentication tag.
 * - Initialization Vectors: 12-byte cryptographically secure random bytes generated per encryption.
 * - Zero-knowledge: Plaintext credentials are never written to disk or logs.
 */
object CryptoManager {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_ALGORITHM = "AES"
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 32

    const val VERIFIER_PLAINTEXT = "E2EE_VAULT_INTEGRITY_CHECK_TOKEN_v1"

    private val secureRandom = SecureRandom()

    data class EncryptionResult(
        val ciphertextBase64: String,
        val ivBase64: String
    )

    /**
     * Generates a cryptographically secure random salt for PBKDF2 key derivation.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit AES secret key from the master password and salt using PBKDF2.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, AES_KEY_ALGORITHM)
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Base64 ciphertext and Base64 IV.
     */
    fun encrypt(plaintext: String, secretKey: SecretKey): EncryptionResult {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return EncryptionResult(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts Base64 ciphertext with Base64 IV using AES-256-GCM.
     */
    fun decrypt(ciphertextBase64: String, ivBase64: String, secretKey: SecretKey): String {
        if (ciphertextBase64.isEmpty() || ivBase64.isEmpty()) return ""

        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Verifies whether the derived key can decrypt the vault verifier token.
     */
    fun verifyKey(verifierCiphertext: String, verifierIv: String, candidateKey: SecretKey): Boolean {
        return try {
            val decrypted = decrypt(verifierCiphertext, verifierIv, candidateKey)
            decrypted == VERIFIER_PLAINTEXT
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Generates a cryptographically secure random password.
     */
    fun generateSecurePassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ" // Removed confusing I, O
        val lower = "abcdefghijkmnopqrstuvwxyz" // Removed confusing l
        val numbers = "23456789" // Removed 0, 1
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val pool = StringBuilder().apply {
            if (includeUppercase) append(upper)
            if (includeLowercase) append(lower)
            if (includeNumbers) append(numbers)
            if (includeSymbols) append(symbols)
        }.toString().ifEmpty { lower + numbers }

        val passwordChars = CharArray(length)
        // Ensure at least one character of each selected type is included
        var idx = 0
        if (includeUppercase && idx < length) passwordChars[idx++] = upper[secureRandom.nextInt(upper.length)]
        if (includeLowercase && idx < length) passwordChars[idx++] = lower[secureRandom.nextInt(lower.length)]
        if (includeNumbers && idx < length) passwordChars[idx++] = numbers[secureRandom.nextInt(numbers.length)]
        if (includeSymbols && idx < length) passwordChars[idx++] = symbols[secureRandom.nextInt(symbols.length)]

        while (idx < length) {
            passwordChars[idx++] = pool[secureRandom.nextInt(pool.length)]
        }

        // Shuffle securely
        for (i in passwordChars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = passwordChars[i]
            passwordChars[i] = passwordChars[j]
            passwordChars[j] = temp
        }

        return String(passwordChars)
    }

    /**
     * Analyzes password strength and returns score (0-100) and qualitative rating.
     */
    fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength(0, "Empty", 0xFF9E9E9E)

        var score = 0
        if (password.length >= 8) score += 20
        if (password.length >= 12) score += 20
        if (password.length >= 16) score += 15

        if (password.any { it.isUpperCase() }) score += 12
        if (password.any { it.isLowerCase() }) score += 12
        if (password.any { it.isDigit() }) score += 11
        if (password.any { !it.isLetterOrDigit() }) score += 15

        // Penalize repeating consecutive characters
        var repeats = 0
        for (i in 1 until password.length) {
            if (password[i] == password[i - 1]) repeats++
        }
        score -= (repeats * 5)
        score = score.coerceIn(0, 100)

        return when {
            score < 30 -> PasswordStrength(score, "Weak", 0xFFEF4444)
            score < 60 -> PasswordStrength(score, "Fair", 0xFFF59E0B)
            score < 80 -> PasswordStrength(score, "Good", 0xFF3B82F6)
            score < 95 -> PasswordStrength(score, "Strong", 0xFF10B981)
            else -> PasswordStrength(score, "Military Grade", 0xFF059669)
        }
    }

    data class PasswordStrength(
        val score: Int,
        val label: String,
        val colorHex: Long
    )
}
