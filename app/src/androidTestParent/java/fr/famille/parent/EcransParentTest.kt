package fr.famille.parent

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.famille.Harnais
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EcransParentTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var enfantId = ""

    @Before fun preparer() {
        fr.famille.Intro.marquer(ctx)
        DepotParent.liste(ctx).forEach { DepotParent.retirer(ctx, it.id) }
        val e = DepotParent.ajouter(ctx, "Léo")
        enfantId = e.id
        DepotParent.modifier(ctx, e.id) {
            it.etatTs = System.currentTimeMillis() - 120_000
            it.etat = org.json.JSONObject(
                """{"bat":64,"charge":false,"v":"1.0.0","perms":{"notif":true,"loc":true,"bgloc":false,"usage":true,"admin":true,"overlay":true,"batt":false},
                    "usage":[["Vidéos",95],["Jeux",42],["Navigateur",7]],
                    "pos":{"la":48.8566,"lo":2.3522,"pr":18.0,"ts":${System.currentTimeMillis() - 60_000}}}"""
            )
        }
        DepotParent.ajouterPosition(ctx, e.id, org.json.JSONObject("""{"la":48.85,"lo":2.35,"pr":25.0,"ts":${System.currentTimeMillis() - 600_000}}"""))
    }

    @After fun nettoyer() { DepotParent.liste(ctx).forEach { DepotParent.retirer(ctx, it.id) } }

    @Test fun accueilAvecEnfant() {
        Harnais.verifierEcran(Intent(ctx, AccueilParent::class.java), true, "Accueil parent")
    }

    @Test fun accueilSansEnfant() {
        DepotParent.liste(ctx).forEach { DepotParent.retirer(ctx, it.id) }
        Harnais.verifierEcran(Intent(ctx, AccueilParent::class.java), true, "Accueil parent (vide)")
    }

    @Test fun ajouterUnEnfant() {
        Harnais.verifierEcran(Intent(ctx, AjouterEnfant::class.java), false, "Ajouter un enfant")
        Harnais.verifierBoutonRetour(Intent(ctx, AjouterEnfant::class.java), "Ajouter un enfant")
    }

    @Test fun codeDeLEnfant() {
        val i = Intent(ctx, CodeEnfant::class.java).putExtra(EnfantActivity.EXTRA_ID, enfantId)
        Harnais.verifierEcran(i, false, "Code enfant")
        Harnais.verifierBoutonRetour(i, "Code enfant")
    }

    @Test fun tableauDeBordEnfant() {
        val i = Intent(ctx, EnfantActivity::class.java).putExtra(EnfantActivity.EXTRA_ID, enfantId)
        Harnais.verifierEcran(i, false, "Tableau de bord enfant")
        Harnais.verifierBoutonRetour(i, "Tableau de bord enfant")
    }

    @Test fun historiqueDesPositions() {
        val i = Intent(ctx, HistoriqueActivity::class.java).putExtra(EnfantActivity.EXTRA_ID, enfantId)
        Harnais.verifierEcran(i, false, "Historique")
        Harnais.verifierBoutonRetour(i, "Historique")
    }
}
