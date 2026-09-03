package app.sonicsound.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAuthTest {

    @Test
    fun computeAuthProof_isDeterministicHmacSha256Hex() {
        val nonce = "test-nonce-123"
        val password = "s3cret"
        val proof1 = RemoteAuth.computeAuthProof(nonce, password)
        val proof2 = RemoteAuth.computeAuthProof(nonce, password)
        assertEquals(proof1, proof2)
        assertEquals(64, proof1.length)
        assertTrue(proof1.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun verifyAuthProof_succeedsForMatchingProof() {
        val nonce = "abc-def"
        val password = "hunter2"
        val proof = RemoteAuth.computeAuthProof(nonce, password)
        assertTrue(RemoteAuth.verifyAuthProof(nonce, password, proof))
    }

    @Test
    fun verifyAuthProof_failsForWrongPassword() {
        val nonce = "abc-def"
        val proof = RemoteAuth.computeAuthProof(nonce, "correct")
        assertFalse(RemoteAuth.verifyAuthProof(nonce, "wrong", proof))
    }

    @Test
    fun verifyAuthProof_failsForWrongNonce() {
        val password = "correct"
        val proof = RemoteAuth.computeAuthProof("nonce-a", password)
        assertFalse(RemoteAuth.verifyAuthProof("nonce-b", password, proof))
    }

    @Test
    fun verifyAuthProof_failsForTamperedProof() {
        val nonce = "n1"
        val password = "p1"
        val proof = RemoteAuth.computeAuthProof(nonce, password)
        val tampered = if (proof.startsWith("a")) "b" + proof.drop(1) else "a" + proof.drop(1)
        assertNotEquals(proof, tampered)
        assertFalse(RemoteAuth.verifyAuthProof(nonce, password, tampered))
    }

    @Test
    fun verifyAuthProof_rejectsLegacyMd5StyleProof() {
        // Protocol v1 used md5(nonce+password); v2 must fail closed on that.
        val nonce = "legacy-nonce"
        val password = "legacy-pass"
        val legacyMd5 = md5Hex("$nonce$password")
        assertFalse(RemoteAuth.verifyAuthProof(nonce, password, legacyMd5))
        assertNotEquals(legacyMd5, RemoteAuth.computeAuthProof(nonce, password))
    }

    @Test
    fun protocolVersion_isTwo() {
        assertEquals(2, RemoteAuth.PROTOCOL_VERSION)
    }

    private fun md5Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
