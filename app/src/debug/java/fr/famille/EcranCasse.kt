package fr.famille

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Ecran defectueux de controle (versions de test uniquement). Voir AndroidManifest.xml de ce dossier. */
class EcranCasse : Activity() {
    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mode = intent.getStringExtra("mode") ?: "sans-boutons"
        if (mode == "mauvaise-etiquette") {
            c.addView(Button(this).apply { tag = "retour"; text = "Fermer" })
            c.addView(Button(this).apply { tag = "aide"; text = "?" })
            c.addView(TextView(this).apply { tag = "titre"; text = "Ecran casse" })
        } else {
            c.addView(TextView(this).apply { text = "Ecran sans bouton retour ni aide" })
        }
        setContentView(c)
    }
}
