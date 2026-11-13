package fr.famille

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class Recu(val id: String, val temps: Long, val texte: String)

/**
 * Relais ntfy (gratuit, sans compte). Les messages sont deja chiffres avant d'arriver ici :
 * le serveur ne voit que du texte illisible.
 */
class Ntfy(serveur: String = Protocole_SERVEUR) {
    private val base = serveur.trimEnd('/')

    fun publier(sujet: String, texte: String): Boolean {
        return try {
            val c = URL("$base/$sujet").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.setRequestProperty("Cache", "yes")
            c.outputStream.use { it.write(texte.toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** Recupere sans attendre les messages publies apres `depuis` (identifiant, duree comme "10m", ou null = 30 min). */
    fun relever(sujet: String, depuis: String?): List<Recu>? {
        return try {
            val since = depuis ?: "30m"
            val c = URL("$base/$sujet/json?poll=1&since=$since").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            if (c.responseCode !in 200..299) {
                c.disconnect()
                return null
            }
            val liste = ArrayList<Recu>()
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                var ligne = r.readLine()
                while (ligne != null) {
                    analyserLigne(ligne)?.let { liste.add(it) }
                    ligne = r.readLine()
                }
            }
            c.disconnect()
            liste
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ecoute en continu (bloquant). Se reconnecte tout seul avec une pause croissante.
     * `arret()` est consulte souvent ; `dernier()` donne l'identifiant a reprendre apres une coupure.
     */
    fun ecouter(sujet: String, dernier: () -> String?, arret: () -> Boolean, surMessage: (Recu) -> Unit) {
        var pause = 2_000L
        while (!arret()) {
            var c: HttpURLConnection? = null
            try {
                val since = dernier() ?: "10m"
                c = URL("$base/$sujet/json?since=$since").openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 100_000
                if (c.responseCode in 200..299) {
                    pause = 2_000L
                    BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                        while (!arret()) {
                            val ligne = r.readLine() ?: break
                            analyserLigne(ligne)?.let { surMessage(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                // coupure reseau : on reessaie plus bas
            } finally {
                try { c?.disconnect() } catch (e: Exception) { }
            }
            var attendu = 0L
            while (!arret() && attendu < pause) {
                try { Thread.sleep(500) } catch (e: InterruptedException) { return }
                attendu += 500
            }
            pause = Math.min(pause * 2, 60_000L)
        }
    }

    companion object {
        /** Renvoie null pour les lignes qui ne sont pas des messages (ouverture, battement de coeur). */
        fun analyserLigne(ligne: String): Recu? = try {
            val o = JSONObject(ligne)
            if (o.optString("event") != "message") null
            else {
                val id = o.getString("id")
                val texte = o.optString("message")
                if (texte.isBlank()) null else Recu(id, o.optLong("time", 0L) * 1000L, texte)
            }
        } catch (e: Exception) {
            null
        }
    }
}

const val Protocole_SERVEUR = "https://ntfy.sh"
