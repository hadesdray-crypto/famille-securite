package fr.famille.parent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fr.famille.Cle
import fr.famille.Ntfy
import java.util.concurrent.atomic.AtomicBoolean

/** Ecoute en direct les alertes des enfants (administrateur desactive, autorisation retiree...). */
class ServiceParent : Service() {
    private val arret = AtomicBoolean(false)
    private val fils = ArrayList<Thread>()

    override fun onBind(i: Intent?): IBinder? = null

    private fun canaux() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("fond", "Surveillance des alertes", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("alertes", "Alertes de tes enfants", NotificationManager.IMPORTANCE_HIGH))
    }

    @Suppress("DEPRECATION")
    private fun builder(canal: String, titre: String, texte: String): Notification.Builder =
        (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, canal) else Notification.Builder(this))
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(titre).setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        canaux()
        val ouvrir = PendingIntent.getActivity(this, 1, Intent(this, AccueilParent::class.java), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val n = builder("fond", "Famille Parent", "Surveillance des alertes de tes enfants.").setContentIntent(ouvrir).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(40, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(40, n)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        arret.set(false)
        synchronized(fils) {
            fils.removeAll { !it.isAlive }
            val deja = fils.map { it.name }.toSet()
            for (e in DepotParent.liste(this)) {
                val nom = "ecoute-${e.id}"
                if (nom in deja) continue
                val t = Thread { ecouter(e.id) }
                t.name = nom
                fils.add(t)
                t.start()
            }
        }
        return START_STICKY
    }

    private fun ecouter(enfantId: String) {
        val e0 = DepotParent.trouver(this, enfantId) ?: return
        val secret = e0.secret() ?: return
        Ntfy(DepotParent.serveurDefaut).ecouter(Cle.sujet(secret, "haut"), { DepotParent.trouver(this, enfantId)?.dernierId }, { arret.get() || DepotParent.trouver(this, enfantId) == null }) { recu ->
            for (ev in SyncParent.traiter(this, enfantId, recu)) notifier(ev)
        }
    }

    private fun notifier(ev: Evenement) {
        val ouvrir = PendingIntent.getActivity(this, ev.enfantId.hashCode(), Intent(this, EnfantActivity::class.java).putExtra(EnfantActivity.EXTRA_ID, ev.enfantId),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val n = builder("alertes", "Alerte", ev.texte).setContentIntent(ouvrir).setAutoCancel(true).build()
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(("${ev.enfantId}${ev.texte}").hashCode(), n) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        arret.set(true)
        super.onDestroy()
    }

    companion object {
        fun demarrer(ctx: Context) {
            if (DepotParent.liste(ctx).isEmpty()) return
            try {
                val i = Intent(ctx, ServiceParent::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) { }
        }
    }
}
