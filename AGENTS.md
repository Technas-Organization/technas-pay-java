# technas-pay-java : règles pour un agent de code

Consignes pour un assistant de code. Appliquez-les dans l'application de l'utilisateur, pas dans ce dépôt. Le README détaille chaque fonction ; des exemples compilés sont dans [`examples/`](examples) (Spring Boot en Java et en Kotlin).

Même API, mêmes règles que les SDK Node (`@technas/pay`), Python, PHP et Dart : mêmes noms de méthodes, en Java (`pay.askForPayment(PaymentRequest.builder()…build())`, accesseurs `payment.checkoutUrl()`). Kotlin s'en sert tel quel.

## Avant de commencer

1. Java 17 ou plus récent. Côté SERVEUR uniquement : jamais dans une app Android ni dans du code envoyé au navigateur.
2. `TECHNAS_PAY_SECRET` doit exister dans l'environnement du serveur (variable d'environnement, coffre, jamais un fichier commité) et commencer par `tps_test_`. S'il manque, demandez-le à l'utilisateur. **N'en inventez pas, ne l'affichez pas, ne le loggez pas, ne le commitez pas.**
3. Repérez l'entité de commande : un identifiant, un montant calculé côté serveur, un état « payée », et une colonne pour garder `payment.id()`.
4. Un seul `TechnasPay` pour l'application (un `@Bean` Spring, un singleton) : il est sans état et sûr entre threads.

## Installer

Dépendance JitPack (voir le README) :

```xml
<repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
<dependency>
  <groupId>com.github.Technas-Organization</groupId>
  <artifactId>technas-pay-java</artifactId>
  <version>v0.8.0</version>
</dependency>
```

## Quelle méthode pour quoi

