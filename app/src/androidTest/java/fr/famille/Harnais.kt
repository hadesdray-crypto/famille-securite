package fr.famille

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Controle commun a TOUS les ecrans : bouton retour visible et etiquete, bouton « ? », titre,
 * rien hors de l'ecran, fleche retour d'Android qui revient en arriere (ou demande « quitter ? » sur l'accueil).
 */
object Harnais {
    private fun instr() = InstrumentationRegistry.getInstrumentation()

    private fun attendre(delaiMs: Long = 6000, condition: () -> Boolean): Boolean {
        val fin = System.currentTimeMillis() + delaiMs
        while (System.currentTimeMillis() < fin) {
            instr().waitForIdleSync()
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private fun appuyerRetourAndroid() {
        instr().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instr().waitForIdleSync()
    }

    private fun accorderNotifications() {
        try {
            instr().uiAutomation.grantRuntimePermission(instr().targetContext.packageName, "android.permission.POST_NOTIFICATIONS")
        } catch (e: Exception) { /* Android < 13 : rien a accorder */ }
    }

    fun verifierEcran(intent: Intent, racine: Boolean, nom: String) {
        accorderNotifications()
        val sc = ActivityScenario.launch<Activity>(intent)
        try {
            instr().waitForIdleSync()
            val vues = arrayOfNulls<View>(3)
            val ecran = arrayOf(Rect(), Rect())
            sc.onActivity { a ->
                val d = a.window.decorView
                vues[0] = d.findViewWithTag<View>("retour")
                vues[1] = d.findViewWithTag<View>("aide")
                vues[2] = d.findViewWithTag<View>("titre")
                val dm = a.resources.displayMetrics
                ecran[0] = Rect(0, 0, dm.widthPixels, dm.heightPixels)
            }
            val retour = vues[0]; val aide = vues[1]; val titre = vues[2]
            assertNotNull("$nom : bouton retour absent", retour)
            assertNotNull("$nom : bouton aide absent", aide)
            assertNotNull("$nom : titre absent", titre)
            assertEquals("$nom : etiquette du bouton retour", if (racine) "Quitter" else "Retour", (retour as Button).text.toString())
            assertEquals("$nom : etiquette du bouton aide", "?", (aide as Button).text.toString())
            assertTrue("$nom : titre vide", (titre as TextView).text.isNotBlank())
            sc.onActivity {
                for ((n, v) in listOf("retour" to retour, "aide" to aide)) {
                    val r = Rect()
                    assertTrue("$nom : bouton $n invisible", v.getGlobalVisibleRect(r))
                    assertTrue("$nom : bouton $n deborde de l'ecran ($r dans ${ecran[0]})", ecran[0].contains(r))
                    assertTrue("$nom : bouton $n trop petit", r.width() >= 40 && r.height() >= 40)
                }
            }

            // 1. aide : ouvre une fenetre, la fleche retour la ferme et on reste sur l'ecran
            sc.onActivity { (aide as View).performClick() }
            instr().waitForIdleSync()
            appuyerRetourAndroid()
            assertTrue("$nom : l'ecran a disparu en fermant l'aide", sc.state == Lifecycle.State.RESUMED)

            // 2. fleche retour d'Android
            appuyerRetourAndroid()
            if (racine) {
                // l'accueil demande « quitter ? » : on reste dessus, la fleche suivante ferme la fenetre
                assertTrue("$nom : l'accueil se ferme sans confirmation", sc.state == Lifecycle.State.RESUMED)
                appuyerRetourAndroid()
                assertTrue("$nom : l'accueil se ferme malgre « rester »", sc.state == Lifecycle.State.RESUMED)
            } else {
                assertTrue("$nom : la fleche retour ne ferme pas l'ecran", attendre { sc.state == Lifecycle.State.DESTROYED })
            }
        } finally {
            try { sc.close() } catch (e: Exception) { }
        }
    }

    /** Le bouton Retour de l'en-tete doit avoir le meme effet que la fleche d'Android. */
    fun verifierBoutonRetour(intent: Intent, nom: String) {
        val sc = ActivityScenario.launch<Activity>(intent)
        try {
            instr().waitForIdleSync()
            sc.onActivity { a -> (a.window.decorView.findViewWithTag<View>("retour") as Button).performClick() }
            assertTrue("$nom : le bouton Retour ne ferme pas l'ecran", attendre { sc.state == Lifecycle.State.DESTROYED })
        } finally {
            try { sc.close() } catch (e: Exception) { }
        }
    }
}
