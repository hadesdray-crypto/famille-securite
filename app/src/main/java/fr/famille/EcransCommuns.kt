package fr.famille

import android.content.Intent
import android.widget.LinearLayout

/** Page de texte (mentions legales, conditions, confidentialite, comment ca marche). */
class TexteActivity : Ecran() {
    override val titre: String
        get() = intent.getStringExtra(EXTRA_TITRE) ?: "Information"
    override val aide: String
        get() = "Lis le texte en faisant défiler la page. Le bouton Retour te ramène à l'écran précédent."

    override fun construire(c: LinearLayout) {
        val texte = intent.getStringExtra(EXTRA_TEXTE) ?: "Texte indisponible."
        c.carte { texte(texte) }
    }

    companion object {
        const val EXTRA_TITRE = "titre"
        const val EXTRA_TEXTE = "texte"
        fun ouvrir(depuis: android.content.Context, titre: String, texte: String) {
            depuis.startActivity(Intent(depuis, TexteActivity::class.java).putExtra(EXTRA_TITRE, titre).putExtra(EXTRA_TEXTE, texte))
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
