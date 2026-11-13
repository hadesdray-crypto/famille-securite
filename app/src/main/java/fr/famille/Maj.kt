package fr.famille

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class InfoMaj(val code: Int, val nom: String, val apk: String, val sha256: String, val changes: List<String>)

object Maj {
    /** Lit la partie du fichier de version qui concerne ce role ("enfant" ou "parent"). */
    fun lire(json: String, role: String): InfoMaj? = try {
        val o = JSONObject(json).getJSONObject(role)
        val liste = ArrayList<String>()
        val ch = o.optJSONArray("changes")
        if (ch != null) for (i in 0 until ch.length()) liste.add(ch.getString(i))
        InfoMaj(o.getInt("code"), o.getString("nom"), o.getString("apk"), o.optString("sha256", "").lowercase(), liste)
    } catch (e: Exception) {
        null
    }

    fun plusRecente(info: InfoMaj, codeInstalle: Int) = info.code > codeInstalle

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val tampon = ByteArray(64 * 1024)
            var n = s.read(tampon)
            while (n > 0) { md.update(tampon, 0, n); n = s.read(tampon) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun telechargerTexte(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        val t = if (c.responseCode in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        c.disconnect(); t
    } catch (e: Exception) { null }

    /**
     * Telecharge avec reprise : si une partie du fichier existe deja, on redemande seulement la suite.
     * Renvoie null si tout va bien, sinon le message d'erreur.
     */
    fun telecharger(url: String, partiel: File, progres: (Long, Long) -> Unit): String? {
        return try {
            partiel.parentFile?.mkdirs()
            var deja = if (partiel.exists()) partiel.length() else 0L
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            if (deja > 0) c.setRequestProperty("Range", "bytes=$deja-")
            val code = c.responseCode
            val reprise = code == 206
            if (code != 200 && code != 206) { c.disconnect(); return "Le serveur a répondu $code." }
            if (!reprise) deja = 0L
            val total = c.contentLengthLong.let { if (it > 0) it + deja else -1L }
            FileOutputStream(partiel, reprise).use { out ->
                c.inputStream.use { ins ->
                    val tampon = ByteArray(64 * 1024)
                    var fait = deja
                    var n = ins.read(tampon)
                    while (n > 0) { out.write(tampon, 0, n); fait += n; progres(fait, total); n = ins.read(tampon) }
                }
            }
            c.disconnect()
            null
        } catch (e: Exception) {
            "Connexion interrompue (${e.javaClass.simpleName}). Touche « Reprendre »."
        }
    }

    fun installer(ctx: Context, apk: File): String? = try {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { ins -> s.openWrite("famille.apk", 0, apk.length()).use { out -> ins.copyTo(out); s.fsync(out) } }
            val drapeau = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pend = PendingIntent.getBroadcast(ctx, id, Intent(ctx, RecepteurInstall::class.java).setPackage(ctx.packageName), drapeau or PendingIntent.FLAG_UPDATE_CURRENT)
            s.commit(pend.intentSender)
        }
        null
    } catch (e: Exception) {
        "Installation impossible : ${e.message}"
    }
}

/** Recoit le resultat de l'installateur Android et ouvre sa fenetre de validation. */
class RecepteurInstall : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val statut = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (statut == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val conf = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (conf != null) { conf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(conf) }
        } else if (statut != PackageInstaller.STATUS_SUCCESS) {
            val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Échec de l'installation."
            c.getSharedPreferences("famille", Context.MODE_PRIVATE).edit().putString("maj_erreur", msg).apply()
        }
    }
}

class MajActivity : Ecran() {
    override val titre get() = "Mises à jour"
    override val aide get() = "L'application cherche sur Internet si une nouvelle version existe. Tu vois la liste des changements, puis tu valides. Si le chargement s'interrompt, « Reprendre » continue là où il s'était arrêté. L'ancienne version est gardée en copie."

