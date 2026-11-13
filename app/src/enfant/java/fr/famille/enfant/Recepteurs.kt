package fr.famille.enfant

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Administrateur de l'appareil : sert uniquement a verrouiller l'ecran a la demande du parent. */
class RecepteurAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Si tu désactives cette protection, ton parent sera prévenu."

    override fun onDisabled(context: Context, intent: Intent) {
        Thread { Liaison.alerte(context, "admin_desactive") }.start()
    }
}

/** Relance le service apres un redemarrage ou une mise a jour de l'appli. */
class RecepteurDemarrage : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (ReglagesEnfant(c).associe) ServiceEnfant.demarrer(c)
    }
}
