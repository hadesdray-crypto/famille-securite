package fr.famille.enfant

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import fr.famille.AProposActivity
import fr.famille.Cle
import fr.famille.Ecran
import fr.famille.TexteActivity
import fr.famille.Textes
import fr.famille.Theme
import org.json.JSONObject

class AccueilEnfant : Ecran() {
    override val titre get() = "Famille Enfant"
    override val racine get() = true
    override val aide get() = "Cette application garde le lien avec ton parent. Associe-la avec le code qu'il te donne, puis active chaque autorisation. Une notification reste affichée : c'est normal, elle montre que l'application est active."

    override fun onCreate(etat: android.os.Bundle?) {
        super.onCreate(etat)
        fr.famille.Intro.montrerSiPremiereFois(this)
    }

    override fun onResume() {
        super.onResume()
        reconstruire()
        val reg = ReglagesEnfant(this)
        if (reg.associe) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            }
            ServiceEnfant.demarrer(this)
        }
    }

    override fun construire(c: LinearLayout) {
        val reg = ReglagesEnfant(this)
        if (!reg.associe) {
            c.carte {
                texte("Ce téléphone n'est pas encore associé à un parent.")
                petit("Demande à ton parent le code de 34 caractères, puis touche le bouton ci-dessous.")
            }
            c.bouton("Associer à mon parent", Theme.VERT) { startActivity(Intent(this, AssociationEnfant::class.java)) }
        } else {
            val perms = Capteurs.permissions(this)
            val actives = AutorisationsEnfant.CLES.count { perms.optBoolean(it, false) }
            c.carte {
                texte("Associé à ton parent.", Theme.VERT, 18f)
                val s = reg.derniereSynchro
                petit(if (s == 0L) "Aucun envoi pour l'instant." else "Dernier envoi à ton parent : ${AutorisationsEnfant.ilYA(s)}.")
                petit("Autorisations actives : $actives sur ${AutorisationsEnfant.CLES.size}.")
            }
            c.bouton("Autorisations", if (actives < AutorisationsEnfant.CLES.size) Theme.ORANGE else Theme.GRIS) {
                startActivity(Intent(this, AutorisationsEnfant::class.java))
            }
        }
        c.carte {
            texte("Ce que voit ton parent :", Theme.CYAN)
            petit("• ta position")
            petit("• le temps passé aujourd'hui sur chaque application")
            petit("• le niveau de batterie")
            petit("• si les autorisations de cette application sont actives")
            texte("Ce que ton parent peut faire :", Theme.CYAN)
            petit("• t'envoyer un message")
            petit("• faire sonner ton téléphone")
            petit("• verrouiller l'écran")
            petit("• t'appeler en visio")
        }
        c.bouton("Comment ça marche") { TexteActivity.ouvrir(this, "Comment ça marche", Textes.COMMENT_ENFANT) }
        c.bouton("À propos et mises à jour") { startActivity(Intent(this, AProposActivity::class.java)) }
    }
}

class AssociationEnfant : Ecran() {
    override val titre get() = "Associer à mon parent"
    override val aide get() = "Tape le code de 34 caractères que ton parent t'a donné (lettres et chiffres, les tirets sont facultatifs). « Coller » reprend le code copié. Si le code est refusé, une faute s'est glissée : vérifie chaque caractère."

    override fun construire(c: LinearLayout) {
        c.carte {
            texte("Saisis le code de ton parent")
            petit("Exemple : ABCD-EFGH-IJKL-MNOP-QRST-UVWX-YZ23-45")
        }
        val champ = c.champ("Code d'association")
        val lu = c.petit("")
        champ.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val net = (s?.toString() ?: "").uppercase().filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
                lu.text = if (net.isEmpty()) "" else "Caractères lus : ${net.length} sur 34"
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        c.bouton("Coller le code copié") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
            if (t.isBlank()) message("Rien à coller : copie d'abord le code.") else champ.setText(t)
        }
        c.bouton("Valider le code", Theme.VERT) {
            val secret = Cle.lireCode(champ.text.toString())
            if (secret == null) message("Code incorrect. Vérifie chaque caractère : il doit y en avoir 34.")
            else {
                val reg = ReglagesEnfant(this)
                reg.code = Cle.code(secret)
                reg.dernierId = null
                ServiceEnfant.demarrer(this)
                message("Associé. Active maintenant les autorisations.")
                startActivity(Intent(this, AutorisationsEnfant::class.java))
                finish()
            }
        }
    }
}

class AutorisationsEnfant : Ecran() {
    override val titre get() = "Autorisations"
    override val aide get() = "Chaque ligne doit être « ACTIF ». Touche « Activer » : Android ouvre la bonne page, accepte, puis reviens avec le bouton Retour. Sans ces autorisations, ton parent ne reçoit pas tout."

    override fun onResume() {
        super.onResume()
        reconstruire()
    }

