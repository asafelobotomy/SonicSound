package app.sonicsound.remote

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypted Quick Connect envelope (all fields base64). */
data class QuickConnectSealed(
    val clientPub: String = "",
    val nonce: String = "",
    val ct: String = "",
)

/**
 * Quick Connect crypto, mirrored in src/quickconnect/qcCrypto.ts:
 * ECDH P-256 → HKDF-SHA256(salt = code, info = INFO) → AES-256-GCM.
 * Mixing the TV-displayed code into the key means a wrong code fails decryption,
 * and a passive LAN observer never sees the password.
 */
object QuickConnectCrypto {
    private const val INFO = "sonicsound-quickconnect-v1"
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }.generateKeyPair()

    /** Uncompressed SEC1 point (0x04 || X || Y), base64. */
    fun publicKeyBase64(keyPair: KeyPair): String =
        b64.encodeToString(rawPublicKey(keyPair.public as ECPublicKey))

    fun seal(tvPubBase64: String, code: String, plaintext: String): QuickConnectSealed {
        val clientKeys = generateKeyPair()
        val tvPub = decodePublicKey(unb64.decode(tvPubBase64), clientKeys.public as ECPublicKey)
        val key = deriveKey(clientKeys.private, tvPub, code)
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return QuickConnectSealed(
            clientPub = publicKeyBase64(clientKeys),
            nonce = b64.encodeToString(nonce),
            ct = b64.encodeToString(ct),
        )
    }

    /** Returns the plaintext, or null when the code is wrong or the envelope is malformed. */
    fun open(tvKeys: KeyPair, code: String, sealed: QuickConnectSealed): String? = try {
        val clientPub = decodePublicKey(unb64.decode(sealed.clientPub), tvKeys.public as ECPublicKey)
        val key = deriveKey(tvKeys.private, clientPub, code)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, unb64.decode(sealed.nonce)),
        )
        String(cipher.doFinal(unb64.decode(sealed.ct)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun deriveKey(own: PrivateKey, peer: ECPublicKey, code: String): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own)
        agreement.doPhase(peer, true)
        val shared = agreement.generateSecret()
        return hkdfSha256(shared, code.toByteArray(Charsets.UTF_8), INFO.toByteArray(Charsets.UTF_8), 32)
    }

    internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    private fun rawPublicKey(key: ECPublicKey): ByteArray {
        val x = unsigned32(key.w.affineX)
        val y = unsigned32(key.w.affineY)
        return byteArrayOf(0x04) + x + y
    }

    private fun unsigned32(n: BigInteger): ByteArray {
        val bytes = n.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }

    /** Decodes an uncompressed P-256 point; the curve params come from [sameCurve]. */
    private fun decodePublicKey(raw: ByteArray, sameCurve: ECPublicKey): ECPublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "Bad public key" }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        val spec = ECPublicKeySpec(ECPoint(x, y), sameCurve.params)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }
}
