package fr.famille

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit automatique de la carte de navigation : chaque ecran declare dans un manifeste doit etre
 * exerce par un test de navigation (retour, aide, fleche Android). Un ecran ajoute sans test fait echouer ce controle.
 */
class CarteNavigationTest {
    private fun source(chemin: String) = File(chemin)

    private fun ecransDeclares(manifeste: File): Set<String> =
        Regex("<activity[^>]*android:name=\"([^\"]+)\"").findAll(manifeste.readText()).map { it.groupValues[1].substringAfterLast('.') }.toSet()

    private fun texteDesTests(dossiers: List<String>): String =
        dossiers.map { File(it) }.filter { it.exists() }.flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }.joinToString("\n") { it.readText() }

    @Test fun chaqueEcranDeLEnfantEstTeste() {
        val declares = ecransDeclares(source("src/main/AndroidManifest.xml")) + ecransDeclares(source("src/enfant/AndroidManifest.xml"))
        val tests = texteDesTests(listOf("src/androidTest", "src/androidTestEnfant"))
        val manquants = declares.filter { !tests.contains("$it::class.java") }
        assertTrue("Ecrans sans test de navigation (enfant) : $manquants", manquants.isEmpty())
        assertTrue("aucun ecran trouve : le controle ne verifie rien", declares.size >= 6)
    }

    @Test fun chaqueEcranDuParentEstTeste() {
        val declares = ecransDeclares(source("src/main/AndroidManifest.xml")) + ecransDeclares(source("src/parent/AndroidManifest.xml"))
        val tests = texteDesTests(listOf("src/androidTest", "src/androidTestParent"))
        val manquants = declares.filter { !tests.contains("$it::class.java") }
        assertTrue("Ecrans sans test de navigation (parent) : $manquants", manquants.isEmpty())
        assertTrue("aucun ecran trouve : le controle ne verifie rien", declares.size >= 7)
    }

    @Test fun toutEcranHeriteDeLaBaseCommune() {
        // la base Ecran porte le bouton retour, le « ? » et la fleche Android : aucun ecran ne doit l'oublier
        val sources = listOf("src/main/java", "src/enfant/java", "src/parent/java").map { File(it) }.filter { it.exists() }
            .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        val classes = sources.flatMap { Regex("class\\s+(\\w+(?:Activity|Enfant|Parent))\\s*:\\s*(\\w+)\\(\\)").findAll(it.readText()).toList() }
        val intrus = classes.filter { it.groupValues[2] != "Ecran" && !it.groupValues[1].startsWith("Service") }.map { it.groupValues[1] }
        assertTrue("Ecrans qui n'heritent pas de Ecran : $intrus", intrus.isEmpty())
    }
}
