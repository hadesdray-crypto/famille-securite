package fr.famille

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CleTest {
    @Test fun codeFait34CaracteresEtSeRelit() {
        val s = Cle.nouveauSecret()
        val code = Cle.code(s)
        assertEquals(34 + 8, code.length)   // 34 caracteres + 8 tirets
        assertArrayEquals(s, Cle.lireCode(code))
    }

    @Test fun saisieTolereMinusculesEspacesEtTirets() {
        val s = Cle.nouveauSecret()
        val brut = Cle.code(s).replace("-", "").lowercase()
        assertArrayEquals(s, Cle.lireCode(brut))
        assertArrayEquals(s, Cle.lireCode("  " + Cle.code(s).replace("-", " ") + "  "))
    }

    // Secret fixe : la cle de controle fait 2 caracteres (10 bits), donc un secret tire au hasard laisse passer environ 3 % des cas une faute qui tombe sur la meme cle.
    // Le test verifie le mecanisme de facon reproductible ; la limite d'environ 1 faute sur 1000 est connue.
    @Test fun fauteDeFrappeDetectee() {
        val s = ByteArray(Cle.TAILLE_SECRET) { (it * 7 + 3).toByte() }
        val net = Cle.code(s).replace("-", "")
        for (i in net.indices) {
            val c = net[i]
            val autre = if (c == 'A') 'B' else 'A'
            val faux = net.substring(0, i) + autre + net.substring(i + 1)
            assertNull("faute en position $i non detectee", Cle.lireCode(faux))
        }
    }

    @Test fun codeTropCourtOuVideRefuse() {
        assertNull(Cle.lireCode(""))
        assertNull(Cle.lireCode("ABCD-EFGH"))
        assertNull(Cle.lireCode("!!!!"))
    }

    @Test fun chiffrementAllerRetour() {
        val s = Cle.nouveauSecret()
        val t = "Salut, é à ü — {\"x\":1}"
        assertEquals(t, Cle.dechiffrer(s, Cle.chiffrer(s, t)))
    }

    @Test fun deuxChiffrementsDuMemeTexteDiffèrent() {
        val s = Cle.nouveauSecret()
        assertNotEquals(Cle.chiffrer(s, "a"), Cle.chiffrer(s, "a"))
    }

    @Test fun messageAltereRefuse() {
        val s = Cle.nouveauSecret()
        val c = Cle.chiffrer(s, "verrou")
        val alteré = c.substring(0, c.length - 3) + (if (c.last() == 'A') "BBB" else "AAA")
        assertNull(Cle.dechiffrer(s, alteré))
    }

    @Test fun mauvaiseCleRefuse() {
        assertNull(Cle.dechiffrer(Cle.nouveauSecret(), Cle.chiffrer(Cle.nouveauSecret(), "secret")))
    }

    @Test fun garbageRefuse() {
        val s = Cle.nouveauSecret()
        assertNull(Cle.dechiffrer(s, "n'importe quoi !"))
        assertNull(Cle.dechiffrer(s, ""))
        assertNull(Cle.dechiffrer(s, "AAAA"))
    }

    @Test fun sujetsStablesEtDistincts() {
        val s = Cle.nouveauSecret()
        assertEquals(Cle.sujet(s, "bas"), Cle.sujet(s, "bas"))
        assertNotEquals(Cle.sujet(s, "bas"), Cle.sujet(s, "haut"))
        assertTrue(Cle.sujet(s, "haut").startsWith("fam"))
        assertEquals(43, Cle.sujet(s, "haut").length)
        assertNotEquals(Cle.sujet(Cle.nouveauSecret(), "bas"), Cle.sujet(s, "bas"))
    }

    @Test fun base64Aller_retourSurToutesLesTailles() {
        for (n in 0..40) {
            val o = ByteArray(n) { (it * 7 + n).toByte() }
            assertArrayEquals(o, Cle.deB64(Cle.b64(o)))
        }
    }
}

class ProtocoleTest {
    @Test fun enveloppeAllerRetour() {
        val m = Protocole.nouveau(Protocole.MESSAGE, JSONObject().put("texte", "Bonjour"))
        val lu = Protocole.lire(Protocole.enJson(m))
        assertNotNull(lu)
        assertEquals(m.id, lu!!.id)
        assertEquals("message", lu.type)
        assertEquals("Bonjour", lu.d.getString("texte"))
    }

    @Test fun jsonInvalideRefuse() {
        assertNull(Protocole.lire("pas du json"))
        assertNull(Protocole.lire("{}"))
        assertNull(Protocole.lire("{\"i\":\"\",\"t\":\"x\",\"ts\":1}"))
    }

    @Test fun chiffreDechiffreUnMessage() {
        val s = Cle.nouveauSecret()
        val m = Protocole.nouveau(Protocole.VERROU)
        val lu = Protocole.dechiffrer(s, Protocole.chiffrer(s, m))
        assertEquals(m.id, lu?.id)
        assertEquals(Protocole.VERROU, lu?.type)
    }

    @Test fun identifiantsUniques() {
        val ids = (1..200).map { Protocole.nouveau(Protocole.PING).id }.toSet()
        assertEquals(200, ids.size)
    }
}

class RejeuTest {
    @Test fun accepteUneFoisSeulement() {
        val r = Rejeu()
        assertTrue(r.accepter("a", 1000, 1000))
        assertFalse(r.accepter("a", 1000, 1000))
    }

    @Test fun refuseTropVieuxOuTropNeuf() {
        val r = Rejeu()
        val dix = Protocole.TOLERANCE_MS
        assertFalse(r.accepter("vieux", 0, dix + 1))
        assertFalse(r.accepter("futur", dix + 1, 0))
        assertTrue(r.accepter("limite", 0, dix))
    }

