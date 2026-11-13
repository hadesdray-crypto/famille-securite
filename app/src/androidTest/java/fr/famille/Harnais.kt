package fr.famille

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
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

    private fun attendre(delaiMs: Long = 8000, condition: () -> Boolean): Boolean {
        val fin = System.currentTimeMillis() + delaiMs
        while (System.currentTimeMillis() < fin) {
            instr().waitForIdleSync()
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private fun focus(sc: ActivityScenario<Activity>): Boolean {
        var f = false
        try { sc.onActivity { f = it.hasWindowFocus() } } catch (e: Exception) { f = false }
        return f
    }

    /** Attend que l'ecran ait (ou perde) le focus : c'est le signe qu'une fenetre vient de se fermer (ou de s'ouvrir). */
    private fun attendreFocus(sc: ActivityScenario<Activity>, attendu: Boolean): Boolean = attendre(15000) { focus(sc) == attendu }

    /** Ferme les applications qu'un test precedent a pu laisser au premier plan (Reglages Android, etc.) : elles volent le focus. */
    private fun shell(cmd: String): String = try {
        instr().uiAutomation.executeShellCommand(cmd).use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { String(it.readBytes()) } }
    } catch (e: Throwable) { "" }

    /** Fenetre qui a le focus d'apres Android : sert a expliquer un echec de focus. */
    private fun fenetreAuFocus(): String = shell("dumpsys window").lines().filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") }.joinToString(" | ") { it.trim() }

    private fun nettoyerPremierPlan() {
        for (pkg in listOf("com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller")) shell("am force-stop $pkg")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("cmd statusbar collapse")
        instr().waitForIdleSync()
    }

    private fun exigerFocus(sc: ActivityScenario<Activity>, nom: String) {
        if (attendreFocus(sc, true)) return
        nettoyerPremierPlan()   // seconde chance : quelque chose d'autre tient le premier plan
        assertTrue("$nom : l'ecran n'a pas le focus au demarrage (fenetre au focus : ${fenetreAuFocus()})", attendreFocus(sc, true))
    }
    private fun appuyerRetourAndroid() {
        instr().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instr().waitForIdleSync()
    }

    private fun accorderNotifications() {
        if (Build.VERSION.SDK_INT < 33) return   // avant Android 13, rien a accorder
        try {
            instr().uiAutomation.grantRuntimePermission(instr().targetContext.packageName, "android.permission.POST_NOTIFICATIONS")
        } catch (e: Throwable) { /* deja accordee ou refusee : le test ne depend pas de ca */ }
    }

    fun verifierEcran(intent: Intent, racine: Boolean, nom: String) {
        accorderNotifications()
        nettoyerPremierPlan()
        val sc = ActivityScenario.launch<Activity>(intent)
        try {
            exigerFocus(sc, nom)
            val vues = arrayOfNulls<View>(3)
            val ecran = arrayOf(Rect())
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

            // 1. aide : une fenetre s'ouvre (l'ecran perd le focus), la fleche retour la ferme (il le retrouve)
            sc.onActivity { (aide as View).performClick() }
            assertTrue("$nom : la fenetre d'aide ne s'ouvre pas", attendreFocus(sc, false))
            appuyerRetourAndroid()
            assertTrue("$nom : la fenetre d'aide ne se ferme pas avec la fleche retour", attendreFocus(sc, true))
            assertTrue("$nom : l'ecran a disparu en fermant l'aide", sc.state == Lifecycle.State.RESUMED)

            // 2. fleche retour d'Android sur l'ecran lui-meme
            appuyerRetourAndroid()
            if (racine) {
                // l'accueil demande « quitter ? » : une fenetre s'ouvre, on reste sur l'accueil
                assertTrue("$nom : l'accueil ne demande pas confirmation avant de quitter", attendreFocus(sc, false))
                assertTrue("$nom : l'accueil s'est ferme sans confirmation", sc.state == Lifecycle.State.RESUMED)
                appuyerRetourAndroid()
                assertTrue("$nom : la fenetre « quitter ? » ne se ferme pas", attendreFocus(sc, true))
                assertTrue("$nom : l'accueil s'est ferme alors qu'on a choisi de rester", sc.state == Lifecycle.State.RESUMED)
            } else {
                assertTrue("$nom : la fleche retour ne ferme pas l'ecran", attendre { sc.state == Lifecycle.State.DESTROYED })
            }
        } finally {
            try { sc.close() } catch (e: Exception) { }
        }
    }

    /** Le bouton Retour de l'en-tete doit avoir le meme effet que la fleche d'Android. */
    fun verifierBoutonRetour(intent: Intent, nom: String) {
        nettoyerPremierPlan()
        val sc = ActivityScenario.launch<Activity>(intent)
        try {
            exigerFocus(sc, nom)
            sc.onActivity { a -> (a.window.decorView.findViewWithTag<View>("retour") as Button).performClick() }
            assertTrue("$nom : le bouton Retour ne ferme pas l'ecran", attendre { sc.state == Lifecycle.State.DESTROYED })
        } finally {
            try { sc.close() } catch (e: Exception) { }
        }
    }
}
