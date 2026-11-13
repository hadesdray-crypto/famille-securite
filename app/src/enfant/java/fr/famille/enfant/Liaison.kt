package fr.famille.enfant

import android.content.Context
import fr.famille.Cle
import fr.famille.Message
import fr.famille.Ntfy
import fr.famille.Protocole
import fr.famille.Protocole_SERVEUR
import fr.famille.Rejeu
import org.json.JSONArray

/** Reglages et etat de l'application Enfant (stockes dans l'espace prive de l'appli). */
class ReglagesEnfant(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("famille", Context.MODE_PRIVATE)

    var code: String?
        get() = p.getString("code", null)
        set(v) { if (v == null) p.edit().remove("code").apply() else p.edit().putString("code", v).apply() }

    val associe: Boolean get() = secret() != null

    fun secret(): ByteArray? = code?.let { Cle.lireCode(it) }

    var serveur: String
        get() = p.getString("serveur", Protocole_SERVEUR) ?: Protocole_SERVEUR
        set(v) { p.edit().putString("serveur", v).apply() }

    var intervalleMin: Int
        get() = p.getInt("intervalle", 15)
        set(v) { p.edit().putInt("intervalle", v.coerceIn(5, 120)).apply() }

    var dernierId: String?
        get() = p.getString("dernier_id", null)
        set(v) { if (v != null) p.edit().putString("dernier_id", v).apply() }

    var derniereSynchro: Long
        get() = p.getLong("synchro", 0L)
        set(v) { p.edit().putLong("synchro", v).apply() }

    fun chargerRejeu(r: Rejeu) {
        try {
            val a = JSONArray(p.getString("rejeu", "[]"))
            r.importer((0 until a.length()).map { a.getString(it) })
        } catch (e: Exception) { }
    }

    fun sauverRejeu(r: Rejeu) {
        p.edit().putString("rejeu", JSONArray(r.exporter()).toString()).apply()
    }

    fun oublier() {
        p.edit().remove("code").remove("dernier_id").remove("synchro").remove("rejeu").apply()
    }
}

/** Envoi de messages vers le parent. A appeler hors du fil principal. */
object Liaison {
    fun envoyer(ctx: Context, m: Message): Boolean {
        val reg = ReglagesEnfant(ctx)
        val secret = reg.secret() ?: return false
        return Ntfy(reg.serveur).publier(Cle.sujet(secret, "haut"), Protocole.chiffrer(secret, m))
    }

    fun alerte(ctx: Context, type: String): Boolean {
        val d = org.json.JSONObject().put("type", type)
        return envoyer(ctx, Protocole.nouveau(Protocole.ALERTE, d))
    }
}
