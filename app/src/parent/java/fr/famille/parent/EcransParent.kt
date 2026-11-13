package fr.famille.parent

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import fr.famille.AProposActivity
import fr.famille.Ecran
import fr.famille.Temps
import fr.famille.Theme
import fr.famille.dp
import org.json.JSONObject
import java.util.UUID

class AccueilParent : Ecran() {
    override val titre get() = "Famille Parent"
    override val racine get() = true
    override val aide get() = "La liste de tes enfants. Touche un enfant pour voir sa position, son temps d'écran et lui envoyer une commande. « Ajouter un enfant » crée le code à saisir sur son téléphone."

    private var actif = false

    override fun onCreate(etat: android.os.Bundle?) {
        super.onCreate(etat)
        fr.famille.Intro.montrerSiPremiereFois(this)
    }

    override fun onResume() {
        super.onResume()
        actif = true
        reconstruire()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
        }
        ServiceParent.demarrer(this)
        val liste = DepotParent.liste(this)
        if (liste.isNotEmpty()) Thread {
            var change = false
            for (e in liste) { val r = SyncParent.relever(this, e); if (r != null) change = true }
            if (change) runOnUiThread { if (actif && !isFinishing && !isDestroyed) reconstruire() }
        }.start()
    }

    override fun onPause() {
        actif = false
        super.onPause()
    }

    override fun construire(c: LinearLayout) {
        val liste = DepotParent.liste(this)
        if (liste.isEmpty()) {
            c.carte {
                texte("Aucun enfant pour l'instant.")
                petit("Touche « Ajouter un enfant » : l'application crée un code à saisir sur son téléphone.")
            }
        }
        for (e in liste) {
            c.carte {
                texte(e.nom, Theme.CYAN, 20f)
                val etat = e.etat
                if (etat == null) petit("Pas encore de nouvelles : le code n'a pas été saisi, ou son téléphone est hors ligne.")
                else {
                    petit("Dernier signe : ${Temps.ilYA(e.etatTs)}")
                    val bat = etat.optInt("bat", -1)
                    if (bat >= 0) petit("Batterie : $bat %" + if (etat.optBoolean("charge")) " (en charge)" else "")
                    val manque = manquantes(etat)
                    if (manque > 0) texte("$manque autorisation(s) manquante(s) sur son téléphone", Theme.ORANGE, 15f)
                }
                bouton("Ouvrir", Theme.GRIS) { startActivity(Intent(this@AccueilParent, EnfantActivity::class.java).putExtra(EnfantActivity.EXTRA_ID, e.id)) }
            }
        }
        c.bouton("Ajouter un enfant", Theme.VERT) { startActivity(Intent(this, AjouterEnfant::class.java)) }
        c.bouton("Ouvrir Google Family Link (temps d'écran)") { ouvrirFamilyLink(this) }
        c.bouton("À propos et mises à jour") { startActivity(Intent(this, AProposActivity::class.java)) }
    }

    companion object {
        fun manquantes(etat: JSONObject): Int {
            val p = etat.optJSONObject("perms") ?: return 0
            return Libelles.PERMISSIONS.keys.count { !p.optBoolean(it, true) }
        }

        const val PAQUET_FAMILY_LINK = "com.google.android.apps.kids.familylinkhelper"

        fun ouvrirFamilyLink(ctx: Context) {
            val i = ctx.packageManager.getLaunchIntentForPackage(PAQUET_FAMILY_LINK)
            try {
                if (i != null) ctx.startActivity(i)
                else try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PAQUET_FAMILY_LINK"))) }
                catch (e: Exception) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PAQUET_FAMILY_LINK"))) }
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, "Impossible d'ouvrir Google Family Link.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

class AjouterEnfant : Ecran() {
    override val titre get() = "Ajouter un enfant"
    override val aide get() = "Écris le prénom de l'enfant, puis touche « Créer le code ». Tu saisiras ce code sur son téléphone, dans l'application Famille Enfant."

    override fun construire(c: LinearLayout) {
        c.carte {
            texte("Prénom de l'enfant")
            petit("Il sert seulement à l'afficher dans cette application.")
        }
        val champ = c.champTexte("Prénom")
        c.bouton("Créer le code", Theme.VERT) {
            val nom = champ.text.toString().trim()
            if (nom.isEmpty()) message("Écris d'abord un prénom.")
            else if (nom.length > 30) message("Le prénom est trop long (30 caractères au plus).")
            else {
                val e = DepotParent.ajouter(this, nom)
                startActivity(Intent(this, CodeEnfant::class.java).putExtra(EnfantActivity.EXTRA_ID, e.id))
                finish()
            }
        }
    }
}

class CodeEnfant : Ecran() {
    override val titre get() = "Code d'association"
    override val aide get() = "Ce code relie le téléphone de l'enfant au tien. Saisis-le sur son téléphone, dans « Associer à mon parent ». Ne le montre à personne d'autre."

    override fun construire(c: LinearLayout) {
        val e = DepotParent.trouver(this, intent.getStringExtra(EnfantActivity.EXTRA_ID) ?: "")
        if (e == null) { c.texte("Enfant introuvable."); return }
        c.carte {
            texte("Code pour ${e.nom}", Theme.CYAN, 18f)
            val t = texte(e.code, Theme.TEXTE, 24f)
            t.typeface = Typeface.MONOSPACE
            t.setTextIsSelectable(true)
            petit("34 caractères. Les tirets sont facultatifs à la saisie.")
        }
        c.carte {
            texte("Sur le téléphone de ${e.nom}")
            petit("1. Installe Famille Enfant.\n2. Ouvre-la, touche « Associer à mon parent ».\n3. Tape ce code (ou colle-le), puis « Valider le code ».\n4. Active toutes les autorisations de la page suivante.")
        }
        c.bouton("Copier le code") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", e.code))
            message("Code copié.")
        }
        c.bouton("Partager le code") {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.code)
            try { startActivity(Intent.createChooser(i, "Envoyer le code à")) } catch (x: Exception) { message("Aucune application pour partager.") }
        }
        c.bouton("J'ai saisi le code", Theme.VERT) { finish() }
    }
}

class EnfantActivity : Ecran() {
    override val titre get() = DepotParent.trouver(this, intent.getStringExtra(EXTRA_ID) ?: "")?.nom ?: "Enfant"
    override val aide get() = "Tableau de bord de ton enfant : état, autorisations, position, temps d'écran du jour, et actions à distance. Après une commande, attends la confirmation (quelques secondes) : si le téléphone est éteint ou sans réseau, elle reste valable 10 minutes."

    private var statut: android.widget.TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private var vue = false
    private var carte: WebView? = null
    private var suivi: Runnable? = null

    private fun enfant() = DepotParent.trouver(this, intent.getStringExtra(EXTRA_ID) ?: "")

    override fun onResume() {
        super.onResume()
        vue = true
        val e = enfant()
        if (e == null) { finish(); return }
        reconstruire()
        Thread {
            if (SyncParent.relever(this, e) != null) runOnUiThread { if (vue && !isFinishing && !isDestroyed) reconstruire() }
        }.start()
    }

    override fun onPause() {
        vue = false
        suivi?.let { main.removeCallbacks(it) }
        super.onPause()
    }

    override fun onDestroy() {
        carte?.destroy()
        carte = null
        super.onDestroy()
    }

    override fun construire(c: LinearLayout) {
        carte?.destroy(); carte = null
        val e = enfant() ?: return
        statut = c.petit("Prêt.")
        val etat = e.etat

        c.titreSection("État")
        c.carte {
            if (etat == null) {
                texte("Pas encore de nouvelles.", Theme.ORANGE)
                petit("Vérifie que le code a été saisi sur son téléphone et que l'application Famille Enfant est ouverte.")
            } else {
                texte("Dernier signe : ${Temps.ilYA(e.etatTs)}")
                if (System.currentTimeMillis() - e.etatTs > 3L * e.intervalleMin * 60_000L + 60_000L)
                    texte("Pas de nouvelles depuis longtemps : téléphone éteint, sans réseau, ou application arrêtée.", Theme.ORANGE, 15f)
                val bat = etat.optInt("bat", -1)
                if (bat >= 0) petit("Batterie : $bat %" + if (etat.optBoolean("charge")) " (en charge)" else "")
                petit("Version de l'application enfant : ${etat.optString("v", "?")}")
                petit("Rapport toutes les ${e.intervalleMin} minutes.")
            }
        }

        if (etat != null) {
            c.titreSection("Autorisations sur son téléphone")
            c.carte {
                val p = etat.optJSONObject("perms")
                for ((cle, nom) in Libelles.PERMISSIONS) {
                    val ok = p?.optBoolean(cle, false) ?: false
                    texte("$nom : " + if (ok) "actif" else "MANQUANT", if (ok) Theme.VERT else Theme.ORANGE, 15f)
                }
            }

            c.titreSection("Position")
            val pos = etat.optJSONObject("pos")
            c.carte {
                if (pos == null) petit("Aucune position reçue. Touche « Demander la position maintenant ».")
                else {
                    val la = pos.optDouble("la"); val lo = pos.optDouble("lo")
                    texte("%.5f, %.5f".format(la, lo))
                    petit("Précision : environ ${pos.optDouble("pr").toInt()} m — ${Temps.ilYA(pos.optLong("ts"))}")
                    try {
                        val w = WebView(this@EnfantActivity)
                        w.settings.javaScriptEnabled = true
                        w.contentDescription = "Carte de la position"
                        val d = 0.003
                        w.loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=${lo - d * 1.5},${la - d},${lo + d * 1.5},${la + d}&layer=mapnik&marker=$la,$lo")
                        carte = w
                        addView(w, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(8) })
                    } catch (x: Exception) {
                        petit("Carte indisponible sur ce téléphone.")
                    }
                    bouton("Ouvrir dans l'application Cartes") {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo(${Uri.encode(e.nom)})"))) }
                        catch (x: Exception) { message("Aucune application de cartes.") }
                    }
                }
                bouton("Demander la position maintenant", Theme.VERT) { commande(e, "position", "Demande de position") }
                bouton("Historique des positions") { startActivity(Intent(this@EnfantActivity, HistoriqueActivity::class.java).putExtra(EXTRA_ID, e.id)) }
            }

            c.titreSection("Temps d'écran aujourd'hui")
            c.carte {
                val u = etat.optJSONArray("usage")
                if (u == null || u.length() == 0) petit("Pas de données : l'accès à l'usage n'est pas activé sur son téléphone, ou rien n'a été utilisé.")
                else for (i in 0 until u.length()) {
                    val l = u.getJSONArray(i)
                    petit("${l.getString(0)} : ${minutes(l.getLong(1))}")
                }
            }
        }

        c.titreSection("Actions à distance")
        c.carte {
            bouton("Verrouiller son écran", Theme.ORANGE) {
                dialogue("Verrouiller l'écran ?", "L'écran de ${e.nom} va se verrouiller tout de suite.", "Verrouiller", { commande(e, "verrou", "Verrouillage") }, "Annuler")
            }
            bouton("Envoyer un message") {
                saisie("Message pour ${e.nom}", "Ton message", "", "Envoyer", { t ->
                    if (t.isEmpty()) message("Écris d'abord un message.")
                    else commande(e, "message", "Message", JSONObject().put("texte", t.take(300)))
                }, true)
            }
            bouton("Faire sonner son téléphone") {
                dialogue("Faire sonner ?", "Son téléphone sonnera fort pendant 30 secondes, même en mode silencieux.", "Faire sonner", { commande(e, "sonner", "Sonnerie", JSONObject().put("sec", 30)) }, "Annuler")
            }
            bouton("Arrêter la sonnerie") { commande(e, "arreter_son", "Arrêt de la sonnerie") }
            bouton("Appel vidéo (Jitsi Meet)", Theme.VERT) { appelVideo(e) }
        }

        c.titreSection("Réglages")
        c.carte {
            bouton("Fréquence des rapports : ${e.intervalleMin} min") { choisirFrequence(e) }
            bouton("Temps d'écran et applis : Google Family Link") { AccueilParent.ouvrirFamilyLink(this@EnfantActivity) }
            bouton("Retirer cet enfant", Theme.ROUGE) { retirer(e) }
        }
        c.petit("Pour bloquer des applications ou fixer des limites de temps, utilise Google Family Link : c'est lui que l'enfant ne peut pas contourner.")
    }

    private fun minutes(m: Long) = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"

    private fun commande(e: Enfant, type: String, libelle: String, donnees: JSONObject = JSONObject()) {
        statut?.text = "$libelle : envoi…"
        Thread {
            val id = Commandes.envoyer(e, type, donnees)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (id == null) statut?.text = "$libelle : échec de l'envoi. Vérifie ta connexion Internet."
                else { statut?.text = "$libelle : envoyé, en attente de la réponse du téléphone…"; suivre(e, id, libelle) }
            }
        }.start()
    }

    private fun suivre(e: Enfant, id: String, libelle: String) {
        var essais = 0
        suivi?.let { main.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                if (!vue || isFinishing || isDestroyed) return
                essais++
                Thread {
                    SyncParent.relever(this@EnfantActivity, e)
                    val a = DepotParent.trouver(this@EnfantActivity, e.id)?.acks?.optJSONObject(id)
                    runOnUiThread {
                        if (!vue || isFinishing || isDestroyed) return@runOnUiThread
                        when {
                            a != null && a.optBoolean("ok") -> { statut?.text = "$libelle : confirmé par le téléphone."; reconstruire(); statut?.text = "$libelle : confirmé par le téléphone." }
                            a != null -> statut?.text = "$libelle : refusé par le téléphone (${explication(a.optString("detail"))})."
                            essais >= 25 -> statut?.text = "$libelle : pas de réponse. Son téléphone est peut-être éteint ou sans réseau ; la commande reste valable 10 minutes."
                            else -> main.postDelayed(this, 3000)
                        }
                    }
                }.start()
            }
        }
        suivi = r
        main.postDelayed(r, 2500)
    }

    private fun explication(d: String) = when (d) {
        "admin_inactif" -> "l'administrateur de l'appareil n'est pas activé"
        "commande_inconnue" -> "version de l'application enfant trop ancienne : mets-la à jour"
        "adresse_invalide" -> "adresse d'appel invalide"
        else -> d.ifBlank { "raison inconnue" }
    }

    private fun appelVideo(e: Enfant) {
        val salle = "Famille" + UUID.randomUUID().toString().replace("-", "").take(14)
        val url = "https://meet.jit.si/$salle"
        commande(e, "appel", "Appel vidéo", JSONObject().put("url", url))
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (x: Exception) { message("Installe l'application Jitsi Meet pour appeler en visio.") }
    }

    private fun choisirFrequence(e: Enfant) {
        val valeurs = intArrayOf(5, 15, 30, 60)
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Fréquence des rapports")
            .setItems(arrayOf("Toutes les 5 minutes", "Toutes les 15 minutes", "Toutes les 30 minutes", "Toutes les heures")) { _, i ->
                val m = valeurs[i]
                DepotParent.modifier(this, e.id) { it.intervalleMin = m }
                commande(e, "reglage", "Fréquence", JSONObject().put("min", m))
                reconstruire()
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun retirer(e: Enfant) {
        dialogue("Retirer ${e.nom} ?", "Tu ne verras plus sa position ni son temps d'écran.", "Continuer", {
            dialogue("Dissocier son téléphone ?", "Si son téléphone est en ligne, il arrêtera de t'envoyer des informations. Sinon, désinstalle l'application sur son téléphone.", "Dissocier et retirer", {
                Thread { Commandes.envoyer(e, "dissocier") ; runOnUiThread { DepotParent.retirer(this, e.id); finish() } }.start()
            }, "Retirer seulement", { DepotParent.retirer(this, e.id); finish() })
        }, "Annuler")
    }

    companion object {
        const val EXTRA_ID = "enfant"
    }
}

class HistoriqueActivity : Ecran() {
    override val titre get() = "Historique des positions"
    override val aide get() = "Les dernières positions reçues, de la plus récente à la plus ancienne. Touche une ligne pour l'ouvrir dans l'application Cartes."

    override fun construire(c: LinearLayout) {
        val id = intent.getStringExtra(EnfantActivity.EXTRA_ID) ?: ""
        val liste = DepotParent.positions(this, id)
        if (liste.isEmpty()) c.carte { texte("Aucune position enregistrée pour l'instant.") }
        for (p in liste.take(100)) {
            val la = p.optDouble("la"); val lo = p.optDouble("lo")
            c.carte {
                texte(Temps.heure(p.optLong("ts")))
                petit("%.5f, %.5f — précision ${p.optDouble("pr").toInt()} m".format(la, lo))
                bouton("Ouvrir dans Cartes") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo"))) } catch (x: Exception) { message("Aucune application de cartes.") }
                }
            }
        }
        if (liste.isNotEmpty()) c.bouton("Vider l'historique", Theme.ROUGE) {
            dialogue("Vider l'historique ?", "Toutes les positions enregistrées seront effacées de ce téléphone.", "Vider", { DepotParent.viderHistorique(this, id); reconstruire() }, "Annuler")
        }
    }
}
