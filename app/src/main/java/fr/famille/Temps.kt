package fr.famille

object Temps {
    /** "a l'instant", "il y a 5 min", "il y a 3 h", "il y a 2 j". */
    fun ilYA(ts: Long, maintenant: Long = System.currentTimeMillis()): String {
        val s = (maintenant - ts) / 1000
        return when {
            s < 60 -> "à l'instant"
            s < 3600 -> "il y a ${s / 60} min"
            s < 86400 -> "il y a ${s / 3600} h"
            else -> "il y a ${s / 86400} j"
        }
    }

    fun heure(ts: Long): String {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = ts
        return "%02d/%02d %02d:%02d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
}
