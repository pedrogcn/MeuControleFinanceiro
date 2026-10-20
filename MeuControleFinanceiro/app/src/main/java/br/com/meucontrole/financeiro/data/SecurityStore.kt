package br.com.meucontrole.financeiro.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Bloqueio local por PIN (opcional). Guarda apenas sal + hash PBKDF2 do PIN — nunca o PIN em si.
 * O PIN NÃO entra no backup. Se for esquecido, a única saída é limpar os dados do app
 * (por isso o app recomenda exportar backups com frequência).
 */
class SecurityStore(context: Context) {

    private val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_HASH)

    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt).toHex())
            .putInt(KEY_FAILS, 0)
            .apply()
        return true
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromHex() ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.fromHex() ?: return false
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        prefs.edit().putInt(KEY_FAILS, if (ok) 0 else failedAttempts + 1).apply()
        return ok
    }

    val failedAttempts: Int get() = prefs.getInt(KEY_FAILS, 0)

    fun clearPin() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_FAILS).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FAILS = "pin_fails"
        fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }
    }
}
