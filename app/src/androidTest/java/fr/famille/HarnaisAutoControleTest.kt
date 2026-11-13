package fr.famille

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Controle du controleur : le banc d'essai doit REJETER des ecrans defectueux connus.
 * S'il les laissait passer, tous ses « OK » sur les vrais ecrans ne vaudraient rien.
 */
class HarnaisAutoControleTest {
    private fun intent(mode: String): Intent {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(ctx, EcranCasse::class.java).putExtra("mode", mode)
    }

    private fun doitEchouer(mode: String, motCle: String) {
        try {
            Harnais.verifierEcran(intent(mode), racine = false, nom = "EcranCasse/$mode")
        } catch (e: AssertionError) {
            assertTrue("le banc a echoue pour une autre raison que prevue : ${e.message}", e.message!!.contains(motCle))
            return
        }
        fail("Le banc d'essai a ACCEPTE un ecran defectueux (mode $mode) : il ne detecte pas les erreurs")
    }

    @Test fun refuseUnEcranSansBoutons() = doitEchouer("sans-boutons", "absent")

    @Test fun refuseUneEtiquetteIncorrecte() = doitEchouer("mauvaise-etiquette", "etiquette")
}
