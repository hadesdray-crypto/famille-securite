package fr.famille

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret partage entre un parent et un enfant.
 * Le code d'association (34 caracteres) contient le secret (32) + une cle de controle (2) :
 * une faute de frappe est detectee tout de suite, au lieu de ne rien produire.
 */
object Cle {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    const val TAILLE_SECRET = 20

    fun nouveauSecret(): ByteArray = ByteArray(TAILLE_SECRET).also { SecureRandom().nextBytes(it) }

    private fun base32(octets: ByteArray): String {
        val sb = StringBuilder()
        var tampon = 0
        var bits = 0
        for (o in octets) {
            tampon = (tampon shl 8) or (o.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(tampon shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(tampon shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun depuisBase32(texte: String): ByteArray? {
        var tampon = 0
        var bits = 0
        val sortie = java.io.ByteArrayOutputStream()
        for (c in texte) {
            val v = B32.indexOf(c)
            if (v < 0) return null
            tampon = (tampon shl 5) or v
            bits += 5
            if (bits >= 8) {
                sortie.write((tampon shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return sortie.toByteArray()
    }

    private fun controle(secret: ByteArray): String {
        val h = MessageDigest.getInstance("SHA-256").digest(secret)
        return "" + B32[(h[0].toInt() and 0xFF) and 31] + B32[(h[1].toInt() and 0xFF) and 31]
    }

    /** Code affiche au parent, par groupes de 4 : ABCD-EFGH-... */
    fun code(secret: ByteArray): String {
        val brut = base32(secret) + controle(secret)
        return brut.chunked(4).joinToString("-")
    }

    /** Lit un code saisi (majuscules/minuscules, tirets, espaces tolerés). Renvoie null si invalide. */
    fun lireCode(saisie: String): ByteArray? {
        val net = saisie.uppercase().filter { it in B32 }
        if (net.length != 34) return null
        val secret = depuisBase32(net.substring(0, 32)) ?: return null
        if (secret.size < TAILLE_SECRET) return null
        val s = secret.copyOf(TAILLE_SECRET)
        return if (controle(s) == net.substring(32)) s else null
    }

    private fun hmac(cle: ByteArray, donnees: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(cle, "HmacSHA256"))
        return mac.doFinal(donnees.toByteArray(Charsets.UTF_8))
    }

    private fun hex(o: ByteArray) = o.joinToString("") { "%02x".format(it) }

    /** Nom du sujet de relais. sens = "bas" (parent vers enfant) ou "haut" (enfant vers parent). */
    fun sujet(secret: ByteArray, sens: String): String = "fam" + hex(hmac(secret, "sujet-$sens")).take(40)

    private fun cleAes(secret: ByteArray): SecretKeySpec = SecretKeySpec(hmac(secret, "aes-v1"), "AES")

    fun chiffrer(secret: ByteArray, clair: String): String {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, cleAes(secret), GCMParameterSpec(128, nonce))
        val ct = c.doFinal(clair.toByteArray(Charsets.UTF_8))
        return b64(nonce + ct)
    }

    fun dechiffrer(secret: ByteArray, texte: String): String? {
        return try {
            val brut = deB64(texte.trim()) ?: return null
            if (brut.size < 12 + 16) return null
            val nonce = brut.copyOfRange(0, 12)
            val ct = brut.copyOfRange(12, brut.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, cleAes(secret), GCMParameterSpec(128, nonce))
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun b64(o: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < o.size) {
            val b0 = o[i].toInt() and 0xFF
            val b1 = if (i + 1 < o.size) o[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < o.size) o[i + 2].toInt() and 0xFF else 0
            sb.append(ALPHA[b0 shr 2])
            sb.append(ALPHA[((b0 and 3) shl 4) or (b1 shr 4)])
            if (i + 1 < o.size) sb.append(ALPHA[((b1 and 15) shl 2) or (b2 shr 6)])
            if (i + 2 < o.size) sb.append(ALPHA[b2 and 63])
            i += 3
        }
        return sb.toString()
    }

    fun deB64(t: String): ByteArray? {
        val sortie = java.io.ByteArrayOutputStream()
        var tampon = 0
        var bits = 0
        for (c in t) {
            val v = ALPHA.indexOf(c)
            if (v < 0) return null
            tampon = (tampon shl 6) or v
            bits += 6
            if (bits >= 8) {
                sortie.write((tampon shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return sortie.toByteArray()
    }
}