    private class Ligne(val cle: String, val nom: String, val pourquoi: String, val activer: () -> Unit)

    private fun lignes(): List<Ligne> = listOf(
        Ligne("notif", "Notifications", "Pour afficher les messages de ton parent.") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8)
            else ouvrir(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        },
        Ligne("loc", "Position précise", "Pour que ton parent sache où tu es.") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9)
        },
        Ligne("bgloc", "Position tout le temps", "Choisis « Toujours autoriser » dans la page Position, pour que ça marche écran éteint.") {
            if (Build.VERSION.SDK_INT >= 30) ouvrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            else if (Build.VERSION.SDK_INT >= 29) requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 10)
        },
        Ligne("usage", "Accès à l'usage", "Pour compter le temps passé sur chaque application.") {
            ouvrir(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        Ligne("admin", "Administrateur de l'appareil", "Pour que ton parent puisse verrouiller l'écran.") {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Capteurs.composantAdmin(this))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Sert uniquement à verrouiller l'écran quand ton parent le demande.")
            ouvrir(i)
        },
        Ligne("overlay", "Affichage par-dessus les autres applications", "Pour que les messages de ton parent s'affichent tout de suite.") {
            ouvrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        },
        Ligne("batt", "Économie de batterie", "Pour que l'application ne soit pas endormie par le téléphone.") {
            ouvrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    )

    private fun ouvrir(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e2: Exception) { message("Impossible d'ouvrir les réglages.") }
        }
    }

    override fun construire(c: LinearLayout) {
        val perms: JSONObject = Capteurs.permissions(this)
        for (l in lignes()) {
            val actif = perms.optBoolean(l.cle, false)
            c.carte {
                texte(l.nom)
                texte(if (actif) "ACTIF" else "À ACTIVER", if (actif) Theme.VERT else Theme.ORANGE, 15f)
                petit(l.pourquoi)
                if (!actif) bouton("Activer", Theme.VERT) { l.activer() }
            }
        }
        c.carte {
            texte("Si le téléphone bloque quelque chose", Theme.CYAN)
            petit(AIDE_BLOCAGES)
        }
    }

    companion object {
        val CLES = listOf("notif", "loc", "bgloc", "usage", "admin", "overlay", "batt")

        const val AIDE_BLOCAGES =
            "Samsung, Auto Blocker : Paramètres > Sécurité et confidentialité > Auto Blocker. Désactive « Bloquer les installations de logiciels non autorisés » le temps d'une mise à jour.\n\n" +
            "Play Protect : Play Store > ton profil > Play Protect > roue crantée. Si l'installation est refusée, désactive « Analyser les applis avec Play Protect », installe, puis réactive.\n\n" +
            "Samsung, veille des applications : Paramètres > Batterie > Limites d'utilisation en arrière-plan > Applications jamais en veille > ajoute Famille Enfant.\n\n" +
            "Xiaomi : Paramètres > Applications > Autorisations > Démarrage automatique, et batterie « Aucune restriction »."

        fun ilYA(ts: Long): String {
            val s = (System.currentTimeMillis() - ts) / 1000
            return when {
                s < 60 -> "à l'instant"
                s < 3600 -> "il y a ${s / 60} min"
                s < 86400 -> "il y a ${s / 3600} h"
                else -> "il y a ${s / 86400} j"
            }
        }
    }
}

/** Message, appel ou son envoye par le parent. S'affiche meme ecran verrouille. */
class MessageActivity : Ecran() {
    override val titre get() = intent.getStringExtra(EXTRA_TITRE) ?: "Ton parent"
    override val aide get() = "Message de ton parent. Touche « J'ai lu » pour fermer. Si ton téléphone sonne, « Arrêter le son » le coupe."

    override fun onCreate(etat: android.os.Bundle?) {
        super.onCreate(etat)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    override fun construire(c: LinearLayout) {
        val corps = intent.getStringExtra(EXTRA_TEXTE) ?: ""
        val url = intent.getStringExtra(EXTRA_URL)
        val son = intent.getBooleanExtra(EXTRA_SON, false)
        c.carte {
            val t: TextView = texte(corps, Theme.TEXTE, 26f)
            t.gravity = android.view.Gravity.CENTER
        }
        if (url != null && url.startsWith("https://")) {
            c.bouton("Répondre à l'appel", Theme.VERT) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { message("Installe l'application Jitsi Meet pour répondre (demande à ton parent de l'autoriser).") }
            }
        }
        if (son) c.bouton("Arrêter le son", Theme.ROUGE) { Sonner.arreter(); message("Son arrêté.") }
        c.bouton("J'ai lu", Theme.GRIS) { Sonner.arreter(); finish() }
    }

    companion object {
        const val EXTRA_TITRE = "titre"
        const val EXTRA_TEXTE = "texte"
        const val EXTRA_URL = "url"
        const val EXTRA_SON = "son"
    }
}
