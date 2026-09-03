package app.sonicsound.remote

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** LAN Remote pairing: account fingerprint and challenge-response auth (no password on wire). */
object RemoteAuth {
    const val PROTOCOL_VERSION = 2

    fun normalizeServerUrl(url: String): String =
        url.trim().removeSuffix("/").lowercase()

    fun accountFingerprint(url: String, username: String?): String {
        val normalized = normalizeServerUrl(url)
        val user = (username ?: "").lowercase()
        return md5("$normalized|$user")
    }

    fun accountsMatch(
        remoteUrl: String,
        remoteUsername: String?,
        localUrl: String,
        localUsername: String?,
    ): Boolean {
        if (normalizeServerUrl(remoteUrl) != normalizeServerUrl(localUrl)) return false
        val remote = (remoteUsername ?: "").lowercase()
        val local = (localUsername ?: "").lowercase()
        return remote.isNotEmpty() && remote == local
    }

    /** HMAC-SHA256(key=password, data=nonce) as lowercase hex. */
    fun computeAuthProof(nonce: String, password: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(nonce.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyAuthProof(nonce: String, password: String, proof: String): Boolean =
        computeAuthProof(nonce, password).equals(proof, ignoreCase = false)

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
