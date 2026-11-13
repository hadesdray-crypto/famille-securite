package fr.famille.parent

import android.content.Context
import android.content.SharedPreferences
import fr.famille.Cle
import fr.famille.Ntfy
import fr.famille.Protocole
import fr.famille.Protocole_SERVEUR
import fr.famille.Recu
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Enfant(
    val id: String,
    var nom: String,
    val code: String,
    var dernierId: String? = null,
    var etat: JSONObject? = null,
    var etatTs: Long = 0L,
    var acks: JSONObject = JSONObject(),
    var intervalleMin: Int = 15
) {
    fun secret(): ByteArray? = Cle.lireCode(code)

    fun enJson(): JSONObject = JSONObject().put("id", id).put("nom", nom).put("code", code)
        .put("dernier", dernierId ?: JSONObject.NULL).put("etat", etat ?: JSONObject.NULL)
        .put("etatTs", etatTs).put("acks", acks).put("min", intervalleMin)

    companion object {
        fun deJson(o: JSONObject) = Enfant(
            o.getString("id"), o.getString("nom"), o.getString("code"),
            if (o.isNull("dernier")) null else o.getString("dernier"),
            if (o.isNull("etat")) null else o.getJSONObject("etat"),
            o.optLong("etatTs", 0L), o.optJSONObject("acks") ?: JSONObject(), o.optInt("min", 15)
        )
    }
}

class Evenement(val enfantId: String, val nom: String, val texte: String)

/** Liste des enfants, dans l'espace prive de l'appli. Toutes les operations sont protegees (ecran + service en parallele). */
object DepotParent {
    private fun p(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("famille_parent", Context.MODE_PRIVATE)

    @Synchronized
    fun liste(ctx: Context): List<Enfant> = try {
        val a = JSONArray(p(ctx).getString("enfants", "[]"))
        (0 until a.length()).map { Enfant.deJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun ecrire(ctx: Context, l: List<Enfant>) {
        val a = JSONArray()
        l.forEach { a.put(it.enJson()) }
        p(ctx).edit().putString("enfants", a.toString()).apply()
    }

    @Synchronized
    fun trouver(ctx: Context, id: String): Enfant? = liste(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun ajouter(ctx: Context, nom: String): Enfant {
        val e = Enfant(UUID.randomUUID().toString().take(8), nom, Cle.code(Cle.nouveauSecret()))
        ecrire(ctx, liste(ctx) + e)
        return e
    }

    @Synchronized
    fun retirer(ctx: Context, id: String) {
        ecrire(ctx, liste(ctx).filter { it.id != id })
        historiqueFichier(ctx, id).delete()
    }

    /** Modifie un enfant de facon atomique. */
    @Synchronized
    fun modifier(ctx: Context, id: String, action: (Enfant) -> Unit) {
        val l = liste(ctx)
        val e = l.firstOrNull { it.id == id } ?: return
        action(e)
        ecrire(ctx, l)
    }

    var serveurDefaut: String = Protocole_SERVEUR

    // ---- historique des positions (un fichier par enfant, une ligne JSON par position) ----
    fun historiqueFichier(ctx: Context, id: String) = File(ctx.filesDir, "historique_$id.jsonl")

    @Synchronized
    fun ajouterPosition(ctx: Context, id: String, pos: JSONObject) {
        val f = historiqueFichier(ctx, id)
        val lignes = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
        val derniere = lignes.lastOrNull()?.let { try { JSONObject(it) } catch (e: Exception) { null } }
        if (derniere != null && derniere.optLong("ts") == pos.optLong("ts")) return
        lignes.add(pos.toString())
        while (lignes.size > 300) lignes.removeAt(0)
        f.writeText(lignes.joinToString("\n") + "\n")
    }

    @Synchronized
    fun positions(ctx: Context, id: String): List<JSONObject> {
        val f = historiqueFichier(ctx, id)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { try { JSONObject(it) } catch (e: Exception) { null } }.reversed()
    }

    @Synchronized
    fun viderHistorique(ctx: Context, id: String) { historiqueFichier(ctx, id).delete() }
}

object Libelles {
    val PERMISSIONS = linkedMapOf(
        "notif" to "Notifications", "loc" to "Position précise", "bgloc" to "Position tout le temps",
        "usage" to "Accès à l'usage", "admin" to "Administrateur (verrouillage)",
        "overlay" to "Affichage par-dessus", "batt" to "Économie de batterie"
    )
}

object SyncParent {
    /** Traite un message recu de l'enfant. Renvoie les evenements a signaler au parent. */
    fun traiter(ctx: Context, enfantId: String, recu: Recu): List<Evenement> {
        val e = DepotParent.trouver(ctx, enfantId) ?: return emptyList()
        val secret = e.secret() ?: return emptyList()
        val m = Protocole.dechiffrer(secret, recu.texte)
        val evts = ArrayList<Evenement>()
        DepotParent.modifier(ctx, enfantId) { x ->
            x.dernierId = recu.id
            if (m != null) when (m.type) {
                Protocole.ETAT -> {
                    val avant = x.etat?.optJSONObject("perms")
                    val apres = m.d.optJSONObject("perms")
                    if (avant != null && apres != null) {
                        for ((cle, nom) in Libelles.PERMISSIONS) {
                            if (avant.optBoolean(cle, true) && !apres.optBoolean(cle, true)) evts.add(Evenement(x.id, x.nom, "${x.nom} a désactivé : $nom"))
                        }
                    }
                    x.etat = m.d
                    x.etatTs = m.ts
                    m.d.optJSONObject("pos")?.let { DepotParent.ajouterPosition(ctx, x.id, it) }
                }
                Protocole.ACK -> x.acks.put(m.d.optString("re"), JSONObject().put("ok", m.d.optBoolean("ok")).put("detail", m.d.optString("detail")).put("ts", m.ts))
                Protocole.ALERTE -> when (m.d.optString("type")) {
                    "admin_desactive" -> evts.add(Evenement(x.id, x.nom, "${x.nom} a désactivé l'administrateur de l'appareil : le verrouillage à distance ne marche plus."))
                    else -> evts.add(Evenement(x.id, x.nom, "Alerte de ${x.nom}."))
                }
            }
            // on garde les 60 derniers accuses de reception seulement
            if (x.acks.length() > 60) {
                val cles = x.acks.keys().asSequence().toList().sortedBy { k -> x.acks.getJSONObject(k).optLong("ts") }
                cles.take(x.acks.length() - 60).forEach { x.acks.remove(it) }
            }
        }
        return evts
    }

    /** Recupere les messages en attente (sans attendre). Renvoie null si le reseau est indisponible. */
    fun relever(ctx: Context, e: Enfant): List<Evenement>? {
        val secret = e.secret() ?: return null
        val recus = Ntfy(DepotParent.serveurDefaut).relever(Cle.sujet(secret, "haut"), e.dernierId) ?: return null
        val evts = ArrayList<Evenement>()
        for (r in recus) evts.addAll(traiter(ctx, e.id, r))
        return evts
    }
}

object Commandes {
    /** Envoie une commande chiffree a l'enfant. Renvoie l'identifiant de la commande, ou null si l'envoi echoue. A appeler hors du fil principal. */
    fun envoyer(e: Enfant, type: String, donnees: JSONObject = JSONObject()): String? {
        val secret = e.secret() ?: return null
        val m = Protocole.nouveau(type, donnees)
        val ok = Ntfy(DepotParent.serveurDefaut).publier(Cle.sujet(secret, "bas"), Protocole.chiffrer(secret, m))
        return if (ok) m.id else null
    }
}
