package com.naqaa.app.data

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** One account that can open the application; only a salted hash of the password is kept. */
data class UserAccount(
    val username: String,
    val salt: String,
    val hash: String,
    val createdAt: Instant = Instant.now()
)

/**
 * Who is using the application right now. It lives in memory on purpose: closing the
 * process signs everybody out, and nothing about the session is written to disk.
 */
object Session {

    @Volatile
    var username: String? = null
        private set

    /** True only for the hidden developer account; see [DevAccount]. */
    @Volatile
    var developer: Boolean = false
        private set

    val signedIn: Boolean get() = username != null

    fun open(name: String, isDeveloper: Boolean = false) {
        username = name
        developer = isDeveloper
    }

    fun close() {
        username = null
        developer = false
    }

    /** The name printed on reports. The developer account never appears there. */
    fun reportName(): String? = if (developer) null else username
}

/** Password hashing shared by the user accounts and the developer account. */
object Passwords {

    const val MIN_LENGTH = 6
    const val ITERATIONS = 200_000

    fun salt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decode(text: String): ByteArray? = runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()
}

/**
 * The hidden developer account.
 *
 * Neither the name nor the password is in the source: only a SHA-256 of the lower-cased
 * name and a salted PBKDF2 hash of the password. `tools/make_dev_hash.py` prints new
 * constants. A session opened with it skips the waiting screens and the timers so the
 * features can be tried quickly; it is not stored in the account list and never shows up
 * in reports.
 */
object DevAccount {

    private const val USER_SHA256 = "47444bddb6619293293040e17292b7038e8bdf12082d841e6c3ca4ff7357687d"
    private const val SALT = "Tr2E+tVC2lnxcztZUe3zxw=="
    private const val HASH = "4vHcy9abaIdxcJOCR1oOxpWO5NbFIap6Bs3RxXmsS0w="

    fun isReservedName(username: String): Boolean = sha256(username.trim().lowercase()) == USER_SHA256

    /** Slow by design (PBKDF2); call it off the main thread. */
    fun matches(username: String, password: CharArray): Boolean {
        if (!isReservedName(username)) return false
        val salt = Passwords.decode(SALT) ?: return false
        val expected = Passwords.decode(HASH) ?: return false
        return MessageDigest.isEqual(Passwords.hash(password, salt), expected)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * The accounts of the application, sealed with the same Keystore key as the settings and
 * written through [AtomicFile]. User names are compared without case and are unique.
 */
class UserStore(context: Context, private val vault: Vault) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private var cached: List<UserAccount>? = null

    @Synchronized
    fun all(): List<UserAccount> {
        cached?.let { return it }
        val loaded = if (!file.baseFile.exists()) {
            emptyList()
        } else {
            runCatching { decode(String(vault.open(file.readFully(), CONTEXT), Charsets.UTF_8)) }
                .getOrDefault(emptyList())
        }
        cached = loaded
        return loaded
    }

    fun hasUsers(): Boolean = all().isNotEmpty()

    sealed interface Result {
        data object Ok : Result
        data object NameTaken : Result
        data object NameInvalid : Result
        data object PasswordShort : Result
    }

    /** Slow (PBKDF2); call it off the main thread. */
    @Synchronized
    fun create(username: String, password: CharArray): Result {
        val name = username.trim()
        if (name.length !in 2..24 || name.any { it.isISOControl() }) return Result.NameInvalid
        if (password.size < Passwords.MIN_LENGTH) return Result.PasswordShort
        if (DevAccount.isReservedName(name) || all().any { it.username.equals(name, ignoreCase = true) }) {
            return Result.NameTaken
        }
        val salt = Passwords.salt()
        val account = UserAccount(name, Passwords.encode(salt), Passwords.encode(Passwords.hash(password, salt)))
        write(all() + account)
        return Result.Ok
    }

    /** Returns the stored spelling of the name when the password is right, otherwise null. */
    fun verify(username: String, password: CharArray): String? {
        val account = all().firstOrNull { it.username.equals(username.trim(), ignoreCase = true) } ?: return null
        val salt = Passwords.decode(account.salt) ?: return null
        val expected = Passwords.decode(account.hash) ?: return null
        return if (MessageDigest.isEqual(Passwords.hash(password, salt), expected)) account.username else null
    }

    /** Adds accounts from a backup whose names are not taken yet; returns how many were added. */
    @Synchronized
    fun merge(incoming: List<UserAccount>): Int {
        val current = all()
        val fresh = incoming.filter { candidate ->
            !DevAccount.isReservedName(candidate.username) &&
                current.none { it.username.equals(candidate.username, ignoreCase = true) }
        }
        if (fresh.isNotEmpty()) write(current + fresh)
        return fresh.size
    }

    @Synchronized
    fun wipe() {
        file.delete()
        cached = emptyList()
    }

    @Synchronized
    private fun write(accounts: List<UserAccount>) {
        val bytes = vault.seal(encode(accounts).toByteArray(Charsets.UTF_8), CONTEXT)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        cached = accounts
    }

    companion object {
        private const val FILE_NAME = "users.enc"
        private const val CONTEXT = "users"

        fun encode(accounts: List<UserAccount>): String {
            val array = JSONArray()
            accounts.forEach {
                array.put(
                    JSONObject()
                        .put("username", it.username)
                        .put("salt", it.salt)
                        .put("hash", it.hash)
                        .put("createdAt", it.createdAt.toString())
                )
            }
            return JSONObject().put("users", array).toString()
        }

        fun decode(text: String): List<UserAccount> {
            val array = JSONObject(text).optJSONArray("users") ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("username")
                if (name.isBlank()) return@mapNotNull null
                UserAccount(
                    username = name,
                    salt = item.optString("salt"),
                    hash = item.optString("hash"),
                    createdAt = item.optString("createdAt").takeIf(String::isNotBlank)
                        ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
                )
            }
        }
    }
}
