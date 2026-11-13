package fr.famille

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout

/** Premiere ouverture : l'ecran « Bienvenue » s'affiche une seule fois. */
object Intro {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("famille_intro", Context.MODE_PRIVATE)
    fun vue(ctx: Context): Boolean = p(ctx).getBoolean("vue", false)
    fun marquer(ctx: Context) { p(ctx).edit().putBoolean("vue", true).apply() }

    /** A appeler a l'ouverture de l'accueil : montre « Comment ça marche » une seule fois. */
    fun montrerSiPremiereFois(ecran: Context) {
        if (vue(ecran)) return
        marquer(ecran)
        val t = if (BuildConfig.ROLE == "enfant") Textes.COMMENT_ENFANT else Textes.COMMENT_PARENT
        TexteActivity.ouvrir(ecran, "Bienvenue", t, "J'ai compris")
    }
}

/** Page de texte (mentions legales, conditions, confidentialite, comment ca marche). */
class TexteActivity : Ecran() {
    override val titre: String
        get() = intent.getStringExtra(EXTRA_TITRE) ?: "Information"
    override val aide: String
        get() = "Lis le texte en faisant défiler la page. Le bouton Retour te ramène à l'écran précédent."

    override fun construire(c: LinearLayout) {
        val corps = intent.getStringExtra(EXTRA_TEXTE) ?: "Texte indisponible."
        c.carte { texte(corps) }
        val bouton = intent.getStringExtra(EXTRA_BOUTON)
        if (bouton != null) c.bouton(bouton, Theme.VERT) { finish() }
    }

    companion object {
        const val EXTRA_TITRE = "titre"
        const val EXTRA_TEXTE = "texte"
        const val EXTRA_BOUTON = "bouton"
        fun ouvrir(depuis: Context, titre: String, texte: String, bouton: String? = null) {
            val i = Intent(depuis, TexteActivity::class.java).putExtra(EXTRA_TITRE, titre).putExtra(EXTRA_TEXTE, texte)
            if (bouton != null) i.putExtra(EXTRA_BOUTON, bouton)
            if (depuis !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            depuis.startActivity(i)
        }
    }
}

/** A propos : version, textes legaux, mises a jour. */
class AProposActivity : Ecran() {
    override val titre get() = "À propos"
    override val aide get() = "Ici : la version de l'application, comment elle fonctionne, les mentions légales, et la recherche de mises à jour."

    override fun construire(c: LinearLayout) {
        c.carte {
            texte(getString(R.string.app_name))
            petit("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            petit("Fonctionne sans compte, sans publicité, sans pistage.")
        }
        c.bouton("Comment ça marche", Theme.VERT) {
            val t = if (BuildConfig.ROLE == "enfant") Textes.COMMENT_ENFANT else Textes.COMMENT_PARENT
            TexteActivity.ouvrir(this@AProposActivity, "Comment ça marche", t)
        }
        c.bouton("Mises à jour") { startActivity(Intent(this@AProposActivity, MajActivity::class.java)) }
        c.bouton("Mentions légales") { TexteActivity.ouvrir(this@AProposActivity, "Mentions légales", Textes.MENTIONS) }
        c.bouton("Conditions d'utilisation") { TexteActivity.ouvrir(this@AProposActivity, "Conditions d'utilisation", Textes.CONDITIONS) }
        c.bouton("Confidentialité") { TexteActivity.ouvrir(this@AProposActivity, "Confidentialité", Textes.CONFIDENTIALITE) }
    }
}
