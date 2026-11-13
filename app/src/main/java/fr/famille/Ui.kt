package fr.famille

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object Theme {
    val FOND = Color.parseColor("#0E1A2F")
    val CARTE = Color.parseColor("#16233D")
    val TEXTE = Color.parseColor("#EAF2FF")
    val TEXTE2 = Color.parseColor("#9DB2D3")
    val CYAN = Color.parseColor("#22D3EE")
    val VERT = Color.parseColor("#2E9E5B")
    val ROUGE = Color.parseColor("#C0392B")
    val ORANGE = Color.parseColor("#D98A1F")
    val GRIS = Color.parseColor("#3A4670")
}

object Config {
    /** Adresse qui recevra les signalements du bouton « ? ». A compléter par le propriétaire. */
    const val ADRESSE_SIGNALEMENT = ""
    const val URL_MAJ = "https://raw.githubusercontent.com/hadesdray-crypto/famille-maj/main/version.json"
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun fond(couleur: Int, rayon: Float = 14f): GradientDrawable =
    GradientDrawable().apply { setColor(couleur); cornerRadius = rayon * 3f }

/**
 * Base de TOUS les ecrans. Elle garantit :
 *  - un bouton de retour visible et etiquete en haut ("Quitter" sur l'accueil) ;
 *  - la fleche / le geste retour d'Android : revient en arriere ecran par ecran ;
 *  - un bouton « ? » sur chaque page (aide + signalement d'erreur) ;
 *  - un contenu qui defile, sans debordement meme avec une police a 200 %.
 */
abstract class Ecran : Activity() {
    abstract val titre: String
    open val aide: String get() = "Cette page n'a pas d'aide particulière. Utilise le bouton Retour pour revenir."
    open val racine: Boolean get() = false

    protected lateinit var contenu: LinearLayout
    private lateinit var defile: ScrollView
    private var dialogueOuvert: AlertDialog? = null

