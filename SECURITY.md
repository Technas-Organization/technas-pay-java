# Sécurité

## Signaler une faille

Écrivez à **contact@technas.fr**, objet « Sécurité : technas-pay-java ». N'ouvrez pas d'issue publique.
Nous accusons réception sous 3 jours ouvrés et vous tenons informé jusqu'au correctif.

Merci de ne pas tester sur les comptes d'autres partenaires ni en mode réel (`tps_live_`) : le mode test (`tps_test_`) suffit à tout reproduire.

## Ce que ce SDK garantit, et comment c'est vérifié

| Garantie | Où | Preuve |
|---|---|---|
| Signature des avis vérifiée sur le corps brut, comparaison en temps constant (`MessageDigest.isEqual`) | `webhook/Signature.java` | `SecurityTest.comparaisonEnTempsConstant` |
| Avis falsifié, rejoué, trop vieux (300 s) ou daté du futur : refusé, aucune fonction appelée | idem | `avisFalsifieRefuse`, `avisRejoueOuTropVieuxRefuse` |
| JSON strict : modèle en arbre seulement (aucune classe instanciée depuis la donnée reçue), pas de NaN, pas de clé en double, rien après la valeur, profondeur 64 | `internal/Json.java` | `corpsJsonPiegeRefuse`, `reponseIllisibleNonInterpretee` |
| Montants en `BigDecimal`, jamais en `double` | `model/` | relu ; SpotBugs |
| Casse ASCII seulement sur les adresses, devises et en-têtes (aucune transformation Unicode) | `internal/Ascii.java` | FindSecBugs `IMPROPER_UNICODE` |
| TLS toujours vérifié : aucun `TrustManager`, `HostnameVerifier` ni `SSLContext` dans le code ; aucune redirection suivie ; délais de connexion et de réponse posés | `http/JdkTransport.java` | `tlsJamaisDesactive`, `aucuneRedirectionSuivieEtDelaiPose` |
| La clé ne part que dans l'en-tête `Authorization`, jamais vers une adresse en clair, jamais dans une URL | `TechnasPay.java` | `cleJamaisVersUneAdresseEnClair`, `leSecretNApparaitNullePart` |
| Le secret n'apparaît dans aucun message, `toString` ni exception ; le client ne se sérialise pas | idem | `leSecretNApparaitNullePart`, `leClientNeSeSerialisePas` |
| Jamais de désérialisation Java, de réflexion sur la donnée reçue ni de typage polymorphe Jackson | tout `src/main` | `tlsJamaisDesactive` (liste des appels interdits) |
| Corps reçu par l'adaptateur Servlet borné à 64 Ko, lu brut | `servlet/TechnasPayServlet.java` | `ServletTest.parametresDeRequeteEtCorpsTropGrand` |
| Une dépendance d'exécution : `jackson-databind` | `pom.xml` | OSV-Scanner en CI, Dependabot |

En CI, à chaque modification : compilation avec tous les avertissements en erreur (`-Xlint:all -Werror`), SpotBugs au plus strict (effort max, seuil bas) avec FindSecBugs, OSV-Scanner sur les dépendances, recherche de secrets dans l'historique (TruffleHog). Dependabot surveille les dépendances Maven et les actions.
