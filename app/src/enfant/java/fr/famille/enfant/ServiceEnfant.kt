package fr.famille.enfant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import fr.famille.Cle
import fr.famille.Message
import fr.famille.Ntfy
import fr.famille.Protocole
import fr.famille.Rejeu
import fr.famille.enfant.Notifier.CANAL_FOND
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object Notifier {
    const val CANAL_FOND = "fond"
    const val CANAL_IMPORTANT = "important"
    const val ID_FOND = 41
    const val ID_MESSAGE = 42

    fun canaux(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CANAL_FOND, "Protection active", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Montre que l'application Famille est active."
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_IMPORTANT, "Messages de ton parent", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Messages et appels de ton parent."
        })
    }

    @Suppress("DEPRECATION")
    fun construire(ctx: Context, canal: String, titre: String, texte: String): Notification.Builder =
        (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, canal) else Notification.Builder(ctx))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titre).setContentText(texte)
            .setStyle(Notification.BigTextStyle().bigText(texte))

    private fun drapeaux() = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    /** Affiche un message / un appel / une alerte de son, par-dessus tout si l'autorisation existe. */
    fun afficher(ctx: Context, titre: String, texte: String, url: String?, son: Boolean) {
        val i = Intent(ctx, MessageActivity::class.java)
            .putExtra(MessageActivity.EXTRA_TITRE, titre).putExtra(MessageActivity.EXTRA_TEXTE, texte)
            .putExtra(MessageActivity.EXTRA_URL, url).putExtra(MessageActivity.EXTRA_SON, son)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        canaux(ctx)
        val pi = PendingIntent.getActivity(ctx, ID_MESSAGE, i, drapeaux())
        val n = construire(ctx, CANAL_IMPORTANT, titre, texte).setContentIntent(pi).setAutoCancel(true)
            .setFullScreenIntent(pi, true).setCategory(Notification.CATEGORY_CALL).build()
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID_MESSAGE, n) } catch (e: Exception) { }
        if (Settings.canDrawOverlays(ctx)) {
            try { ctx.startActivity(i) } catch (e: Exception) { }
        }
    }
}

class ServiceEnfant : Service() {
    private val arret = AtomicBoolean(false)
    private var ecoute: Thread? = null
    private val travail = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val rejeu = Rejeu()
    private lateinit var reg: ReglagesEnfant

    private val rapport = object : Runnable {
        override fun run() {
            if (arret.get()) return
            travail.execute { envoyerEtat(false) }
            main.postDelayed(this, reg.intervalleMin * 60_000L)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        reg = ReglagesEnfant(this)
        reg.chargerRejeu(rejeu)
    }

    @Suppress("DEPRECATION")
    private fun passerAuPremierPlan() {
        Notifier.canaux(this)
        val ouvrir = PendingIntent.getActivity(this, 1, Intent(this, AccueilEnfant::class.java), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val n = Notifier.construire(this, CANAL_FOND, "Famille est active", "Ton parent peut voir ta position et ton temps d'écran, et t'envoyer un message.")
            .setContentIntent(ouvrir).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                startForeground(Notifier.ID_FOND, n, type)
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(Notifier.ID_FOND, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(Notifier.ID_FOND, n)
            }
        } catch (e: Exception) {
            try { startForeground(Notifier.ID_FOND, n) } catch (e2: Exception) { }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        passerAuPremierPlan()
        if (!reg.associe) { stopSelf(); return START_NOT_STICKY }
        arret.set(false)
        if (ecoute == null || ecoute?.isAlive != true) {
            ecoute = Thread { ecouter() }.also { it.name = "famille-ecoute"; it.start() }
            main.removeCallbacks(rapport)
            main.post(rapport)
        }
        return START_STICKY
    }

    private fun ecouter() {
        val secret = reg.secret() ?: return
        Ntfy(reg.serveur).ecouter(Cle.sujet(secret, "bas"), { reg.dernierId }, { arret.get() }) { recu ->
            reg.dernierId = recu.id
            val m = Protocole.dechiffrer(secret, recu.texte) ?: return@ecouter
            if (!rejeu.accepter(m.id, m.ts)) return@ecouter
            reg.sauverRejeu(rejeu)
            travail.execute { executer(m) }
        }
    }

    private fun ack(m: Message, ok: Boolean, detail: String = "") {
        Liaison.envoyer(this, Protocole.nouveau(Protocole.ACK, JSONObject().put("re", m.id).put("t", m.type).put("ok", ok).put("detail", detail)))
    }

    private fun executer(m: Message) {
        try {
            when (m.type) {
                Protocole.VERROU -> {
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    if (dpm.isAdminActive(Capteurs.composantAdmin(this))) { dpm.lockNow(); ack(m, true) }
                    else {
                        Notifier.afficher(this, "Autorisation manquante", "Ton parent a voulu verrouiller le téléphone. Ouvre Famille Enfant > Autorisations et active « Administrateur de l'appareil ».", null, false)
                        ack(m, false, "admin_inactif")
                    }
                }
                Protocole.MESSAGE -> {
                    Notifier.afficher(this, "Message de ton parent", m.d.optString("texte", ""), null, false)
                    ack(m, true)
                }
                Protocole.POSITION -> { envoyerEtat(true); ack(m, true) }
                Protocole.SONNER -> {
                    Sonner.demarrer(this, m.d.optInt("sec", 30))
                    Notifier.afficher(this, "Ton parent te cherche", "Ton téléphone sonne. Touche « Arrêter le son ».", null, true)
                    ack(m, true)
                }
                Protocole.ARRETER_SON -> { Sonner.arreter(); ack(m, true) }
                Protocole.APPEL -> {
                    val url = m.d.optString("url", "")
                    if (url.startsWith("https://")) {
                        Notifier.afficher(this, "Appel vidéo de ton parent", "Touche « Répondre » pour rejoindre l'appel.", url, false)
                        ack(m, true)
                    } else ack(m, false, "adresse_invalide")
                }
                Protocole.REGLAGE -> {
                    reg.intervalleMin = m.d.optInt("min", reg.intervalleMin)
                    main.removeCallbacks(rapport)
                    main.postDelayed(rapport, reg.intervalleMin * 60_000L)
                    ack(m, true)
                }
                Protocole.PING -> { envoyerEtat(false); ack(m, true) }
                Protocole.DISSOCIER -> {
                    ack(m, true)
                    reg.oublier()
                    arret.set(true)
                    main.post { stopSelf() }
                }
                else -> ack(m, false, "commande_inconnue")
            }
        } catch (e: Exception) {
            ack(m, false, "erreur_" + e.javaClass.simpleName)
        }
    }

    private fun envoyerEtat(actif: Boolean) {
        val (bat, charge) = Capteurs.batterie(this)
        val d = JSONObject()
            .put("bat", bat).put("charge", charge)
            .put("v", fr.famille.BuildConfig.VERSION_NAME)
            .put("perms", Capteurs.permissions(this))
            .put("usage", Capteurs.usageDuJour(this))
        Capteurs.position(this, actif)?.let { d.put("pos", it.enJson()) }
        if (Liaison.envoyer(this, Protocole.nouveau(Protocole.ETAT, d))) reg.derniereSynchro = System.currentTimeMillis()
    }

    override fun onDestroy() {
        arret.set(true)
        main.removeCallbacks(rapport)
        travail.shutdown()
        super.onDestroy()
    }

    companion object {
        fun demarrer(ctx: Context) {
            val i = Intent(ctx, ServiceEnfant::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
                // Android refuse parfois un demarrage en arriere-plan : l'ecran d'accueil le relancera a l'ouverture.
            }
        }
    }
}