    /** Adresse du fichier de version. Seules les versions de test (debug) acceptent une adresse imposee. */
    private val adresseVersion: String
        get() = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_URL_MAJ) ?: Config.URL_MAJ else Config.URL_MAJ

    private var info: InfoMaj? = null
    private var etat: TextView? = null
    private var barre: ProgressBar? = null
    private var occupe = false

    override fun construire(c: LinearLayout) {
        c.carte {
            texte("Version installée : ${BuildConfig.VERSION_NAME}")
            etat = petit("Touche « Chercher une mise à jour ».")
            barre = ProgressBar(this@MajActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = android.view.View.GONE }
            addView(barre, LinearLayout.LayoutParams(-1, -2))
        }
        c.bouton("Chercher une mise à jour", Theme.VERT) { chercher(c) }
        c.carte {
            texte("Si l'installation est bloquée", Theme.CYAN)
            petit(Textes.AIDE_BLOCAGES)
        }
        val erreur = getSharedPreferences("famille", MODE_PRIVATE).getString("maj_erreur", null)
        if (erreur != null) {
            c.petit("Dernier échec d'installation : $erreur")
            getSharedPreferences("famille", MODE_PRIVATE).edit().remove("maj_erreur").apply()
        }
    }

    private fun chercher(c: LinearLayout) {
        if (occupe) return
        occupe = true
        etat?.text = "Recherche en cours…"
        Thread {
            val json = Maj.telechargerTexte(adresseVersion)
            val i = if (json == null) null else Maj.lire(json, BuildConfig.ROLE)
            runOnUiThread {
                occupe = false
                if (isFinishing) return@runOnUiThread
                when {
                    json == null -> etat?.text = "Impossible de joindre le serveur. Vérifie la connexion Internet puis réessaie."
                    i == null -> etat?.text = "Le fichier de version est illisible. Réessaie plus tard."
                    !Maj.plusRecente(i, BuildConfig.VERSION_CODE) -> etat?.text = "Tu as déjà la dernière version (${BuildConfig.VERSION_NAME})."
                    else -> proposer(c, i)
                }
            }
        }.start()
    }

    private fun proposer(c: LinearLayout, i: InfoMaj) {
        info = i
        etat?.text = "Nouvelle version disponible : ${i.nom}"
        c.carte {
            texte("Ce qui change :")
            if (i.changes.isEmpty()) petit("Améliorations et corrections.") else i.changes.forEach { petit("• $it") }
        }
        c.bouton("Mettre à jour maintenant", Theme.ORANGE) { demarrer(i) }
    }

    private fun demarrer(i: InfoMaj) {
        if (occupe) return
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            dialogue("Autorisation nécessaire",
                "Android demande ton accord pour installer cette mise à jour. Touche « Ouvrir les réglages », autorise cette application, puis reviens et relance la mise à jour.",
                "Ouvrir les réglages", {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }, "Plus tard")
            return
        }
        occupe = true
        barre?.visibility = android.view.View.VISIBLE
        etat?.text = "Chargement…"
        val dossier = File(filesDir, "maj")
        val partiel = File(dossier, "nouvelle.apk.part")
        Thread {
            val erreur = Maj.telecharger(i.apk, partiel) { fait, total ->
                runOnUiThread {
                    if (total > 0) { barre?.progress = (100L * fait / total).toInt(); etat?.text = "Chargement… ${100L * fait / total} %" }
                }
            }
            if (erreur != null) { fin("$erreur") ; return@Thread }
            if (i.sha256.isNotEmpty() && Maj.sha256(partiel) != i.sha256) {
                partiel.delete()
                fin("Le fichier reçu est incomplet ou abîmé. Touche « Mettre à jour maintenant » pour recommencer.")
                return@Thread
            }
            val fichierFinal = File(dossier, "nouvelle.apk")
            partiel.renameTo(fichierFinal)
            try { File(applicationInfo.sourceDir).copyTo(File(dossier, "precedente.apk"), overwrite = true) } catch (e: Exception) { }
            val r = Maj.installer(this, fichierFinal)
            if (r != null) fin(r) else runOnUiThread { occupe = false; etat?.text = "Valide l'installation dans la fenêtre d'Android." }
        }.start()
    }

    private fun fin(msg: String) {
        runOnUiThread { occupe = false; etat?.text = msg; barre?.visibility = android.view.View.GONE }
    }

    companion object {
        const val EXTRA_URL_MAJ = "url_maj"
    }
}
