package fr.famille.enfant

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import fr.famille.EvtUsage
import fr.famille.UsageCalc
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Pos(val lat: Double, val lon: Double, val prec: Float, val ts: Long) {
    fun enJson(): JSONObject = JSONObject().put("la", lat).put("lo", lon).put("pr", prec.toDouble()).put("ts", ts)
}

object Capteurs {
    fun batterie(ctx: Context): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val niveau = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val echelle = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val statut = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charge = statut == BatteryManager.BATTERY_STATUS_CHARGING || statut == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (niveau >= 0 && echelle > 0) niveau * 100 / echelle else -1
        return pct to charge
    }

    fun composantAdmin(ctx: Context) = ComponentName(ctx, RecepteurAdmin::class.java)

    fun permissions(ctx: Context): JSONObject {
        val o = JSONObject()
        o.put("loc", ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        o.put("bgloc", Build.VERSION.SDK_INT < 29 || ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
        o.put("usage", usageAutorise(ctx))
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        o.put("admin", dpm.isAdminActive(composantAdmin(ctx)))
        o.put("overlay", Settings.canDrawOverlays(ctx))
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        o.put("notif", nm.areNotificationsEnabled())
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        o.put("batt", pm.isIgnoringBatteryOptimizations(ctx.packageName))
        return o
    }

    fun usageAutorise(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    private fun debutJournee(maintenant: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = maintenant
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Applis les plus utilisees aujourd'hui : tableau de [nom, minutes]. Vide si l'acces n'est pas accorde. */
    fun usageDuJour(ctx: Context, n: Int = 8): JSONArray {
        val sortie = JSONArray()
        if (!usageAutorise(ctx)) return sortie
        try {
            val maintenant = System.currentTimeMillis()
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val flux = usm.queryEvents(debutJournee(maintenant), maintenant)
            val liste = ArrayList<EvtUsage>()
            val e = UsageEvents.Event()
            while (flux.hasNextEvent()) {
                flux.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    val type = if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) EvtUsage.PREMIER_PLAN else EvtUsage.ARRIERE_PLAN
                    liste.add(EvtUsage(e.packageName, type, e.timeStamp))
                }
            }
            val total = UsageCalc.agreger(liste, maintenant).filterKeys { it != ctx.packageName }
            val pm = ctx.packageManager
            for ((paquet, min) in UsageCalc.meilleures(total, n)) {
                val nom = try { pm.getApplicationInfo(paquet, 0).loadLabel(pm).toString() } catch (x: Exception) { paquet }
                sortie.put(JSONArray().put(nom).put(min))
            }
        } catch (e: Exception) {
            // acces refuse ou service indisponible : on renvoie ce qu'on a
        }
        return sortie
    }

    private fun dernierConnu(ctx: Context): Pos? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var meilleur: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(p)
                if (l != null && (meilleur == null || l.time > meilleur.time)) meilleur = l
            } catch (e: Exception) { }
        }
        return meilleur?.let { Pos(it.latitude, it.longitude, it.accuracy, it.time) }
    }

    /** Position. `actif` = on demande un point frais (jusqu'a 40 s), sinon on prend le dernier connu. */
    fun position(ctx: Context, actif: Boolean): Pos? {
        val ancien = dernierConnu(ctx)
        if (!actif || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return ancien
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val resultat = arrayOfNulls<Location>(1)
        val fini = CountDownLatch(1)
        val fournisseurs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
        }
        if (fournisseurs.isEmpty()) return ancien
        val ecouteurs = ArrayList<android.location.LocationListener>()
        val main = Handler(Looper.getMainLooper())
        main.post {
            for (f in fournisseurs) {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        lm.getCurrentLocation(f, null, ctx.mainExecutor) { l ->
                            if (l != null && (resultat[0] == null || l.accuracy < resultat[0]!!.accuracy)) resultat[0] = l
                            if (l != null) fini.countDown()
                        }
                    } else {
                        val ec = object : android.location.LocationListener {
                            override fun onLocationChanged(l: Location) {
                                if (resultat[0] == null || l.accuracy < resultat[0]!!.accuracy) resultat[0] = l
                                fini.countDown()
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                            override fun onProviderEnabled(p: String) {}
                            override fun onProviderDisabled(p: String) {}
                        }
                        ecouteurs.add(ec)
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(f, ec, Looper.getMainLooper())
                    }
                } catch (e: Exception) { }
            }
        }
        try { fini.await(40, TimeUnit.SECONDS) } catch (e: InterruptedException) { }
        if (resultat[0] != null) try { Thread.sleep(1500) } catch (e: InterruptedException) { }   // laisse une chance au point GPS plus precis
        main.post { for (ec in ecouteurs) try { lm.removeUpdates(ec) } catch (e: Exception) { } }
        val l = resultat[0]
        return if (l != null) Pos(l.latitude, l.longitude, l.accuracy, l.time) else ancien
    }
}

object Sonner {
    private var sonnerie: Ringtone? = null
    private var ancienVolume = -1
    private val main = Handler(Looper.getMainLooper())
    private val arreterPlusTard = Runnable { arreter() }

    @Synchronized
    fun demarrer(ctx: Context, secondes: Int) {
        arreter()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ancienVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) } catch (e: Exception) { }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(ctx.applicationContext, uri) ?: return
        r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
        r.play()
        sonnerie = r
        main.postDelayed(arreterPlusTard, secondes.coerceIn(5, 120) * 1000L)
        val appCtx = ctx.applicationContext
        main.post { dernierContexte = appCtx }
    }

    @Volatile private var dernierContexte: Context? = null

    @Synchronized
    fun arreter() {
        main.removeCallbacks(arreterPlusTard)
        try { sonnerie?.stop() } catch (e: Exception) { }
        sonnerie = null
        val ctx = dernierContexte
        if (ctx != null && ancienVolume >= 0) {
            try { (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, ancienVolume, 0) } catch (e: Exception) { }
        }
        ancienVolume = -1
    }

    fun enCours(): Boolean = sonnerie != null
}
