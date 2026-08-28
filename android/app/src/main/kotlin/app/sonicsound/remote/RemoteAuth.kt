package app.sonicsound.remote

import java.security.MessageDigest

/** LAN Remote pairing: account fingerprint and challenge-response auth (no password on wire). */
object RemoteAuth {
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

    fun computeAuthProof(nonce: String, password: String): String = md5("$nonce$password")

    fun verifyAuthProof(nonce: String, password: String, proof: String): Boolean =
        computeAuthProof(nonce, password) == proof

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
