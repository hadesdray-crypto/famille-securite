package fr.famille

object Textes {
    const val A_COMPLETER = "[à compléter]"

    val MENTIONS = """
Éditeur de l'application
Nom ou raison sociale : [à compléter]
Statut : micro-entreprise [à compléter]
Adresse : [à compléter]
Adresse électronique de contact : [à compléter]
Numéro d'immatriculation : [à compléter]

Hébergement des mises à jour
Les fichiers de mise à jour sont publiés sur GitHub (GitHub, Inc.). L'échange de messages entre les deux téléphones passe par un relais ntfy ; les messages y sont chiffrés et illisibles pour le relais.

Propriété
L'application, son nom et ses textes appartiennent à l'éditeur. Toute copie ou diffusion sans accord est interdite.
""".trimIndent()

    val CONDITIONS = """
1. Objet
Cette application permet à un parent de rester en lien avec son enfant : voir sa position, voir le temps passé sur les applications, lui envoyer un message, faire sonner ou verrouiller son téléphone, l'appeler en visio.

2. Usage familial
L'application est réservée à un parent ou tuteur légal, pour son propre enfant mineur. Elle ne doit jamais servir à surveiller une autre personne sans son accord.

3. Transparence
Sur le téléphone de l'enfant, une notification permanente indique que l'application est active. Le parent s'engage à expliquer à l'enfant ce que fait l'application. Elle ne se cache pas.

4. Ce que l'application ne fait pas
Elle ne bloque pas les applications et ne limite pas le temps d'écran : pour cela, utilise Google Family Link. Elle ne remplace pas la présence et le dialogue avec l'enfant.

5. Responsabilité
Le service est fourni tel quel. L'éditeur ne garantit pas que les messages arrivent toujours à temps (réseau, économie de batterie du téléphone) et ne peut être tenu responsable d'un défaut de transmission. Ne compte pas sur cette application seule pour une situation d'urgence : appelle les secours.

6. Prix et licence
[à compléter : conditions de vente et de licence, si l'application est vendue]

7. Droit applicable
Droit français. [à compléter : tribunal compétent / médiateur de la consommation]
""".trimIndent()

    val CONFIDENTIALITE = """
Données traitées
- La position du téléphone de l'enfant (à la demande du parent, et à intervalle régulier réglé par le parent).
- Le temps passé aujourd'hui sur chaque application et le niveau de batterie.
- L'état des autorisations de l'application.
- Les messages et commandes envoyés par le parent.

Où sont les données
- Sur les deux téléphones, dans l'espace privé de l'application.
- Entre les deux téléphones : chaque message est chiffré (AES-256-GCM) avec un secret que seuls les deux téléphones connaissent. Le relais ntfy (ntfy.sh) ne voit que du texte illisible et conserve les messages environ 12 heures.
- Aucun compte, aucune publicité, aucun outil de mesure d'audience, aucune donnée envoyée à l'éditeur.

Durée de conservation
- L'historique des positions est gardé sur le téléphone du parent. Le parent peut le vider à tout moment.
- Retirer l'enfant dans l'application du parent, ou désinstaller l'application, supprime les données locales.

Droits
Conformément au RGPD, le parent peut accéder aux données, les corriger ou les effacer depuis l'application. Pour toute demande : [à compléter : adresse de contact].

Enfants
L'application est conçue pour un usage par un parent avec son enfant mineur, et sous sa responsabilité.
""".trimIndent()

    val COMMENT_ENFANT = """
1. Ton parent te donne un code de 34 caractères. Tape-le dans « Associer à mon parent ».
2. Ouvre « Autorisations » et accepte chaque ligne, une par une : position, accès à l'usage, administrateur de l'appareil, affichage par-dessus les autres applications, notifications, économie de batterie.
3. Une notification « Famille est active » reste affichée : c'est normal, elle te montre que l'application fonctionne.
4. Ton parent peut alors voir ta position et le temps passé sur les applications, t'envoyer un message, faire sonner ton téléphone, le verrouiller ou t'appeler en visio.
5. Pour le temps d'écran et le blocage des applications, ton parent utilise Google Family Link.
""".trimIndent()

    val COMMENT_PARENT = """
1. Touche « Ajouter un enfant », écris son prénom : l'application crée un code de 34 caractères.
2. Sur le téléphone de l'enfant, installe « Famille Enfant » et saisis ce code dans « Associer à mon parent ». Fais-le avec lui : c'est un moment pour lui expliquer ce que fait l'application.
3. Sur son téléphone, passe par « Autorisations » pour tout activer (position, accès à l'usage, administrateur, notifications, économie de batterie).
4. Reviens ici : tu vois sa position, son temps d'écran du jour et sa batterie.
5. Boutons d'action : demander la position, verrouiller l'écran, envoyer un message, faire sonner, appeler en visio.
6. Pour limiter le temps par application, utilise « Ouvrir Google Family Link ».
""".trimIndent()
}
