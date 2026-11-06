package app.sonicsound.remote

import java.security.KeyPair
import java.security.SecureRandom

/**
 * TV-side Quick Connect session: a 6-digit code plus an ephemeral P-256 key pair.
 * One session at a time; it expires, allows [MAX_ATTEMPTS] wrong codes, and is
 * consumed by a successful login.
 */
object QuickConnect {
    const val MAX_ATTEMPTS = 5
    const val SESSION_MS = 5 * 60_000L
    const val HTTP_PORT = 30003

    sealed class OpenResult {
        data class Ok(val payload: String) : OpenResult()
        data class Rejected(val message: String) : OpenResult()
    }

    private class Session(
        val code: String,
        val keys: KeyPair,
        val expiresAtMs: Long,
        var attemptsLeft: Int = MAX_ATTEMPTS,
    )

    private val random = SecureRandom()

    @Volatile
    private var session: Session? = null

    /** Starts a fresh session and returns its code. */
    @Synchronized
    fun start(nowMs: Long = System.currentTimeMillis()): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        session = Session(code, QuickConnectCrypto.generateKeyPair(), nowMs + SESSION_MS)
        return code
    }

    /** Current code, starting a new session if none is usable. */
    @Synchronized
    fun currentOrStart(nowMs: Long = System.currentTimeMillis()): String =
        activeSession(nowMs)?.code ?: start(nowMs)

    @Synchronized
    fun stop() {
        session = null
    }

    @Synchronized
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean = activeSession(nowMs) != null

    /** TV public key for the handshake, or null when no session is open. */
    @Synchronized
    fun publicKey(nowMs: Long = System.currentTimeMillis()): String? =
        activeSession(nowMs)?.let { QuickConnectCrypto.publicKeyBase64(it.keys) }

    @Synchronized
    fun open(sealed: QuickConnectSealed, nowMs: Long = System.currentTimeMillis()): OpenResult {
        val current = activeSession(nowMs)
            ?: return OpenResult.Rejected("Quick Connect is not active. Open it on the TV.")
        val plaintext = QuickConnectCrypto.open(current.keys, current.code, sealed)
        if (plaintext != null) return OpenResult.Ok(plaintext)
        current.attemptsLeft--
        if (current.attemptsLeft <= 0) {
            session = null
            return OpenResult.Rejected("Too many wrong codes. Reopen Quick Connect on the TV.")
        }
        return OpenResult.Rejected("Wrong code. ${current.attemptsLeft} attempts left.")
    }

    private fun activeSession(nowMs: Long): Session? {
        val current = session ?: return null
        if (nowMs >= current.expiresAtMs) {
            session = null
            return null
        }
        return current
    }
}