    @Test fun capaciteLimitee() {
        val r = Rejeu(5)
        for (i in 1..10) assertTrue(r.accepter("m$i", 1000, 1000))
        assertEquals(5, r.exporter().size)
        assertTrue("le plus ancien est oublié", r.accepter("m1", 1000, 1000))
        assertFalse("un récent est encore connu", r.accepter("m10", 1000, 1000))
    }

    @Test fun exportEtImportConserventLeCache() {
        val a = Rejeu()
        a.accepter("x", 1000, 1000)
        val b = Rejeu()
        b.importer(a.exporter())
        assertFalse(b.accepter("x", 1000, 1000))
    }
}

class NtfyTest {
    @Test fun ligneMessage() {
        val r = Ntfy.analyserLigne("""{"id":"abc","time":1700000000,"event":"message","topic":"t","message":"Zm9v"}""")
        assertNotNull(r)
        assertEquals("abc", r!!.id)
        assertEquals("Zm9v", r.texte)
        assertEquals(1_700_000_000_000L, r.temps)
    }

    @Test fun ouvertureEtBattementIgnores() {
        assertNull(Ntfy.analyserLigne("""{"id":"x","time":1,"event":"open","topic":"t"}"""))
        assertNull(Ntfy.analyserLigne("""{"id":"x","time":1,"event":"keepalive","topic":"t"}"""))
    }

    @Test fun lignesInvalidesIgnorees() {
        assertNull(Ntfy.analyserLigne(""))
        assertNull(Ntfy.analyserLigne("pas du json"))
        assertNull(Ntfy.analyserLigne("""{"id":"x","time":1,"event":"message","message":""}"""))
    }
}

class UsageCalcTest {
    private fun e(p: String, t: Int, ts: Long) = EvtUsage(p, t, ts)
    private val min = 60_000L

    @Test fun additionneLesPassagesAuPremierPlan() {
        val l = listOf(
            e("a", EvtUsage.PREMIER_PLAN, 0), e("a", EvtUsage.ARRIERE_PLAN, 10 * min),
            e("a", EvtUsage.PREMIER_PLAN, 20 * min), e("a", EvtUsage.ARRIERE_PLAN, 25 * min),
            e("b", EvtUsage.PREMIER_PLAN, 30 * min), e("b", EvtUsage.ARRIERE_PLAN, 31 * min)
        )
        val t = UsageCalc.agreger(l, 60 * min)
        assertEquals(15 * min, t["a"])
        assertEquals(1 * min, t["b"])
    }

    @Test fun appliEncoreOuverteCompteJusquaMaintenant() {
        val t = UsageCalc.agreger(listOf(e("a", EvtUsage.PREMIER_PLAN, 50 * min)), 60 * min)
        assertEquals(10 * min, t["a"])
    }

    @Test fun evenementArriereSansDebutIgnore() {
        val t = UsageCalc.agreger(listOf(e("a", EvtUsage.ARRIERE_PLAN, 5 * min)), 60 * min)
        assertNull(t["a"])
    }

    @Test fun desordreCorrige() {
        val l = listOf(e("a", EvtUsage.ARRIERE_PLAN, 10 * min), e("a", EvtUsage.PREMIER_PLAN, 0))
        assertEquals(10 * min, UsageCalc.agreger(l, 60 * min)["a"])
    }

    @Test fun meilleuresTrieesEtSeuilUneMinute() {
        val total = mapOf("a" to 5 * min, "b" to 30 * min, "c" to 30_000L, "d" to 12 * min)
        val top = UsageCalc.meilleures(total, 2)
        assertEquals(listOf("b" to 30L, "d" to 12L), top)
        assertFalse(UsageCalc.meilleures(total, 10).any { it.first == "c" })
    }
}

class MajTest {
    private val json = """{"enfant":{"code":3,"nom":"1.0.2","apk":"https://x/e.apk","sha256":"ABC","changes":["un","deux"]},"parent":{"code":2,"nom":"1.0.1","apk":"https://x/p.apk"}}"""

    @Test fun litLeBonRole() {
        val e = Maj.lire(json, "enfant")!!
        assertEquals(3, e.code)
        assertEquals("1.0.2", e.nom)
        assertEquals(listOf("un", "deux"), e.changes)
        assertEquals("abc", e.sha256)
        val p = Maj.lire(json, "parent")!!
        assertEquals(2, p.code)
        assertTrue(p.changes.isEmpty())
        assertEquals("", p.sha256)
    }

    @Test fun jsonIllisible() {
        assertNull(Maj.lire("n'importe quoi", "enfant"))
        assertNull(Maj.lire("{}", "enfant"))
    }

    @Test fun comparaisonDeVersions() {
        val e = Maj.lire(json, "enfant")!!
        assertTrue(Maj.plusRecente(e, 2))
        assertFalse(Maj.plusRecente(e, 3))
        assertFalse(Maj.plusRecente(e, 4))
    }

    @Test fun sha256DuFichier() {
        val f = File.createTempFile("maj", ".bin")
        f.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Maj.sha256(f))
        f.delete()
    }
}

class TempsTest {
    @Test fun ilYA() {
        val m = 10_000_000L
        assertEquals("à l'instant", Temps.ilYA(m - 30_000, m))
        assertEquals("il y a 5 min", Temps.ilYA(m - 5 * 60_000, m))
        assertEquals("il y a 3 h", Temps.ilYA(m - 3 * 3_600_000L, m))
        assertEquals("il y a 2 j", Temps.ilYA(m - 2 * 86_400_000L, m))
    }
}