    protected abstract fun construire(c: LinearLayout)

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        title = titre
        val racineVue = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.FOND)
        }
        racineVue.addView(entete(), LinearLayout.LayoutParams(-1, -2))
        defile = ScrollView(this).apply { isFillViewport = false }
        contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        defile.addView(contenu, ViewGroup.LayoutParams(-1, -2))
        racineVue.addView(defile, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(racineVue)
        racineVue.setOnApplyWindowInsetsListener { v, ins ->
            val g: Int; val h: Int; val d: Int; val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                g = i.left; h = i.top; d = i.right; b = i.bottom
            } else {
                @Suppress("DEPRECATION")
                run { g = ins.systemWindowInsetLeft; h = ins.systemWindowInsetTop; d = ins.systemWindowInsetRight; b = ins.systemWindowInsetBottom }
            }
            v.setPadding(g, h, d, b)
            ins
        }
        construire(contenu)
    }

    /** Reconstruit le contenu (apres un changement d'etat). */
    protected fun reconstruire() {
        contenu.removeAllViews()
        construire(contenu)
    }

    private fun entete(): View {
        val bloc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.CARTE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val retour = petitBouton(if (racine) "Quitter" else "Retour", Theme.GRIS) { onBackPressed() }
        retour.tag = "retour"
        retour.contentDescription = if (racine) "Quitter l'application" else "Retour à l'écran précédent"
        ligne.addView(retour, LinearLayout.LayoutParams(-2, -2))
        ligne.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val aideB = petitBouton("?", Theme.CYAN, Color.BLACK) { ouvrirAide() }
        aideB.tag = "aide"
        aideB.contentDescription = "Aide et signaler une erreur sur cette page"
        ligne.addView(aideB, LinearLayout.LayoutParams(-2, -2))
        bloc.addView(ligne, LinearLayout.LayoutParams(-1, -2))
        val t = TextView(this).apply {
            text = titre
            setTextColor(Theme.CYAN)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(2))
        }
        t.tag = "titre"
        bloc.addView(t, LinearLayout.LayoutParams(-1, -2))
        return bloc
    }

    private fun petitBouton(texte: String, couleur: Int, texteCouleur: Int = Color.WHITE, action: () -> Unit): Button =
        Button(this).apply {
            text = texte
            isAllCaps = false
            textSize = 16f
            setTextColor(texteCouleur)
            background = fond(couleur, 10f)
            minHeight = dp(48)
            minimumHeight = dp(48)
            minWidth = dp(56)
            minimumWidth = dp(56)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { action() }
        }

    override fun onBackPressed() {
        if (racine) demanderQuitter() else finish()
    }

    private fun demanderQuitter() {
        dialogue("Quitter ?", "Veux-tu fermer l'application ?", "Quitter", { finish() }, "Rester")
    }

    private fun ouvrirAide() {
        val cadre = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        cadre.addView(TextView(this).apply { text = aide; setTextColor(Color.WHITE); textSize = 16f })
        val signaler = Button(this).apply {
            text = "Signaler une erreur sur cette page"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = fond(Theme.ORANGE, 10f)
            minHeight = dp(48)
            setOnClickListener { signaler() }
        }
        cadre.addView(signaler, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val sv = ScrollView(this).apply { addView(cadre) }
        afficher(AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Aide : $titre")
            .setView(sv)
            .setPositiveButton("Fermer", null)
            .create())
    }

    private fun signaler() {
        val rapport = "Page : $titre\nApplication : ${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}\n" +
            "Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" +
            "Décris le problème ici :\n"
        if (Config.ADRESSE_SIGNALEMENT.isBlank()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("rapport", rapport))
            Toast.makeText(this, "Rapport copié. Adresse d'envoi : à compléter.", Toast.LENGTH_LONG).show()
        } else {
            try {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(Config.ADRESSE_SIGNALEMENT))
                    putExtra(Intent.EXTRA_SUBJECT, "Problème : $titre")
                    putExtra(Intent.EXTRA_TEXT, rapport)
                }
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this, "Aucune application de messagerie.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun afficher(d: AlertDialog) {
        dialogueOuvert?.dismiss()
        dialogueOuvert = d
        d.show()
    }

    /** Fenetre avec message defilant et boutons toujours visibles. non = null : un seul bouton. */
    fun dialogue(titre: String, message: String, oui: String, actionOui: () -> Unit, non: String? = null, actionNon: () -> Unit = {}) {
        val sv = ScrollView(this).apply {
            addView(TextView(this@Ecran).apply {
                text = message; setTextColor(Color.WHITE); textSize = 16f
                setPadding(dp(20), dp(8), dp(20), dp(8))
            })
        }
        val b = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titre).setView(sv)
            .setPositiveButton(oui) { _, _ -> actionOui() }
        if (non != null) b.setNegativeButton(non) { _, _ -> actionNon() }
        afficher(b.create())
    }

    /** Fenetre avec un champ de texte. */
    fun saisie(titre: String, indication: String, valeur: String, ok: String, enValeur: (String) -> Unit, multiligne: Boolean = false) {
        val champ = EditText(this).apply {
            hint = indication; setText(valeur); setTextColor(Color.WHITE); setHintTextColor(Theme.TEXTE2)
            textSize = 17f
            inputType = if (multiligne) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        afficher(AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titre).setView(champ)
            .setPositiveButton(ok) { _, _ -> enValeur(champ.text.toString().trim()) }
            .setNegativeButton("Annuler", null)
            .create())
    }

    fun message(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        dialogueOuvert?.dismiss()
        dialogueOuvert = null
        super.onDestroy()
    }

    // ------- elements de page -------

    fun LinearLayout.titreSection(t: String) {
        addView(TextView(this@Ecran).apply {
            text = t; setTextColor(Theme.CYAN); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), dp(16), dp(2), dp(6))
        })
    }

    fun LinearLayout.texte(t: String, couleur: Int = Theme.TEXTE, taille: Float = 16f): TextView {
        val v = TextView(this@Ecran).apply { text = t; setTextColor(couleur); textSize = taille; setPadding(dp(2), dp(4), dp(2), dp(4)) }
        addView(v, LinearLayout.LayoutParams(-1, -2))
        return v
    }

    fun LinearLayout.petit(t: String): TextView = texte(t, Theme.TEXTE2, 14f)

    fun LinearLayout.carte(remplir: LinearLayout.() -> Unit): LinearLayout {
        val c = LinearLayout(this@Ecran).apply {
            orientation = LinearLayout.VERTICAL
            background = fond(Theme.CARTE, 14f)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        c.remplir()
        addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return c
    }

    fun LinearLayout.bouton(t: String, couleur: Int = Theme.GRIS, action: () -> Unit): Button {
        val b = Button(this@Ecran).apply {
            text = t; isAllCaps = false; textSize = 17f; setTextColor(Color.WHITE)
            background = fond(couleur, 12f)
            minHeight = dp(56); minimumHeight = dp(56)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { action() }
        }
        addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return b
    }

    fun LinearLayout.champTexte(indication: String, valeur: String = ""): EditText {
        val e = EditText(this@Ecran).apply {
            hint = indication; setText(valeur); setTextColor(Color.WHITE); setHintTextColor(Theme.TEXTE2)
            textSize = 17f; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = fond(Theme.FOND, 10f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return e
    }

    fun LinearLayout.champ(indication: String, valeur: String = ""): EditText {
        val e = EditText(this@Ecran).apply {
            hint = indication; setText(valeur); setTextColor(Color.WHITE); setHintTextColor(Theme.TEXTE2)
            textSize = 17f; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = fond(Theme.FOND, 10f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return e
    }
}
