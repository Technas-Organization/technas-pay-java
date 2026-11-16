# Journal

## 0.4.1 (16/11/2026)

Routes toutes faites : si l'annulation de l'ancien essai répond 404 (serveur sans la route d'annulation), l'essai suivant est créé comme avant. Contrat v1 à 31 cas.

## 0.4.0 (16/11/2026)

`cancelPayment(id)` (`POST /v1/payments/{id}/cancel`) : annule un paiement que personne n'a commencé à payer. `Payment` : `canceledAt()`, `cancelReason()`, `reason()` (avis `payment.expired` : `canceled` ou `timeout`). Routes toutes faites : `POST /payments/{id}/cancel`, et sur `reference_conflict` l'ancien essai est relu puis annulé avant le suivant (aucun second paiement si l'annulation répond `payment_in_progress` ou `payment_already_completed`). Contrat v1 à 30 cas.

## 0.3.0 (15/11/2026)

Le client change d'offre ou revient plus tard : les routes toutes faites passent à l'essai suivant sur `reference_conflict` (comme sur `payment_expired`) ; contrat v1 à 24 cas ; section dédiée dans README et AGENTS.md ; test de scénario.

## 0.2.1 (15/11/2026)

Avis `account.kyc_required` complet : `stage()`, `blockReason()`, `daysElapsed()`, `graceDays()`, `firstCollectedAt()` (nuls dans un avis plus ancien).

## 0.2.0 (15/11/2026)

Seuil d'identité : `getAccountKyc()` (`GET /v1/account/kyc`), avis `account.kyc_required` (`onKycRequired`, `event.kyc()`), précisions `consoleUrl` (`kyc_required`) et `version` (`contract_required`) dans `details()`.

## 0.1.1 (15/11/2026)

Installation par JitPack réparée (Maven épinglé par le wrapper). Aucun changement de code.

## 0.1.0 (15/11/2026)

Première version : paiements mobile money et carte, statut, historique, remboursements, recharges, numéro de reversement, change, avis signés, routes toutes faites pour l'app (contrat v1, 23 cas) et adaptateur Servlet (Spring Boot, Quarkus, Tomcat, Jetty).
