package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object PasswordHasher {
    private const val SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun generateSalt(length: Int = 16): String {
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(SALT_CHARS[random.nextInt(SALT_CHARS.length)])
            }
        }
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(inputPassword: String, salt: String, expectedHash: String): Boolean {
        val computed = hashPassword(inputPassword, salt)
        return computed.equals(expectedHash, ignoreCase = true)
    }
}
