package fr.famille.enfant

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.famille.Cle
import fr.famille.Harnais
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EcransEnfantTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun associer() { ReglagesEnfant(ctx).code = Cle.code(Cle.nouveauSecret()) }
    private fun dissocier() { ReglagesEnfant(ctx).oublier() }

    @Test fun accueilNonAssocie() {
        dissocier()
        Harnais.verifierEcran(Intent(ctx, AccueilEnfant::class.java), true, "Accueil enfant (non associe)")
    }

    @Test fun accueilAssocie() {
        associer()
        try { Harnais.verifierEcran(Intent(ctx, AccueilEnfant::class.java), true, "Accueil enfant (associe)") } finally { dissocier() }
    }

    @Test fun association() {
        dissocier()
        Harnais.verifierEcran(Intent(ctx, AssociationEnfant::class.java), false, "Association")
        Harnais.verifierBoutonRetour(Intent(ctx, AssociationEnfant::class.java), "Association")
    }

    @Test fun autorisations() {
        Harnais.verifierEcran(Intent(ctx, AutorisationsEnfant::class.java), false, "Autorisations")
        Harnais.verifierBoutonRetour(Intent(ctx, AutorisationsEnfant::class.java), "Autorisations")
    }

    @Test fun messageDuParent() {
        val i = Intent(ctx, MessageActivity::class.java).putExtra(MessageActivity.EXTRA_TEXTE, "Rentre à la maison, il est 18 h. ".repeat(6)).putExtra(MessageActivity.EXTRA_SON, true)
        Harnais.verifierEcran(i, false, "Message du parent")
        Harnais.verifierBoutonRetour(i, "Message du parent")
    }

    @Test fun appelVideo() {
        val i = Intent(ctx, MessageActivity::class.java).putExtra(MessageActivity.EXTRA_TEXTE, "Appel").putExtra(MessageActivity.EXTRA_URL, "https://meet.jit.si/FamilleTest")
        Harnais.verifierEcran(i, false, "Appel video")
    }

    @Test fun codeInvalideRefuseEtCodeValideAccepte() {
        dissocier()
        val secret = Cle.nouveauSecret()
        assert(Cle.lireCode(Cle.code(secret)) != null)
        assert(Cle.lireCode("AAAA-BBBB") == null)
    }
}
