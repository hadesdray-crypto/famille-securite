package fr.famille.parent

import fr.famille.Cle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonneesTest {
    @Test fun enfantAllerRetourJson() {
        val code = Cle.code(Cle.nouveauSecret())
        val e = Enfant("ab12", "Léo", code, "idx", JSONObject().put("bat", 80), 1234L, JSONObject().put("k", JSONObject().put("ok", true)), 30)
        val r = Enfant.deJson(JSONObject(e.enJson().toString()))
        assertEquals("Léo", r.nom)
        assertEquals(code, r.code)
        assertEquals("idx", r.dernierId)
        assertEquals(80, r.etat!!.getInt("bat"))
        assertEquals(1234L, r.etatTs)
        assertEquals(30, r.intervalleMin)
        assertTrue(r.acks.getJSONObject("k").getBoolean("ok"))
        assertNotNull(r.secret())
    }

    @Test fun enfantSansEtatNiDernierId() {
        val e = Enfant("x", "Mia", Cle.code(Cle.nouveauSecret()))
        val r = Enfant.deJson(JSONObject(e.enJson().toString()))
        assertNull(r.dernierId)
        assertNull(r.etat)
        assertEquals(15, r.intervalleMin)
    }

    @Test fun autorisationsManquantesComptees() {
        val perms = JSONObject().put("notif", true).put("loc", false).put("bgloc", false).put("usage", true)
            .put("admin", true).put("overlay", true).put("batt", true)
        assertEquals(2, AccueilParent.manquantes(JSONObject().put("perms", perms)))
        assertEquals(0, AccueilParent.manquantes(JSONObject()))
    }
}
