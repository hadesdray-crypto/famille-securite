package fr.famille

/** Evenement d'usage (copie simplifiee des evenements Android, pour pouvoir tester le calcul). */
class EvtUsage(val paquet: String, val type: Int, val ts: Long) {
    companion object {
        const val PREMIER_PLAN = 1
        const val ARRIERE_PLAN = 2
    }
}

object UsageCalc {
    /**
     * Temps passe au premier plan par appli, en millisecondes.
     * Une appli encore ouverte a la fin compte jusqu'a `maintenant`.
     */
    fun agreger(evts: List<EvtUsage>, maintenant: Long): Map<String, Long> {
        val debut = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        for (e in evts.sortedBy { it.ts }) {
            when (e.type) {
                EvtUsage.PREMIER_PLAN -> debut[e.paquet] = e.ts
                EvtUsage.ARRIERE_PLAN -> {
                    val d = debut.remove(e.paquet)
                    if (d != null && e.ts >= d) total[e.paquet] = (total[e.paquet] ?: 0L) + (e.ts - d)
                }
            }
        }
        for ((p, d) in debut) if (maintenant >= d) total[p] = (total[p] ?: 0L) + (maintenant - d)
        return total
    }

    /** Les `n` applis les plus utilisees, en minutes entieres (au moins 1 minute pour apparaitre). */
    fun meilleures(total: Map<String, Long>, n: Int): List<Pair<String, Long>> =
        total.entries.map { it.key to it.value / 60_000L }.filter { it.second >= 1 }.sortedByDescending { it.second }.take(n)
}