| Besoin | Appel |
|---|---|
| Faire payer une commande | `pay.askForPayment(PaymentRequest.builder().amount(5000).reference(Status.retryReference(orderId, attempt)).customer(Customer.of(email, name)).returnUrl(…).build())`, puis rediriger (303) vers `payment.checkoutUrl()` |
| Savoir combien le client paiera et combien vous recevrez, avant de créer le paiement | `pay.quote(QuoteRequest.builder().amount(200).country("TG").fees("customer").build())` (ou `pay.getFees`) → `customerTotal()` (à afficher au client), `processingFee()`, `commission()`, `netAmount()`, `networks()` |
| Afficher un prix FIXE (le même quel que soit le réseau) et recevoir au moins X | `pay.displayPrice(DisplayPriceRequest.builder().receive(1000).countries(List.of("TG", "BJ")).roundTo(50).build())` → `byCountry().get(pays).price()`, `everywhere().price()` ; puis `askForPayment` avec `.amount(price).fees("partner")` (jamais `"customer"`) |
| La grille des frais par pays et réseau (back-office) | `pay.getRates(country)` (ou `pay.rates`) → `countries().get(i).networks()` : `percent()`, `minimumAmount()`, `operatorFeeEstimate()`, `example1000().customer()/partner()` ; commission : `countries().get(i).commission()` |
| Faire payer TOUS les frais au client (vous recevez exactement `amount`) | `.fees("customer")` dans `PaymentRequest` (ou réglage du compte) ; `"partner"` : le client paie `amount`, frais compris |
| Lister les moyens et réseaux ouverts, ce qui est activé, l'ordre | `pay.getPaymentMethods(country)` ; constantes `TechnasPay.PAYMENT_METHODS`, `TechnasPay.NETWORKS` (une constante par valeur : `PaymentMethod.CARD`, `Network.MIXX`…) |
| Activer / éteindre des moyens et réseaux, fixer l'ordre d'affichage | `pay.setPaymentMethods(PaymentMethodsRequest.builder().methods(…).networks(…).networkOrder(…).build())` ; par paiement : `.networks(List.of(Network.MIXX))` dans `PaymentRequest` |
| Savoir qu'elle est payée | avis signé : `pay.handleWebhook(corpsBrut, signature, WebhookHandlers.builder().onCompleted(…).build())` |
| Rattraper un avis manqué | `pay.getPaymentByReference(reference)` |
| Le client change d'offre ou revient plus tard | `pay.cancelPayment(ancienId)`, puis nouvel essai `Status.retryReference(factureId, n + 1)` ; au retour, `pay.getPayment(dernierPaymentId)` d'abord (voir la section dédiée) |
| Savoir si on peut relancer | `onExpired`, ou `Status.canRetry(pay.getPayment(id).status())` |
| Rembourser | `pay.refund(paymentId)` ou `pay.refund(paymentId, amount, fees, reference)` |
| Noter un remboursement fait hors Technas | `pay.recordExternalRefund(paymentId, 2000, "Flooz", null, null, null)` |
| Recharger le solde | `pay.topUpBalance(20000, "XOF", returnUrl)`, ouvrir `checkoutUrl()` ; `onTopUp` |
| Solde, reversements, historique | `pay.getBalance()`, `pay.getSettlements()`, `pay.listTransactions(20, null, null)` ; `onSettlement` |
| Hors zone CFA | `pay.getFxMode()`, `pay.setFxMode("local", true, null, null)` (après accord explicite) |
| Pays où vendre | `pay.setCountryRules(null, List.of("NG"))`, ou `.allowedCountries(…)` / `.excludedCountries(…)` par paiement |
| Numéro de reversement (administrateur) | `pay.getPayoutNetworks("TG")`, `pay.setPayoutDestination("TG", "moov_tg", "+228…", "Nom")` |
| Payer par carte | `.method("card").amount(new BigDecimal("12.50")).currency("EUR")` après `pay.getCardAccount().canAcceptPayments()` |
| Ouvrir le compte carte | `pay.startCardOnboarding(returnUrl, refreshUrl, "TG", null, null)`, rediriger le RESPONSABLE vers `url()` |
| Seuil d'identité (marchand) | `pay.getAccountKyc()` : `stage()`, `collectedXof()`, `thresholdXof()`, `deadline()`, `consoleUrl()` ; avis `onKycRequired` (`event.kyc()`) |
| Tableau de bord carte | `pay.getCardDashboardLink()` (le responsable s'y connecte avec ses identifiants) |
| Tester la route d'avis | `pay.createTestWebhook("payment.completed", "42-1", BigDecimal.valueOf(5000), "XOF")` (corps et en-tête) |
| Jouer le payeur en test | `pay.simulateTestPayment(id, "completed" \| "failed" \| "expired")` |

## Étapes

1. `TechnasPay pay = TechnasPay.fromEnv();` (lit `TECHNAS_PAY_SECRET` au premier appel).
2. Action « payer » : relire la commande en base, `askForPayment`, garder `payment.id()`, rediriger vers `checkoutUrl()`.
3. Route d'avis `POST` : corps BRUT (`@RequestBody String raw`), puis `handleWebhook` ; répondre `r.status()` avec `r.body()` en JSON. Spring Security : exclure cette route du CSRF et de l'authentification (la signature l'authentifie). Modèles : `examples/`.
4. Test : poster `pay.createTestWebhook(...)` sur la route, puis le lancer.

## App mobile (Flutter)

Ne réécrivez pas de routes : `pay.appRoutes(authorize, resolveOrder).returnUrl(…).webhooks(…).card(?).authorizeAdmin(?).build()`, montées sous `/api/technas-pay` par `TechnasPayServlet.handle(routes, req, res, "/api/technas-pay")` (Spring MVC, Quarkus, Tomcat, Jetty). Hors Servlet : `routes.handle(new AppRequest(…))`. `resolveOrder(orderId, user)` relit la commande EN BASE et rend `null` si elle n'est pas à cet utilisateur. N'y mettez jamais un montant reçu de l'app. Le client change d'offre : l'app appelle `POST /payments/{id}/cancel` sur l'essai affiché, puis `POST /payments` ; si la commande a changé de montant, `POST /payments` annule lui-même l'ancien essai encore ouvert. Côté app : `TechnasPayClient(baseUrl: '…/api/technas-pay')`.

## Obligatoire

- **`amount`** : en unité principale (`5000` = 5 000 FCFA, `new BigDecimal("12.50")` = 12,50 €, jamais × 100, jamais un `double`), calculé côté serveur. Entier pour le mobile money ; décimales seulement par carte.
- **`reference`** : l'identifiant unique de la commande.
- **`customer.email`** et **`customer.name`** : de vrais champs du client, jamais inventés.
- **`returnUrl`** : la page de retour, en https en production.
- Le reste est facultatif. Passez `customer.country` et `customer.phone` si l'application les connaît : ils évitent au client de choisir son pays.

## La devise est automatique

- **Ne passez pas `currency`** si l'application vend en francs CFA.
  - La devise suit le pays du client : TG → XOF, CM → XAF, pour le même montant.
  - Sans pays, le client choisit le sien sur la page de paiement.
- **Ne fixez pas `defaultCurrency`** : il empêcherait ce choix.
- **Hors zone, les montants peuvent avoir des centimes** (`121.96` GHS) : stockez-les en décimal, ne les arrondissez pas.
- Dans le webhook, `event.payment().originalAmount()` est le montant demandé en F CFA ; `amount` et `currency` sont ce qui a été payé.

## Où vendre (tout est facultatif)

- **Le client choisit son pays** : sans `currency` ni `customer.country`, la page le lui demande.
- **Pays fixé** : `customer.country`.
- **Pour tout le compte** : `pay.setCountryRules(allowed, excluded)`. Codes ISO à deux lettres.
- **Pour un seul paiement** : `.allowedCountries(…)` / `.excludedCountries(…)` dans `PaymentRequest`.
- **Devise imposée** : `.currency("XAF")` ; ajoutez `.askCountry(true)` pour que le client choisisse parmi les pays de cette devise.
- **Pays refusé** : `country_not_allowed`. Ne contournez pas ce refus : c'est le choix du marchand.

## Clients hors zone franc CFA : une option à risque, décidée par l'utilisateur

- **Montant** : il reste en F CFA, ne passez pas `currency`.
- **C'est une option** : `pay.getFxMode()` dit laquelle est active, quels pays sont ouverts, et ce qui manque à chacun (`reason`).
- **Technas ne couvre aucun risque de change.** Avant d'activer `local` ou `converted` :
  1. lisez `pay.getFxMode()` → `risks[mode]` (et `risks.markup` si un supplément est prévu) ;
  2. **montrez ce texte à l'utilisateur, en entier, et expliquez-le simplement** : ce qu'il peut perdre, dans quelle monnaie il sera payé, quel numéro de reversement il lui faut ;
  3. attendez son accord explicite, en toutes lettres ;
  4. seulement ensuite, appelez `pay.setFxMode(mode, true, markupPct, null)`.
- **N'activez jamais une option de vous-même**, et ne passez jamais `acceptRisk: true` sans cet accord.
- **`local`** : le solde est tenu et reversé dans la monnaie du client. Il faut un numéro de reversement **confirmé** dans cette monnaie (`setPayoutDestination`, réservé à un administrateur), sinon le pays reste fermé (`no_payout_number`).
- **`converted`** : PAS ENCORE OUVERT. `setFxMode("converted", …)` lève `fx_mode_unavailable` : ne le proposez pas. Seules les options de `pay.getFxMode()` → `availableModes` existent (aujourd'hui `none` et `local`).
- **`markupPct`** (0 à 20) : supplément payé par le client, affiché, qui revient au marchand.
- **Ne promettez jamais** un pays que `getFxMode()` ne donne pas `available: true`.
- **Webhooks** :
  - branchez `onFxModeUpdated` : un changement d'option que l'utilisateur n'a pas fait signale une clé compromise ;
  - branchez `onBalanceConverted` en mode `converted`.

## Sécurité

- **Le secret reste côté serveur.**
  - Jamais dans un template rendu au navigateur, une variable JavaScript, une app mobile, un `application.properties` commité ni un endpoint Actuator (`/env`, `/configprops`).
  - `TechnasPay.toString()` n'affiche pas le secret ; ne l'écrivez pas vous-même dans un log.
  - Jamais de secret dans une page, une réponse JSON, une URL, un log ou un commit.
- **Vérifiez la signature de chaque webhook avec le bon secret.**
  - `handleWebhook` (ou `verifyWebhook`) le fait, avec le secret de `TechnasPay.of(…)` ou `TECHNAS_PAY_SECRET`.
  - Un secret `tps_test_` ne vérifie que les webhooks de test, un secret `tps_live_` que ceux du réel.
  - Le serveur qui reçoit les webhooks réels doit avoir le secret `tps_live_`.
  - Ne contournez jamais un refus de signature (réponse 400).
- **Conservez le corps brut** : `@RequestBody String raw` (Spring), jamais un objet désérialisé puis re-sérialisé avant la vérification.
- **Seul le webhook prouve le paiement.** Ne marquez jamais une commande payée depuis la page `returnUrl`.
- **Réservez à un administrateur** les remboursements et le numéro de reversement. Ne les exposez jamais sur une page publique.

## La règle de relance (à respecter strictement)

**Une référence = un essai. Relancez avec une NOUVELLE référence seulement après `payment.expired` (ou `getPayment` → `expired`), jamais sur `failed`.**

- Ne prenez pas l'identifiant de commande tel quel comme `reference` : `Status.retryReference(orderId, tentative)` compose `"orderId-tentative"` ; gardez la tentative en base, pour pouvoir en créer une nouvelle.
- Rejouer la référence d'un paiement expiré lève `payment_expired` (409) : ne réessayez pas le même appel, créez une nouvelle référence.
- Après expiration, `checkoutUrl()` vaut `null` : n'envoyez jamais l'ancien lien au client.

- `failed` n'est **pas** définitif : le client peut encore payer sur la même page pendant une heure, et `onCompleted` arriver ensuite. Dans `onFailed`, ne marquez pas la commande annulée, n'envoyez pas de nouveau lien.
- `expired` est définitif : le lien est fermé, Technas a vérifié chez l'opérateur que rien n'a été payé ni n'est en cours. `Status.canRetry(payment.status())` vaut `true` seulement là.
- La même référence rend le même paiement expiré : générez-en une nouvelle (ex. `orderId + "-2"`) et gardez le nouveau `payment.id()`.
- Expliquez cette règle à l'utilisateur : un client relancé sur `failed` pourrait payer deux fois.

## Le client change d'offre ou revient plus tard

Le client choisit une offre, ouvre la page de paiement, ne paie pas, puis revient choisir une AUTRE offre, ou revient le lendemain. Rien n'est payé : ne répondez jamais « un paiement existe déjà ».

- **Une référence = un essai.** Nouvelle offre, ou retour après expiration : un NOUVEL essai, `Status.retryReference(factureId, n + 1)`, `n` gardé en base. Réutiliser la référence d'un autre montant lève `reference_conflict` (409) ; celle d'un essai expiré, `payment_expired` (409). Les routes toutes faites (`appRoutes`) le font seules.
- **Une facture n'est payée QUE sur `payment.completed`** (`onCompleted`). Jamais sur `returnUrl`, jamais à l'ouverture de la page.
- **Un essai non payé ne bloque jamais un nouvel essai.** Même offre et essai `pending` ou `failed` : renvoyez le client sur la même page (`checkoutUrl()`). Autre offre : ANNULEZ l'ancien essai (`pay.cancelPayment(ancienId)`), puis créez le nouveau avec une NOUVELLE référence ; ne refusez pas. `expired` : marquez la tentative expirée et laissez recommencer.
- **`cancelPayment` refuse quand c'est trop tard** : `payment_already_completed` (le client a payé l'ancienne offre : relisez le paiement, ne créez rien) ; `payment_in_progress` (une demande est en cours chez l'opérateur : ne créez PAS de second paiement, attendez l'avis de l'issue). Un paiement annulé reste `expired` (`canceledAt()`, `cancelReason()`), et son avis `payment.expired` porte `reason()` = `canceled` (`timeout` pour une échéance passée).
- **Au retour du client**, vérifiez `pay.getPayment(dernierPaymentId)` avant de dire quoi que ce soit : votre base peut avoir manqué un avis.
- **Un ancien essai payé APRÈS le nouveau** (le client a payé les deux pages) : n'activez rien deux fois, remboursez l'essai en trop avec `pay.refund(paymentId)`.

```java
Payment payer(Facture f, long montant) {
    if (f.dernierPaymentId() != null) {
        Payment dernier = pay.getPayment(f.dernierPaymentId()); // jamais la base seule
        if ("completed".equals(dernier.status())) {
            throw new DejaPayee(f.id());
        }
        boolean memeOffre = dernier.amount().compareTo(BigDecimal.valueOf(montant)) == 0;
        if (memeOffre && !"expired".equals(dernier.status()) && dernier.checkoutUrl() != null) {
            return dernier; // pending ou failed : même page, le client peut encore payer
        }
        if (!"expired".equals(dernier.status())) {
            // Autre offre : l'ancien lien est fermé d'abord. payment_in_progress ou
            // payment_already_completed remontent : on ne crée alors AUCUN second paiement.
            pay.cancelPayment(dernier.id());
        }
    }
    int essai = factures.essaiSuivant(f.id()); // n + 1, gardé en base
    Payment p = pay.askForPayment(PaymentRequest.builder()
            .amount(montant)
            .reference(Status.retryReference(f.id(), essai))
            .customer(Customer.of(f.email(), f.nom()))
            .returnUrl("https://votre-site.com/merci")
            .metadata(Map.of("facture_id", f.id()))
            .build());
    factures.retenirPaiement(f.id(), p.id());
    return p;
}

// Dans onCompleted : la facture déjà payée par un AUTRE paiement → rembourser celui-ci.
.onCompleted(e -> {
    String id = e.payment().metadata().get("facture_id").toString();
    if (factures.estPayeePar(id, e.payment().id()) || !factures.estPayee(id)) {
        factures.marquerPayee(id, e.payment().id()); // idempotent
    } else {
        pay.refund(e.payment().id()); // second paiement : rien n'est activé deux fois
    }
})
```

## Frais : qui les paie, combien paie le client

- **`.fees("customer")`** : le client paie votre prix + les frais du réseau de paiement + la commission Technas ; vous recevez EXACTEMENT `amount`. **`.fees("partner")`** : le client paie `amount`, frais compris. Sans `fees` : le réglage du compte. Ne choisissez pas à la place de l'utilisateur : demandez-lui.
- **Avant de créer le paiement**, `pay.quote(QuoteRequest.builder().amount(montant).country(pays).fees(mode).build())` (alias `pay.getFees`) rend `customerTotal()` : c'est CE montant qu'il faut afficher au client, jamais un calcul fait à la main. Rien n'est créé.
- `networks()` donne chaque réseau (`networkName()`, `customerTotal()`, `operatorFeeEstimate()`). `operatorFeeEstimate()` est une estimation des frais propres de l'opérateur, hors Technas : présentez-la comme « environ », jamais comme une promesse.
- Le paiement et l'avis portent `customerAmount()`, `feesPaidBy()`, `customerTotal()`, `processingFee()` (`null` : inconnu, serveur ancien ou carte). `amount()` reste le prix du marchand.
- `amount_too_low` : `e.details().get("minimumAmount")` est le plus petit montant possible.
- Par carte : `"partner"` seulement.

## Webhooks

- **Ils peuvent arriver plusieurs fois** : vos fonctions doivent le supporter. Vérifiez l'état en base, ou gardez `event.id()`.
- **Ne rattrapez pas leurs erreurs pour répondre 200.** Une erreur doit remonter pour que le webhook soit relivré.
- **Deux paiements pour une commande** : une cliente peut payer par mobile money puis par carte (deux séries de références). Dans `onCompleted`, si la commande est déjà payée par un AUTRE `payment.id()`, ne l'ignorez pas : signalez-la au marchand pour remboursement.
- **`onFailed` n'est pas définitif** : `onCompleted` peut arriver après.
- **`onExpired` est définitif** : seul moment où relancer le client (nouvelle référence).
- **`onRefund`** : prévenez le client, par e-mail ou SMS. Technas ne lui écrit pas.
  - `refund.completed` : l'argent est arrivé, `sentAmount` sur son téléphone.
  - `refund.failed` : rien n'est parti et votre solde est recrédité. Proposez un autre moyen, ou refaites le même appel `refund` : il est permis après un échec.
- **`onSettlement`** : l'issue d'un reversement de votre solde vers votre numéro mobile money, dans `event.settlement()`.
  - `settlement.completed` : `netAmount` est arrivé sur le numéro. `amount` a quitté le solde, `fee` (frais du versement, à votre charge) fait la différence.
  - `settlement.failed` : rien n'est arrivé et `amount` est recrédité sur votre solde.

## Paiement par carte (`.method("card")`)

Ce n'est PAS le même parcours que le mobile money. Ne le proposez que si l'utilisateur le demande.

1. **Un compte carte d'abord, une seule fois.** Ajoutez dans les réglages de l'application un bouton « Activer le paiement par carte », réservé au responsable du compte. Au clic, côté serveur :
   ```java
   CardOnboardingLink link = pay.startCardOnboarding(site + "/reglages/carte?fin=1", site + "/reglages/carte?relance=1", "FR", null, null);
   return ResponseEntity.status(303).location(URI.create(link.url())).build();
   ```
   - `country` est le pays de l'UTILISATEUR (pas de ses clients). Demandez-le-lui, ne le devinez pas.
   - Le lien expire en quelques minutes : générez-le au clic, ne l'envoyez jamais par e-mail.
   - Sur `refreshUrl`, rappelez `startCardOnboarding` et redirigez à nouveau.
   - Sur `returnUrl`, affichez `pay.getCardAccount()` : `status` dit où il en est (`onboarding`, `under_review`, `restricted`, `enabled`) et `requirements` ce qui manque.
   - Le responsable y donne son identité et sa banque sur la page sécurisée de notre partenaire carte, qui lui fera accepter son contrat. Ni vous ni Technas ne voyez son IBAN.
2. **Proposer la carte seulement si `canAcceptPayments` est vrai.** Gardez l'état en base via `onCardAccountUpdated`, ou relisez `getCardAccount()`. Sinon, ne montrez que le mobile money.
3. **Faire payer :** `PaymentRequest.builder().method("card").amount(new BigDecimal("49.90")).currency("EUR")…`, puis redirection vers `payment.checkoutUrl()`. `currency` est obligatoire par carte. Le reste est identique : même webhook `onCompleted`, même règle « une référence = un essai », même `payment.expired` au bout d'une heure.
4. **L'argent va directement sur le compte carte de l'utilisateur**, commission Technas déjà retirée. Il n'apparaît PAS dans `getBalance()` et ne passe pas par les reversements. Les virements vers sa banque se règlent dans son tableau de bord carte (`getCardDashboardLink()`).
5. **Rembourser :** le même `pay.refund(paymentId, amount, null, null)`. La carte est remboursée depuis son compte carte (`refund.kind()` vaut `"card"`), sans toucher au solde Technas ; `fees` est sans objet. La commission n'est pas rendue.
6. **Erreur `card_account_not_ready` (409)** : le compte n'encaisse pas encore. `e.details().get("accountStatus")` dit pourquoi. Ne réessayez pas : proposez le mobile money, ou renvoyez le responsable vers `startCardOnboarding`.
7. **En test** (`tps_test_`), la carte de test `4242 4242 4242 4242`, une date future et n'importe quel code valident le paiement ; l'inscription de test accepte des données fictives.

## Rembourser

- **Appel** : `pay.refund(paymentId)` rembourse tout ce qui reste ; `pay.refund(paymentId, BigDecimal.valueOf(2000), null, null)` rembourse une partie.
- **Destination** : l'argent repart au numéro qui a payé, jamais à un autre.
- **Plafond** : le total remboursé ne dépasse jamais le montant payé.
- **Commission** : la commission Technas n'est pas rendue.
- **Frais du renvoi**, choisis à chaque appel avec `fees` :
  - `"partner"` (défaut) : le client reçoit tout, les frais sont pris en plus sur votre solde ;
  - `"customer"` : les frais sont retirés de ce qu'il reçoit (`sentAmount`).
- **Idempotence** : rejouer le même appel ne rembourse pas deux fois. Pour deux remboursements partiels identiques et voulus, donnez à chacun sa `reference`, par exemple l'identifiant de la décision.
- **Statut** : la réponse peut être `pending`. Le résultat arrive dans `onRefund`, ou par `pay.getRefund(id)`.
- **Solde insuffisant** : l'erreur `insufficient_balance` donne `e.details().get("missingAmount")`. Rechargez avec `pay.topUpBalance`, puis rappelez `refund`.
- **Remboursement fait hors Technas** (Flooz, T-Money, espèces) : `pay.recordExternalRefund(paymentId, amount, method, note, recordedBy, reference)`.
  - Aucun argent ne bouge, le solde ne change pas.
  - Il compte dans le même plafond que `refund`.
  - Même idempotence, webhook `refund.recorded`.
  - N'appelez jamais `refund` pour un remboursement déjà fait à la main : le client serait remboursé deux fois.
- **Total remboursé** : `payment.refundedAmount()` additionne les deux sortes.

## Seuil d'identité

- **La règle** : en réel, un compte sans pièce d'identité vérifiée voit ses NOUVEAUX paiements mobile money refusés (`kyc_required`, 409) au-delà d'un montant encaissé en cumul ou d'un nombre de jours après son premier paiement réel encaissé (aujourd'hui 1 000 000 F CFA ou 30 jours).
- **Les chiffres viennent de l'API** : `pay.getAccountKyc()` (`thresholdXof()`, `graceDays()`, `collectedXof()`, `deadline()`). Ne les écrivez jamais en dur.
- **Rien de ce qui existe n'est bloqué** : paiement en cours, remboursement, reversement, lecture. La carte n'est pas concernée.
- **Montrez l'état au MARCHAND** (réglages, tableau de bord), jamais au client final. `stage()` vaut `ok`, `soon` (préavis), `blocked`, `verified` ou `not_applicable`.
- **Branchez `onKycRequired`** : l'avis `account.kyc_required` arrive à 80 % du seuil (`reason` `amount_80`), 7 jours avant l'échéance (`deadline_7d`), puis au premier refus (`blocked`). Prévenez le responsable avec le lien `event.kyc().consoleUrl()`, où il dépose sa pièce.
- **Sur `kyc_required`** : proposez la carte, ou dites au client que le paiement mobile money n'est pas disponible pour le moment. Ne réessayez pas.

## Erreurs

- `card_account_not_ready` (409) : paiement par carte demandé sans compte carte actif. Voir « Paiement par carte ».
- `card_payments_unavailable` (503) : la carte n'est pas encore ouverte chez Technas. Proposez le mobile money.
- `currency_required` : `.method("card")` sans `currency`.

`e.code()`, avec `e.retryable()` (vrai : refaites **le même appel**, mêmes valeurs) et `e.details()`.

| `e.code()` | Que faire |
|---|---|
| `invalid_secret` | Variable absente ou mal copiée : demandez à l'utilisateur de la vérifier |
| `invalid_api_key` | Secret refusé : contacter Technas |
| `invalid_request` | Corriger le champ indiqué dans le message |
| `invalid_amount`, `amount_too_low` | Montant entier positif, assez grand pour couvrir les frais : au moins `e.details().get("minimumAmount")` (`amount_too_low`) |
| `fees_customer_unavailable` | `.fees("customer")` impossible ici (carte, hors franc CFA) : utiliser `"partner"` |
| `currency_not_enabled` | Devise non ouverte sur ce compte : contacter Technas |
| `currency_conversion_unavailable` | Client hors zone CFA, mode de change `none` : ne pas proposer ce pays ; voir `getFxMode()` |
| `country_not_allowed` | Le marchand ne vend pas dans ce pays : ne pas le proposer |
| `method_not_enabled` | Moyen éteint sur ce compte : ne pas le proposer ; voir `getPaymentMethods(null)` |
| `network_not_enabled` | Réseau éteint sur ce compte : ne pas le proposer ; voir `getPaymentMethods(null)` |
| `network_not_available` | Réseau pas ouvert dans ce pays : ne pas le proposer ; voir `getPaymentMethods(country)` |
| `country_not_available` | Pays pas prêt : `e.details().get("reason")` dit quoi (`no_payout_number`…) |
| `risk_not_accepted` | Montrer `e.details().get("risk")` à l'utilisateur ; n'activer qu'avec son accord explicite |
| `fx_mode_unavailable`, `fx_channel_unavailable` | Mode `converted` pas ouvert : proposer `local` (avec l'accord de l'utilisateur sur le risque) ou rester en `none` |
| `quote_expired` | Le client rechoisit son pays sur la page, rien à faire côté code |
| `reference_conflict` | Même référence, autres valeurs : ne pas réutiliser la référence |
| `payment_already_completed` | `cancelPayment` : le paiement est déjà payé. Relire le paiement ; ne créer aucun nouvel essai |
| `kyc_required` | Pièce d'identité à déposer : `e.details().get("consoleUrl")`, `reason` (`amount` ou `duration`). Proposer la carte ; ne pas réessayer |
| `contract_required` | Le responsable doit accepter le contrat dans la console (`e.details().get("version")`) ; ne pas réessayer |
| `payment_in_progress` | Réessayer dans une minute, même appel. Sur `cancelPayment` : ne créez PAS de second paiement, attendez l'avis de l'issue |
| `payment_not_found`, `refund_not_found` | Mauvais identifiant ou autre compte |
| `payment_expired` | Cette référence est un essai expiré : nouveau paiement avec une NOUVELLE référence, jamais le même appel |
| `payment_not_refundable` | Seul un paiement `completed` se rembourse |
| `refund_exceeds_payment` | Rembourser au plus `e.details().get("refundableAmount")` |
| `refund_conflict` | Cette `reference` de remboursement a déjà servi avec d'autres valeurs |
| `insufficient_balance` | Recharger au moins `e.details().get("missingAmount")` avec `topUpBalance`, puis rappeler |
| `payer_unknown`, `refund_network_unavailable` | Remboursement impossible par Technas : rembourser le client autrement, prévenir Technas |
| `provider_unavailable`, `unavailable`, `network_error` | Panne passagère : refaire le même appel |
| `rate_limited` | Ralentir, réessayer |

## Vérifier l'intégration

Avec un secret `tps_test_`, aucun argent ne circule.

1. Lancez l'application et déclenchez un paiement : `checkoutUrl` ouvre une page de test (choix du pays, opérateurs, boutons « réussi » ou « refusé »).
2. Cliquez sur « Simuler un paiement réussi », puis contrôlez côté serveur avec `pay.getPaymentByReference(reference)`.
   Testez aussi la relance : sur un autre paiement, « Simuler un refus » (rien ne doit être relancé), puis « Simuler l'expiration du lien » (`onExpired` doit relancer avec une nouvelle référence). Sans navigateur : `pay.simulateTestPayment(id, "failed")` puis `"expired"`. Un paiement de test non payé expire aussi tout seul au bout d'une heure.
3. Remboursez avec `pay.refund(payment.id())` : en test, le remboursement est terminé aussitôt, avec des frais réalistes, et aucun argent ne part. Si le solde manque, `pay.topUpBalance` ouvre une page de test pour le recharger.
4. Le webhook n'arrive sur votre poste que si Technas connaît une adresse publique. Sinon, testez la route avec `pay.createTestWebhook`, et demandez à l'utilisateur de transmettre à son contact Technas l'adresse d'un tunnel.

## Passer en réel

Remplacez seulement le secret par `tps_live_…`. `returnUrl` doit alors être en https. Cette étape revient à l'utilisateur et à Technas : ne la faites pas de votre propre initiative.
