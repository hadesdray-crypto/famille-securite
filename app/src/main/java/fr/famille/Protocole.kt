package fr.famille

import org.json.JSONObject
import java.util.UUID

/** Message echange entre parent et enfant (avant chiffrement). */
class Message(val id: String, val type: String, val ts: Long, val d: JSONObject)

object Protocole {
    // Parent -> enfant
    const val VERROU = "verrou"
    const val MESSAGE = "message"
    const val POSITION = "position"
    const val SONNER = "sonner"
    const val APPEL = "appel"
    const val REGLAGE = "reglage"
    const val PING = "ping"
    const val DISSOCIER = "dissocier"
    const val ARRETER_SON = "arreter_son"

    // Enfant -> parent
    const val ETAT = "etat"
    const val ACK = "ack"
    const val ALERTE = "alerte"

    const val TOLERANCE_MS = 10 * 60 * 1000L

    fun nouveau(type: String, donnees: JSONObject = JSONObject(), ts: Long = System.currentTimeMillis()): Message =
        Message(UUID.randomUUID().toString().replace("-", "").take(16), type, ts, donnees)

    fun enJson(m: Message): String =
        JSONObject().put("i", m.id).put("t", m.type).put("ts", m.ts).put("d", m.d).toString()

    fun lire(json: String): Message? = try {
        val o = JSONObject(json)
        val id = o.getString("i")
        val t = o.getString("t")
        if (id.isBlank() || t.isBlank()) null else Message(id, t, o.getLong("ts"), o.optJSONObject("d") ?: JSONObject())
    } catch (e: Exception) {
        null
    }

    fun chiffrer(secret: ByteArray, m: Message): String = Cle.chiffrer(secret, enJson(m))

    fun dechiffrer(secret: ByteArray, texte: String): Message? {
        val clair = Cle.dechiffrer(secret, texte) ?: return null
        return lire(clair)
    }
}

/**
 * Refuse les messages trop vieux ou deja vus : un message capture ne peut pas etre rejoue
 * (par exemple pour verrouiller le telephone une seconde fois).
 */
class Rejeu(private val capacite: Int = 200) {
    private val vus = LinkedHashSet<String>()

    @Synchronized
    fun accepter(id: String, ts: Long, maintenant: Long = System.currentTimeMillis()): Boolean {
        if (Math.abs(maintenant - ts) > Protocole.TOLERANCE_MS) return false
        if (vus.contains(id)) return false
        vus.add(id)
        while (vus.size > capacite) {
            val premier = vus.iterator().next()
            vus.remove(premier)
        }
        return true
    }

    @Synchronized
    fun exporter(): List<String> = vus.toList()

    @Synchronized
    fun importer(ids: List<String>) {
        vus.clear()
        vus.addAll(ids.takeLast(capacite))
    }
}
