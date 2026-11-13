package fr.famille

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EcransCommunsTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aPropos() {
        Harnais.verifierEcran(Intent(ctx, AProposActivity::class.java), false, "A propos")
        Harnais.verifierBoutonRetour(Intent(ctx, AProposActivity::class.java), "A propos")
    }

    @Test fun miseAJour() {
        Harnais.verifierEcran(Intent(ctx, MajActivity::class.java), false, "Mises a jour")
        Harnais.verifierBoutonRetour(Intent(ctx, MajActivity::class.java), "Mises a jour")
    }

    @Test fun textes() {
        for ((titre, texte) in listOf("Mentions" to Textes.MENTIONS, "Conditions" to Textes.CONDITIONS, "Confidentialite" to Textes.CONFIDENTIALITE, "Parent" to Textes.COMMENT_PARENT, "Enfant" to Textes.COMMENT_ENFANT)) {
            val i = Intent(ctx, TexteActivity::class.java).putExtra(TexteActivity.EXTRA_TITRE, titre).putExtra(TexteActivity.EXTRA_TEXTE, texte)
            Harnais.verifierEcran(i, false, "Texte $titre")
        }
        Harnais.verifierBoutonRetour(Intent(ctx, TexteActivity::class.java).putExtra(TexteActivity.EXTRA_TEXTE, "x"), "Texte")
    }
}
