# technas-pay-java (Java, Kotlin)

Encaissez des paiements mobile money et par carte via Technas depuis un serveur Java ou Kotlin : Spring Boot, Quarkus, Micronaut, Ktor, ou Java sans framework.

**Technas Pay réunit le mobile money et la carte dans un seul SDK, du serveur jusqu'à l'app Flutter.** Carte : 1 % (plus les frais de votre compte carte, selon le pays : environ 1,5 % + 0,25 € en Europe). Mobile money : 2 % (les frais de l'opérateur sont payés par le client). Sans abonnement, et une clé de test sans papier pour commencer.

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
  <version>v0.2.0</version>
</dependency>
```

Gradle (Kotlin) :

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.Technas-Organization:technas-pay-java:v0.2.0") }
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

Fonctions disponibles : `onCompleted`, `onFailed` (pas définitif), `onExpired` (définitif), `onRefund`, `onTopUp`, `onPayoutDestination`, `onBalanceConverted`, `onFxModeUpdated`, `onCardAccountUpdated`, `onKycRequired`. Une exception dans votre fonction doit remonter : Technas relivrera.

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

Routes : `GET /methods?orderId=`, `POST /payments` `{orderId, method?}`, `GET /payments/{id}`, `POST /webhook` (adresse à déclarer à Technas). Les essais expirés sont sautés tout seuls (`42-1`, `42-2`…) : rien à stocker. Carte : `.card(true)` et une `currency` dans `AppOrder`, puis `.authorizeAdmin(...)` pour l'écran du marchand (`GET /card/account`, `POST /card/onboarding`). Contrat : [`contract/app-contract.v1.json`](contract/app-contract.v1.json), rejoué par ce paquet, les autres SDK et le client Flutter.

Exemples complets, compilés en CI : [`examples/spring-boot`](examples/spring-boot) (Java) et [`examples/kotlin-spring`](examples/kotlin-spring) (Kotlin).

## 5. Tout le reste

| Méthode | Rôle |
|---|---|
| `getPayment(id)`, `getPaymentByReference(ref)` | Statut ; rattraper un avis manqué |
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

En réel, un compte sans pièce d'identité vérifiée voit ses NOUVEAUX paiements mobile money refusés (`kyc_required`) au-delà d'un montant encaissé ou d'un nombre de jours après son premier paiement réel (aujourd'hui 1 000 000 F CFA ou 30 jours : lisez-les dans `getAccountKyc()`, ne les écrivez pas en dur). Rien de ce qui existe n'est bloqué, et la carte n'est pas concernée. `getAccountKyc()` donne l'état à montrer au marchand ; l'avis `account.kyc_required` (`onKycRequired`, contenu `event.kyc()`) arrive à 80 % du seuil, 7 jours avant l'échéance, puis au premier refus. Le responsable dépose sa pièce dans la console (`consoleUrl()`).

## La règle de relance

**Une référence = un essai.** `failed` n'est pas définitif : le client peut encore payer sur la même page pendant une heure. Ne relancez qu'après `payment.expired`, avec une nouvelle référence.

## Développer

```bash
mvn verify spotbugs:check
```

Les tests tournent contre un faux transport (aucun appel réseau) et rejouent le contrat partagé. Sécurité : [SECURITY.md](SECURITY.md).

## Licence

MIT.
