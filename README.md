# technas-pay-java (Java, Kotlin)

Encaissez des paiements mobile money et par carte via Technas depuis un serveur Java ou Kotlin : Spring Boot, Quarkus, Micronaut, Ktor, ou Java sans framework.

**Technas Pay réunit le mobile money et la carte dans un seul SDK, du serveur jusqu'à l'app Flutter.** Carte : 1 % (plus les frais de votre compte carte, selon le pays : environ 1,5 % + 0,25 € en Europe). Mobile money : 2 %, et vous choisissez qui paie les frais : le client (vous recevez exactement votre prix) ou vous (le client paie votre prix) ; `pay.quote` dit les montants avant le paiement. Sans abonnement, et une clé de test sans papier pour commencer.

Devises et pays, pièces demandées, versements, comparaison avec les autres prestataires : [le hub Technas Pay](https://github.com/Technas-Organization/technas-pay).

Vous intégrez avec un assistant de code ? Dites-lui de lire [AGENTS.md](AGENTS.md).

C'est la même API que les SDK [Node](https://github.com/Technas-Organization/technas-pay-node), [Python](https://github.com/Technas-Organization/technas-pay-python), [PHP](https://github.com/Technas-Organization/technas-pay-php) et [Dart](https://github.com/Technas-Organization/technas-pay-dart).

## 1. Installer

Java 17 ou plus récent. Une seule dépendance : `jackson-databind`. Installation depuis GitHub, par [JitPack](https://jitpack.io/#Technas-Organization/technas-pay-java).

Maven :

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>

<dependency>
  <groupId>com.github.Technas-Organization</groupId>
  <artifactId>technas-pay-java</artifactId>
  <version>v0.7.0</version>
</dependency>
```

Gradle (Kotlin) :

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.Technas-Organization:technas-pay-java:v0.7.0") }
```

Technas vous remet **un secret**. Mettez-le dans les variables d'environnement du serveur :

```bash
TECHNAS_PAY_SECRET=tps_test_...
```

Un secret `tps_test_` ne déplace aucun argent. Un secret `tps_live_` encaisse pour de vrai. Le code est le même.

## 2. Demander un paiement

```java
import fr.technas.pay.Status;
import fr.technas.pay.TechnasPay;
import fr.technas.pay.model.Customer;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.PaymentRequest;

TechnasPay pay = TechnasPay.fromEnv(); // lit TECHNAS_PAY_SECRET au premier appel ; un seul client pour l'application

Payment payment = pay.askForPayment(PaymentRequest.builder()
        .amount(5000)                                                // entier : 5 000 FCFA
        .reference(Status.retryReference(order.id(), order.attempt())) // "42-1" : une référence = un essai
        .customer(Customer.of("client@exemple.com", "Awa Mensah"))
        .returnUrl("https://votre-site.com/merci")
        .metadata(Map.of("order_id", order.id()))
        .build());
order.setPaymentId(payment.id());
return ResponseEntity.status(303).location(URI.create(payment.checkoutUrl())).build(); // Spring
```

Calculez le montant côté serveur. Ne passez pas `currency` si vous vendez en francs CFA : la devise suit le pays du client (`new Customer(email, name, phone, "TG")`), ou le client choisit son pays sur la page. Par carte : `.method("card")`, une `.currency("EUR")`, décimales permises (`.amount(new BigDecimal("12.50"))`). Les montants sont des `BigDecimal`, jamais des `double`.

### Qui paie les frais, et combien paie le client

Deux modes, en mobile money (franc CFA). Le mode par défaut est celui de votre compte (demandez à Technas de le changer) ; chaque paiement peut le surcharger avec `.fees(...)`.

| `fees` | Le client paie | Vous recevez |
|---|---|---|
| `"customer"` | votre prix + les frais du réseau de paiement + la commission Technas | **exactement votre prix** |
| `"partner"` | **votre prix**, frais compris | votre prix − les frais |

Pour savoir AVANT de créer le paiement combien le client paiera (le montant à lui afficher) et combien vous recevrez :

```java
import fr.technas.pay.model.Quote;
import fr.technas.pay.model.QuoteRequest;

Quote q = pay.quote(QuoteRequest.builder().amount(200).country("TG").fees("customer").build());
q.customerTotal();   // 214 : ce que le client paie (à afficher)
q.processingFee();   // 9   : frais du réseau de paiement
q.commission();      // 5   : commission Technas (2 %)
q.netAmount();       // 200 : ce que vous recevez
q.networks();        // le détail réseau par réseau (Mixx by Yas, Moov Money…)

Quote p = pay.quote(QuoteRequest.builder().amount(1000).country("TG").fees("partner").build());
// p.customerTotal() 1000, p.processingFee() 40, p.commission() 20, p.netAmount() 940

Payment payment = pay.askForPayment(PaymentRequest.builder()
        .amount(200).fees("customer") // le client paie 214, vous recevez 200
        .reference(…).customer(…).returnUrl(…)
        .build());
```

- `pay.getFees(...)` est le même appel que `pay.quote(...)`. Rien n'est créé.
- Un paiement créé ensuite avec le même montant, le même pays et le même `fees` porte les mêmes montants : `payment.customerTotal()`, `payment.processingFee()`, `payment.netAmount()`, avec `payment.customerAmount()` et `payment.feesPaidBy()`. `payment.amount()` reste votre prix. L'avis `payment.*` (`event.payment()`) porte les mêmes champs. Sur un serveur ancien : `customerAmount()` vaut `amount()`, `feesPaidBy()` vaut `"partner"`, `customerTotal()` et `processingFee()` sont `null`.
- Les frais du réseau de paiement suivent la grille du réseau. Le client choisit son réseau sur la page de paiement : `customerTotal()` est le plus haut des réseaux possibles, `networks().get(i).customerTotal()` le montant exact de chacun.
- L'opérateur du client (Mixx by Yas, Moov…) peut prendre ses propres frais sur le téléphone, en dehors de Technas : `networks().get(i).operatorFeeEstimate()` en donne une estimation quand on la connaît (« environ »), jamais une promesse (`null` sinon).
- Montant trop petit : `amount_too_low` avec `e.details().get("minimumAmount")`. Au Togo, le réseau n'accepte rien sous 200 F : en `"partner"`, 209 F au minimum ; en `"customer"`, 200 F passent.
- Par carte, seulement `"partner"` : ajouter des frais au paiement par carte d'un consommateur est interdit en Europe. `pay.quote(QuoteRequest.builder().amount(20).method("card").currency("EUR").build())` rend la commission (`netAmount()` `null` : les frais de la carte dépendent de la carte du client).

### Les moyens et les réseaux : à vous de choisir

Les moyens (`TechnasPay.PAYMENT_METHODS` : `"mobile_money"`, `"card"`) et les réseaux mobile money (`TechnasPay.NETWORKS` : `"mixx"` pour Mixx by Yas, `"moov"` pour Moov Money / Flooz, `"mtn_momo"`, `"orange_money"`, `"wave"`…) sont des listes fermées, à énumérer, avec une constante par valeur (`PaymentMethod.CARD`, `Network.MIXX`, `Network.ORANGE_MONEY`…). Comme `method()` et `fees`, ce sont des chaînes. Un réseau, c'est le porte-monnaie que le client a dans sa poche.

```java
PaymentMethods ouverts = pay.getPaymentMethods("TG");
// ouverts.networks() : [NetworkSetting[network=mixx, name=Mixx by Yas, country=TG, …, enabled=true], …] dans VOTRE ordre

// Réglage du compte : éteindre la carte, mettre Mixx en premier
pay.setPaymentMethods(PaymentMethodsRequest.builder()
        .methods(List.of(PaymentMethod.MOBILE_MONEY))
        .networkOrder(List.of(Network.MIXX, Network.MOOV))
        .build());

// Pour un paiement : seulement certains réseaux
pay.askForPayment(PaymentRequest.builder().amount(5000).reference(reference).customer(customer).returnUrl(returnUrl)
        .networks(List.of(Network.MIXX)).build());
```

- `setPaymentMethods` : un champ non donné ne change pas ; `.allNetworks()` = tous les réseaux (`.allMethods()` : tous les moyens). Une valeur hors de `PAYMENT_METHODS` / `NETWORKS` est refusée avant tout appel (`invalid_request`).
- `payment.network()` : le réseau avec lequel le client a payé (une fois payé) ; `payment.networks()` : les réseaux permis pour ce paiement, s'il les a restreints.
- Refus : `method_not_enabled` (moyen éteint sur votre compte), `network_not_enabled` (réseau éteint sur votre compte), `network_not_available` (pas ouvert dans ce pays).
- La page de paiement Technas et les devis respectent vos réseaux et leur ordre. L'étape où le client confirme sur la page du réseau de paiement peut encore lui montrer tous les réseaux du pays : ce sera fermé dans une prochaine version.

## 3. Recevoir l'avis de paiement

Technas signe chaque avis (`X-Technas-Payment-Signature: t=…,s=…`). Le SDK vérifie la signature sur le **corps brut**, puis appelle votre fonction.

```java
@PostMapping("/technas/webhook")
ResponseEntity<Map<String, Object>> webhook(@RequestBody String raw,          // corps BRUT, en String
        @RequestHeader(value = Signature.HEADER, required = false) String signature) {
    WebhookResult r = pay.handleWebhook(raw, signature, WebhookHandlers.builder()
            .onCompleted(e -> orders.markPaid(e.payment().metadata().get("order_id"))) // idempotent
            .onExpired(e -> orders.nextAttempt(e.payment().reference()))              // seul moment où relancer
            .build());
    return ResponseEntity.status(r.status()).body(r.body()); // 400 si la signature est refusée
}
```

Fonctions disponibles : `onCompleted`, `onFailed` (pas définitif), `onExpired` (définitif), `onRefund`, `onTopUp`, `onPayoutDestination`, `onBalanceConverted`, `onFxModeUpdated`, `onCardAccountUpdated`, `onKycRequired`, `onSettlement`. Une exception dans votre fonction doit remonter : Technas relivrera.

`onSettlement` reçoit `settlement.completed` et `settlement.failed`, l'issue d'un reversement de votre solde vers votre numéro mobile money. `event.settlement()` donne `amount()` (sorti du solde), `fee()` (frais du versement, à votre charge) et `netAmount()` (arrivé sur le numéro). Sur `settlement.failed`, `amount()` est recrédité sur le solde.

Kotlin :

```kotlin
val r = pay.handleWebhook(raw, signature, WebhookHandlers.builder()
    .onCompleted { e -> orders.markPaid(e.payment().reference()) }
    .build())
```

## 4. Pour une app mobile : les routes toutes faites

Votre app Flutter ([`technas_pay_flutter`](https://github.com/Technas-Organization/technas-pay-dart/tree/main/technas_pay_flutter)) appelle ces routes sans glue. Vous ne fournissez que votre authentification et la lecture de la commande en base :

```java
@RestController
class TechnasPayRoutes {
    private final AppRoutes routes;

    TechnasPayRoutes(TechnasPay pay, OrderRepository orders) {
        routes = pay.<Principal>appRoutes(
                        req -> ((HttpServletRequest) req.original()).getUserPrincipal(),  // null → 401
                        (orderId, user) -> orders.findByIdAndOwner(orderId, user.getName()) // relue EN BASE, null → 404
                                .map(o -> AppOrder.of(o.total(), Customer.of(o.email(), o.name())))
                                .orElse(null))
                .returnUrl("https://votre-app.com/paiement/retour")
                .webhooks(WebhookHandlers.builder().onCompleted(e -> orders.markPaid(e.payment().reference())).build())
                .build();
    }

    @RequestMapping("/api/technas-pay/**")
    void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
        TechnasPayServlet.handle(routes, req, res, "/api/technas-pay");
    }
}
```

Hors Servlet (Ktor, Vert.x, Micronaut Netty) : construisez une `AppRequest(method, path, query, headers, rawBody, original)` et renvoyez `routes.handle(req)` (`status()`, `json()`).

Routes : `GET /methods?orderId=`, `POST /payments` `{orderId, method?}`, `GET /payments/{id}`, `POST /payments/{id}/cancel` (le client change d'offre), `POST /webhook` (adresse à déclarer à Technas). Les essais expirés sont sautés tout seuls (`42-1`, `42-2`…) : rien à stocker. Carte : `.card(true)` et une `currency` dans `AppOrder`, puis `.authorizeAdmin(...)` pour l'écran du marchand (`GET /card/account`, `POST /card/onboarding`). Contrat : [`contract/app-contract.v1.json`](contract/app-contract.v1.json), rejoué par ce paquet, les autres SDK et le client Flutter.

Exemples complets, compilés en CI : [`examples/spring-boot`](examples/spring-boot) (Java) et [`examples/kotlin-spring`](examples/kotlin-spring) (Kotlin).

## 5. Tout le reste

| Méthode | Rôle |
|---|---|
| `getPayment(id)`, `getPaymentByReference(ref)` | Statut ; rattraper un avis manqué |
| `quote(QuoteRequest)`, `getFees(QuoteRequest)` | Devis : ce que le client paiera (`customerTotal()`), ce que vous recevrez (`netAmount()`), réseau par réseau ; rien n'est créé |
| `getPaymentMethods(country)`, `setPaymentMethods(PaymentMethodsRequest)` | Moyens et réseaux ouverts, activés, et leur ordre ; les régler |
| `cancelPayment(id)` | Annuler un paiement que personne n'a commencé à payer (le client change d'offre) |
| `listTransactions(limit, status, cursor)` | Historique paginé (`nextCursor()`) |
| `refund(paymentId)`, `refund(paymentId, amount, fees, reference)` | Rembourser au numéro ou à la carte qui a payé |
| `recordExternalRefund(paymentId, amount, method, note, recordedBy, reference)` | Noter un remboursement fait hors Technas |
| `getRefund(id)` | Statut d'un remboursement |
| `topUpBalance(amount, currency, returnUrl)` | Recharger le solde (ouvrir `checkoutUrl()`) |
| `getBalance()`, `getSettlements()` | Solde par devise, reversements |
| `getFxMode()`, `setFxMode(mode, acceptRisk, markupPct, homeCurrency)` | Clients hors zone franc CFA (option à risque) |
| `setCountryRules(allowed, excluded)` | Où vous vendez |
| `getPayoutNetworks(country)`, `getPayoutDestination()`, `setPayoutDestination(...)` | Numéro de reversement |
| `startCardOnboarding(...)`, `getCardAccount()`, `getCardDashboardLink()` | Compte carte et son tableau de bord |
| `getAccountKyc()` | Seuil d'identité : encaissé, seuil, échéance, lien de la console |
| `verifyWebhook(raw, sig)`, `handleWebhook(...)`, `createTestWebhook(...)` | Avis signés |
| `simulateTestPayment(id, "completed" \| "failed" \| "expired")` | Jouer le payeur en test |

Aides : `Status.canRetry(status)` (vrai seulement pour `expired`), `Status.isFinal()`, `Status.retryReference()`, `Status.currencyForCountry("TG")`.

## Erreurs

```java
try {
    pay.askForPayment(request);
} catch (TechnasPayException e) {
    if ("payment_expired".equals(e.code())) order.incrementAttempt(); // nouvelle référence
    else if (e.retryable()) { /* refaire le même appel */ }
    else throw e;
}
```

Codes et conduite à tenir : [AGENTS.md](AGENTS.md#erreurs).

## Seuil d'identité

En réel, un compte sans pièce d'identité vérifiée voit ses NOUVEAUX paiements mobile money refusés (`kyc_required`) au-delà d'un montant encaissé ou d'un nombre de jours après son premier paiement réel (aujourd'hui 1 000 000 F CFA ou 30 jours : lisez-les dans `getAccountKyc()`, ne les écrivez pas en dur). Rien de ce qui existe n'est bloqué, et la carte n'est pas concernée. `getAccountKyc()` donne l'état à montrer au marchand ; l'avis `account.kyc_required` (`onKycRequired`, contenu `event.kyc()` : `reason()` est son déclencheur, `stage()` et `blockReason()` l'état, comme `getAccountKyc()`) arrive à 80 % du seuil, 7 jours avant l'échéance, puis au premier refus. Le responsable dépose sa pièce dans la console (`consoleUrl()`).

## La règle de relance

**Une référence = un essai.** `failed` n'est pas définitif : le client peut encore payer sur la même page pendant une heure. Ne relancez qu'après `payment.expired`, avec une nouvelle référence.

### Le client change d'offre ou revient plus tard

Le client choisit une offre, ouvre la page de paiement, ne paie pas, puis revient choisir une AUTRE offre, ou revient le lendemain. Rien n'est payé : ne répondez jamais « un paiement existe déjà ».

- **Une référence = un essai.** Nouvelle offre, ou retour après expiration : un NOUVEL essai, `Status.retryReference(factureId, n + 1)`, `n` gardé en base. Réutiliser la référence d'un autre montant lève `reference_conflict` (409) ; celle d'un essai expiré, `payment_expired` (409). Les routes toutes faites (`appRoutes`) le font seules.
- **Une facture n'est payée QUE sur `payment.completed`** (`onCompleted`). Jamais sur `returnUrl`, jamais à l'ouverture de la page.
- **Un essai non payé ne bloque jamais un nouvel essai.** Même offre et essai `pending` ou `failed` : renvoyez le client sur la même page (`checkoutUrl()`). Autre offre : ANNULEZ l'ancien essai (`pay.cancelPayment(ancienId)`), puis créez le nouveau avec une NOUVELLE référence ; ne refusez pas. Si l'annulation répond `payment_in_progress` ou `payment_already_completed`, ne créez aucun second paiement. `expired` : marquez la tentative expirée et laissez recommencer.
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
            pay.cancelPayment(dernier.id()); // autre offre : l'ancien lien est fermé d'abord
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


## Développer

```bash
mvn verify spotbugs:check
```

Les tests tournent contre un faux transport (aucun appel réseau) et rejouent le contrat partagé. Sécurité : [SECURITY.md](SECURITY.md).

## Licence

MIT.
