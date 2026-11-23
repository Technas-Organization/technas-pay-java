package fr.technas.pay;

import fr.technas.pay.internal.Ascii;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.technas.pay.app.AppOrder;
import fr.technas.pay.app.AppRequest;
import fr.technas.pay.app.AppRoutes;
import fr.technas.pay.http.JdkTransport;
import fr.technas.pay.http.Transport;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.AccountKyc;
import fr.technas.pay.model.CardAccount;
import fr.technas.pay.model.CardOnboardingLink;
import fr.technas.pay.model.Customer;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.PaymentMethods;
import fr.technas.pay.model.PaymentMethodsRequest;
import fr.technas.pay.model.PaymentRequest;
import fr.technas.pay.model.Quote;
import fr.technas.pay.model.QuoteRequest;
import fr.technas.pay.model.Refund;
import fr.technas.pay.model.TopUp;
import fr.technas.pay.model.TransactionPage;
import fr.technas.pay.webhook.Event;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookHandlers;
import fr.technas.pay.webhook.WebhookResult;
import fr.technas.pay.webhook.WebhookVerificationException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client SERVEUR de Technas Pay. Le secret ({@code tps_test_…} / {@code tps_live_…}) ne voyage que
 * dans l'en-tête {@code Authorization} : jamais dans une URL, un message d'erreur, un journal ni un
 * {@code toString()}. Jamais dans une app mobile ni dans du code envoyé au navigateur.
 *
 * <pre>{@code
 * TechnasPay pay = TechnasPay.fromEnv(); // lit TECHNAS_PAY_SECRET
 * }</pre>
 */
public final class TechnasPay {
    public static final String VERSION = "0.7.0";

    /** Tous les moyens de paiement, à énumérer : {@code mobile_money}, {@code card} (voir {@link PaymentMethod}). */
    public static final List<String> PAYMENT_METHODS = PaymentMethod.ALL;

    /** Tous les réseaux mobile money, à énumérer : {@code mixx}, {@code moov}, {@code mtn_momo}… (voir {@link Network}). */
    public static final List<String> NETWORKS = Network.ALL;
    public static final String DEFAULT_BASE_URL = "https://pay-api.technas.fr";
    public static final String SECRET_ENV = "TECHNAS_PAY_SECRET";
    private static final Pattern SECRET = Pattern.compile("^tps_(live|test)_([\\x21-\\x2d\\x2f-\\x7e]+)\\.([\\x21-\\x2d\\x2f-\\x7e]+)$"); // ASCII imprimable sans « . »
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final String secret;
    private final String baseUrl;
    private final Transport transport;
    private final long webhookTolerance;
    private final String defaultCurrency;

    private TechnasPay(String secret, String baseUrl, Transport transport, long webhookTolerance, String defaultCurrency) {
        this.secret = secret;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        URI uri = URI.create(this.baseUrl);
        String scheme = uri.getScheme() == null ? "" : Ascii.lower(uri.getScheme());
        String host = uri.getHost() == null ? "" : Ascii.lower(uri.getHost());
        // La clé part dans un en-tête : jamais vers une adresse en clair, sauf la machine locale (tests).
        if (!"https".equals(scheme) && !("http".equals(scheme) && LOCAL_HOSTS.contains(host))) {
            throw new TechnasPayException("invalid_base_url", "baseUrl doit être en https");
        }
        this.transport = transport;
        this.webhookTolerance = webhookTolerance;
        this.defaultCurrency = defaultCurrency;
    }

    /** Avec votre secret (lisez-le de l'environnement ou d'un coffre, jamais du code). */
    public static TechnasPay of(String secret) {
        return builder().secret(secret).build();
    }

    /** Lit {@code TECHNAS_PAY_SECRET} au premier appel. */
    public static TechnasPay fromEnv() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Ni secret ni clé : seulement l'adresse de l'API. */
    @Override
    public String toString() {
        return "TechnasPay[baseUrl=" + baseUrl + "]";
    }

    private record Secrets(String mode, String apiKey, String webhookSecret) {
        @Override
        public String toString() {
            return "Secrets[mode=" + mode + "]";
        }
    }

    private Secrets secrets() {
        String raw = secret != null ? secret : System.getenv(SECRET_ENV);
        Matcher m = SECRET.matcher(raw == null ? "" : raw);
        // Le message ne cite jamais la valeur reçue : elle finirait dans des journaux.
        if (!m.matches()) {
            throw new TechnasPayException("invalid_secret", "Secret Technas absent ou mal formé. Vérifiez la variable " + SECRET_ENV + ".");
        }
        return new Secrets(m.group(1), m.group(2), m.group(3));
    }

    /** Vrai avec un secret {@code tps_test_} : aucun argent ne circule. */
    public boolean isTestMode() {
        return "test".equals(secrets().mode());
    }

    private JsonNode call(String method, String path, Object body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + secrets().apiKey());
        headers.put("User-Agent", "technas-pay-java/" + VERSION);
        headers.put("Accept", "application/json");
        if (body != null) {
            headers.put("Content-Type", "application/json");
        }
        Transport.Response r = transport.send(method, baseUrl + path, headers, body == null ? null : Json.write(body));
        JsonNode data = null;
        if (r.body() != null && !r.body().isEmpty()) {
            try {
                data = Json.parse(r.body());
            } catch (IllegalArgumentException e) {
                if (r.status() >= 200 && r.status() < 300) {
                    throw new TechnasPayException("invalid_response", "Réponse illisible du service de paiement", r.status(), Map.of());
                }
            }
        }
        // Hors 2xx = erreur, redirections comprises : elles ne sont jamais suivies (la clé ne part pas ailleurs).
        if (r.status() < 200 || r.status() >= 300) {
            throw fromResponse(r.status(), data);
        }
        return data == null ? Json.object() : data;
    }

    private static TechnasPayException fromResponse(int status, JsonNode d) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (String[] k : new String[][] {{"missing_amount", "missingAmount"}, {"refundable_amount", "refundableAmount"},
                {"minimum_amount", "minimumAmount"}}) {
            BigDecimal n = Json.nnum(d, k[0]);
            if (n != null) {
                details.put(k[1], n);
            }
        }
        for (String[] k : new String[][] {{"currency", "currency"}, {"reason", "reason"}, {"risk", "risk"}, {"account_status", "accountStatus"}, {"console_url", "consoleUrl"}, {"version", "version"}}) {
            String v = Json.nstr(d, k[0]);
            if (v != null) {
                details.put(k[1], v);
            }
        }
        String code = Json.nstr(d, "code");
        String message = Json.nstr(d, "message");
        return new TechnasPayException(code == null ? "request_failed" : code, message == null ? "Réponse HTTP " + status : message, status, details);
    }

    private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    /** Un identifiant dans un chemin : jamais « . », « .. », « / » ni rien d'encodé (un proxy pourrait le normaliser). */
    private static String seg(String id) {
        if (id == null || !SEGMENT.matcher(id).matches()) {
            throw new TechnasPayException("invalid_request", "Identifiant invalide");
        }
        return id;
    }

    private static String q(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static void positive(BigDecimal amount, boolean integer) {
        boolean ok = amount != null && amount.signum() > 0 && (!integer || amount.stripTrailingZeros().scale() <= 0);
        if (!ok) {
            throw new TechnasPayException("invalid_amount", integer
                    ? "amount doit être un entier positif (5000 = 5 000 FCFA)"
                    : "amount doit être positif (5000 = 5 000 FCFA, 12.5 = 12,50 €)");
        }
    }

    private static String fees(String fees) {
        if (fees != null && !"partner".equals(fees) && !"customer".equals(fees)) {
            throw new TechnasPayException("invalid_request", "fees vaut \"partner\" ou \"customer\"");
        }
        return fees;
    }

    private static List<String> networks(List<String> values, String name) {
        if (values != null && !Network.ALL.containsAll(values)) {
            throw new TechnasPayException("invalid_request", name + " : des réseaux de Network.ALL (mixx, moov, mtn_momo…)");
        }
        return values;
    }

    private static List<String> methods(List<String> values) {
        if (values != null && !PaymentMethod.ALL.containsAll(values)) {
            throw new TechnasPayException("invalid_request", "methods : \"mobile_money\" et \"card\"");
        }
        return values;
    }

    private static String method(String method) {
        String m = method == null ? "mobile_money" : method;
        if (!"mobile_money".equals(m) && !"card".equals(m)) {
            throw new TechnasPayException("invalid_request", "method vaut \"mobile_money\" ou \"card\"");
        }
        return m;
    }

    // --- Paiements ---------------------------------------------------------------------------

    /** Crée la demande de paiement ; redirigez ensuite le client (303) vers {@code checkoutUrl()}. */
    public Payment askForPayment(PaymentRequest in) {
        String method = method(in.method());
        String fees = fees(in.fees());
        List<String> networks = networks(in.networks(), "networks");
        positive(in.amount(), "mobile_money".equals(method));
        String currency = in.currency() == null ? "" : Ascii.upper(in.currency().trim());
        Customer c = in.customer();
        if (currency.isEmpty()) {
            String country = c == null || c.country() == null ? "" : c.country().trim();
            String fromCountry = Status.currencyForCountry(country);
            currency = !country.isEmpty() ? (fromCountry == null ? "" : fromCountry)
                    : (defaultCurrency == null ? "" : Ascii.upper(defaultCurrency.trim()));
        }
        if ("card".equals(method) && currency.isEmpty()) {
            throw new TechnasPayException("currency_required", "currency est obligatoire pour un paiement par carte (EUR, XOF…)");
        }
        ObjectNode body = Json.object();
        body.put("amount", in.amount().stripTrailingZeros().scale() <= 0 ? in.amount().setScale(0) : in.amount());
        body.put("reference", in.reference() == null ? "" : in.reference());
        ObjectNode customer = body.putObject("customer");
        if (c != null) {
            customer.put("email", c.email());
            customer.put("name", c.name());
            if (c.phone() != null) {
                customer.put("phone", c.phone());
            }
            if (c.country() != null) {
                customer.put("country", c.country());
            }
        }
        body.put("return_url", in.returnUrl() == null ? "" : in.returnUrl());
        body.set("metadata", Json.parse(Json.write(in.metadata() == null ? Map.of() : in.metadata())));
        if ("card".equals(method)) {
            body.put("method", "card");
        }
        if (!currency.isEmpty()) {
            body.put("currency", currency);
        }
        if (in.description() != null) {
            body.put("description", in.description());
        }
        if (in.allowedCountries() != null) {
            in.allowedCountries().forEach(body.putArray("allowed_countries")::add);
        }
        if (in.excludedCountries() != null) {
            in.excludedCountries().forEach(body.putArray("excluded_countries")::add);
        }
        if (in.askCountry()) {
            body.put("ask_country", true);
        }
        if (fees != null) {
            body.put("fees", fees);
        }
        if (networks != null && !networks.isEmpty()) {
            networks.forEach(body.putArray("networks")::add);
        }
        return Payment.fromJson(call("POST", "/v1/payments", body));
    }

    /**
     * Le devis : pour un montant, ce que le client paiera ({@code customerTotal()}, à lui afficher) et
     * ce que vous recevrez ({@code netAmount()}), réseau par réseau ({@code networks()}). Ne crée aucun
     * paiement ; un paiement créé ensuite avec le même montant, le même pays et le même {@code fees}
     * porte les mêmes montants. Montant trop petit : {@code amount_too_low}, avec
     * {@code e.details().get("minimumAmount")}.
     *
     * <pre>{@code
     * Quote q = pay.quote(QuoteRequest.builder().amount(200).country("TG").fees("customer").build());
     * q.customerTotal(); // 214 : le client paie 214, vous recevez 200
     * }</pre>
     */
    public Quote quote(QuoteRequest in) {
        String method = method(in.method());
        String fees = fees(in.fees());
        positive(in.amount(), "mobile_money".equals(method));
        ObjectNode body = Json.object();
        body.put("amount", in.amount().stripTrailingZeros().scale() <= 0 ? in.amount().setScale(0) : in.amount());
        if (in.method() != null) {
            body.put("method", method);
        }
        String country = in.country() == null ? "" : Ascii.upper(in.country().trim());
        if (!country.isEmpty()) {
            body.put("country", country);
        }
        if (in.network() != null && !in.network().isEmpty()) {
            body.put("network", in.network());
        }
        String currency = in.currency() == null ? "" : Ascii.upper(in.currency().trim());
        if (!currency.isEmpty()) {
            body.put("currency", currency);
        }
        if (fees != null) {
            body.put("fees", fees);
        }
        return Quote.fromJson(call("POST", "/v1/quotes", body));
    }

    /** Même chose que {@link #quote(QuoteRequest)} : combien le client paiera, combien vous recevrez. */
    public Quote getFees(QuoteRequest in) {
        return quote(in);
    }

    public Payment getPayment(String id) {
        return Payment.fromJson(call("GET", "/v1/payments/" + seg(id), null));
    }

    /**
     * Annule DÉFINITIVEMENT un paiement que personne n'a commencé à payer (le client change d'offre) :
     * rien ne peut plus aboutir dessus. Rend le paiement, {@code expired}, sans lien. Rejouer rend le
     * même résultat. Refus : {@code payment_already_completed} (payé : relisez-le),
     * {@code payment_in_progress} (une demande est en cours chez l'opérateur : ne créez PAS de second
     * paiement, attendez l'avis). Puis créez le nouvel essai avec une NOUVELLE référence.
     */
    public Payment cancelPayment(String id) {
        return Payment.fromJson(call("POST", "/v1/payments/" + seg(id) + "/cancel", Json.object()));
    }

    /** Pour rattraper un avis manqué. */
    public Payment getPaymentByReference(String reference) {
        return Payment.fromJson(call("GET", "/v1/payments?reference=" + q(reference), null));
    }

    /** Vos paiements, du plus récent au plus ancien, par pages ({@code nextCursor}). Arguments {@code null} : non filtrés. */
    public TransactionPage listTransactions(Integer limit, String status, String cursor) {
        List<String> qs = new ArrayList<>();
        if (limit != null) {
            qs.add("limit=" + limit);
        }
        if (status != null && !status.isEmpty()) {
            qs.add("status=" + q(status));
        }
        if (cursor != null && !cursor.isEmpty()) {
            qs.add("cursor=" + q(cursor));
        }
        JsonNode raw = call("GET", "/v1/transactions" + (qs.isEmpty() ? "" : "?" + String.join("&", qs)), null);
        List<Payment> items = new ArrayList<>();
        JsonNode list = raw.get("items");
        if (list != null && list.isArray()) {
            list.forEach(p -> items.add(Payment.fromJson(p)));
        }
        return new TransactionPage(List.copyOf(items), Json.nstr(raw, "next_cursor"));
    }

    /** Mode test : {@code completed}, {@code failed} (pas définitif) ou {@code expired} (définitif), sans navigateur. */
    public Payment simulateTestPayment(String paymentId, String outcome) {
        if (!"test".equals(secrets().mode())) {
            throw new TechnasPayException("test_only", "simulateTestPayment exige un secret tps_test_");
        }
        if (!Set.of("completed", "failed", "expired").contains(outcome)) {
            throw new TechnasPayException("invalid_outcome", "outcome : completed, failed ou expired");
        }
        // Un paiement réussi renvoie vers votre returnUrl : aucune redirection n'est suivie.
        Transport.Response r = transport.send("POST", baseUrl + "/test-checkout/" + seg(paymentId) + "/" + outcome,
                Map.of("User-Agent", "technas-pay-java/" + VERSION), null);
        if (r.status() == 404) {
            throw new TechnasPayException("payment_not_found", "Paiement de test introuvable", 404, Map.of());
        }
        return getPayment(paymentId);
    }

    // --- Remboursements ----------------------------------------------------------------------

    /**
     * Rembourse un paiement {@code completed}, au numéro (ou à la carte) qui a payé. {@code amount}
     * {@code null} : tout ce qui reste. {@code fees} : {@code partner} (défaut) ou {@code customer}.
     * Réservé à un administrateur.
     */
    public Refund refund(String paymentId, BigDecimal amount, String fees, String reference) {
        String f = fees == null ? "partner" : fees;
        if (amount != null) {
            positive(amount, false);
        }
        if (!"partner".equals(f) && !"customer".equals(f)) {
            throw new TechnasPayException("invalid_request", "fees vaut \"partner\" ou \"customer\"");
        }
        ObjectNode body = Json.object().put("fees", f);
        if (amount != null) {
            body.put("amount", amount);
        }
        if (reference != null && !reference.isEmpty()) {
            body.put("reference", reference);
        }
        return Refund.fromJson(call("POST", "/v1/payments/" + seg(paymentId) + "/refunds", body));
    }

    public Refund refund(String paymentId) {
        return refund(paymentId, null, null, null);
    }

    /** Enregistre un remboursement fait HORS Technas (Flooz, espèces…). Aucun argent ne bouge. */
    public Refund recordExternalRefund(String paymentId, long amount, String method, String note, String recordedBy, String reference) {
        positive(BigDecimal.valueOf(amount), true);
        if (method == null || method.trim().isEmpty()) {
            throw new TechnasPayException("invalid_request", "method est obligatoire : \"Flooz\", \"Espèces\"…");
        }
        ObjectNode body = Json.object().put("amount", amount).put("method", method.trim());
        if (note != null && !note.isEmpty()) {
            body.put("note", note);
        }
        if (recordedBy != null && !recordedBy.isEmpty()) {
            body.put("recorded_by", recordedBy);
        }
        if (reference != null && !reference.isEmpty()) {
            body.put("reference", reference);
        }
        return Refund.fromJson(call("POST", "/v1/payments/" + seg(paymentId) + "/external-refunds", body));
    }

    public Refund getRefund(String id) {
        return Refund.fromJson(call("GET", "/v1/refunds/" + seg(id), null));
    }

    // --- Solde, recharges, reversements, change ----------------------------------------------

    /** Recharge votre solde : ouvrez {@code checkoutUrl()}. Crédit : avis {@code balance.topped_up}. */
    public TopUp topUpBalance(long amount, String currency, String returnUrl) {
        positive(BigDecimal.valueOf(amount), true);
        ObjectNode body = Json.object().put("amount", amount).put("currency", currency);
        if (returnUrl != null) {
            body.put("return_url", returnUrl);
        }
        return TopUp.fromJson(call("POST", "/v1/balance/top-ups", body));
    }

    /** Ce que Technas vous doit, par devise : {@code [{currency, amount}]}. */
    public Object getBalance() {
        return Json.plain(call("GET", "/v1/balance", null));
    }

    public Object getSettlements() {
        return Json.plain(call("GET", "/v1/settlements", null));
    }

    public Object getPayoutNetworks(String country) {
        return Json.plain(call("GET", "/v1/payout-networks?country=" + q(country), null));
    }

    public Object getPayoutDestination() {
        return Json.plain(call("GET", "/v1/payout-destination", null));
    }

    /** Rien ne change avant le clic du responsable du compte dans l'e-mail reçu. Réservé à un administrateur. */
    public Object setPayoutDestination(String country, String network, String phone, String beneficiaryName) {
        ObjectNode body = Json.object().put("country", country).put("network", network).put("phone", phone).put("beneficiary_name", beneficiaryName);
        return Json.plain(call("PUT", "/v1/payout-destination", body));
    }

    /** Votre option hors zone CFA, ses risques ({@code risks}), les pays ouverts. */
    public Object getFxMode() {
        return Json.plain(call("GET", "/v1/fx", null));
    }

    /**
     * N'appelez avec {@code acceptRisk = true} qu'APRÈS avoir montré {@code getFxMode().risks[mode]}
     * à l'utilisateur et obtenu son accord explicite. {@code converted} lève {@code fx_mode_unavailable}.
     */
    public Object setFxMode(String mode, boolean acceptRisk, BigDecimal markupPct, String homeCurrency) {
        if (!Set.of("none", "local", "converted").contains(mode)) {
            throw new TechnasPayException("invalid_request", "mode vaut \"none\", \"local\" ou \"converted\"");
        }
        ObjectNode body = Json.object().put("mode", mode).put("accept_risk", acceptRisk);
        if (markupPct != null) {
            body.put("markup_pct", markupPct);
        }
        if (homeCurrency != null) {
            body.put("home_currency", homeCurrency);
        }
        return Json.plain(call("PUT", "/v1/fx", body));
    }

    /** Voir {@link #getPaymentMethods(String)}, tous pays. */
    public PaymentMethods getPaymentMethods() {
        return getPaymentMethods(null);
    }

    /**
     * Les moyens et réseaux ouverts pour votre compte (par pays, {@code country} facultatif), ce que
     * vous avez activé ({@code enabled}) et votre ordre d'affichage. Pour construire votre propre
     * choix de paiement, ou vos réglages.
     */
    public PaymentMethods getPaymentMethods(String country) {
        String c = country == null ? "" : Ascii.upper(country.trim());
        return PaymentMethods.fromJson(call("GET", "/v1/payment-methods" + (c.isEmpty() ? "" : "?country=" + q(c)), null));
    }

    /**
     * Activer ou éteindre des moyens ({@link PaymentMethod}) et des réseaux ({@link Network}), et fixer
     * l'ordre d'affichage sur la page de paiement. Un champ non donné ne change pas ;
     * {@code allNetworks()} : tous les réseaux. Une valeur inconnue est refusée avant tout appel.
     */
    public PaymentMethods setPaymentMethods(PaymentMethodsRequest in) {
        methods(in.methods());
        networks(in.networks(), "networks");
        networks(in.networkOrder(), "networkOrder");
        ObjectNode body = Json.object();
        if (in.allMethods()) {
            body.putNull("methods");
        } else if (in.methods() != null) {
            in.methods().forEach(body.putArray("methods")::add);
        }
        if (in.allNetworks()) {
            body.putNull("networks");
        } else if (in.networks() != null) {
            in.networks().forEach(body.putArray("networks")::add);
        }
        if (in.networkOrder() != null) {
            in.networkOrder().forEach(body.putArray("network_order")::add);
        }
        return PaymentMethods.fromJson(call("PUT", "/v1/payment-methods", body));
    }

    /** {@code allowed} {@code null} : tous les pays. */
    public Object setCountryRules(List<String> allowed, List<String> excluded) {
        ObjectNode body = Json.object();
        if (allowed == null) {
            body.putNull("allowed");
        } else {
            allowed.forEach(body.putArray("allowed")::add);
        }
        (excluded == null ? List.<String>of() : excluded).forEach(body.putArray("excluded")::add);
        return Json.plain(call("PUT", "/v1/country-rules", body));
    }

    // --- Carte ---------------------------------------------------------------------------------

    /**
     * Ouvre (ou reprend) votre compte carte : le RESPONSABLE ouvre {@code url()} et y donne son
     * identité et sa banque. À appeler au clic (le lien expire en minutes). {@code country} la 1ʳᵉ fois.
     */
    public CardOnboardingLink startCardOnboarding(String returnUrl, String refreshUrl, String country, String email, String businessType) {
        ObjectNode body = Json.object().put("return_url", returnUrl).put("refresh_url", refreshUrl);
        if (country != null) {
            body.put("country", Ascii.upper(country.trim()));
        }
        if (email != null) {
            body.put("email", email);
        }
        if (businessType != null) {
            body.put("business_type", businessType);
        }
        return CardOnboardingLink.fromJson(call("POST", "/v1/card/onboarding", body));
    }

    /** {@code canAcceptPayments()} décide de tout pour un paiement par carte. */
    public CardAccount getCardAccount() {
        return CardAccount.fromJson(call("GET", "/v1/card/account", null));
    }

    /** Le tableau de bord du compte carte : le responsable s'y connecte avec SES identifiants. */
    public String getCardDashboardLink() {
        return Json.str(call("POST", "/v1/card/dashboard-link", Json.object()), "url");
    }

    // --- Seuil d'identité ----------------------------------------------------------------------

    /**
     * Où en est le seuil d'identité du compte : encaissé, seuil, échéance, et la console où
     * déposer la pièce. Le même état que la console et que l'avis {@code account.kyc_required}.
     */
    public AccountKyc getAccountKyc() {
        return AccountKyc.fromJson(call("GET", "/v1/account/kyc", null));
    }

    // --- Avis signés ---------------------------------------------------------------------------

    /** Vérifie la signature sur le corps BRUT et rend l'événement. Lève {@link WebhookVerificationException} (répondez 400). */
    public Event verifyWebhook(String rawBody, String signatureHeader) {
        return Signature.verify(rawBody, signatureHeader, secrets().webhookSecret(), webhookTolerance, Instant.now().getEpochSecond());
    }

    /**
     * Vérifie, appelle la bonne fonction et rend la réponse : 400 si la signature est refusée (aucune
     * fonction appelée), 200 sinon. Une exception de vos fonctions REMONTE : répondez 500.
     */
    public WebhookResult handleWebhook(String rawBody, String signatureHeader, WebhookHandlers handlers) {
        Event event;
        try {
            event = verifyWebhook(rawBody, signatureHeader);
        } catch (WebhookVerificationException e) {
            return new WebhookResult(400, Map.of("error", "invalid_signature"), null);
        }
        Consumer<Event> f = handlers.forType(event.type());
        if (f != null) {
            f.accept(event);
        }
        return new WebhookResult(200, Map.of("received", true), event);
    }

    /** Avis {@code payment.*} signé, pour tester votre route : corps et en-têtes. Secret test seulement. */
    public Map<String, String> createTestWebhook(String type, String reference, BigDecimal amount, String currency) {
        if (!"test".equals(secrets().mode())) {
            throw new TechnasPayException("test_only", "createTestWebhook exige un secret tps_test_");
        }
        if (!Set.of("payment.completed", "payment.failed", "payment.expired").contains(type)) {
            throw new TechnasPayException("invalid_request", "type : payment.completed, payment.failed ou payment.expired");
        }
        String now = Instant.now().toString();
        boolean completed = "payment.completed".equals(type);
        boolean expired = "payment.expired".equals(type);
        ObjectNode root = Json.object().put("id", "evt_test_" + UUID.randomUUID()).put("type", type).put("created_at", now);
        ObjectNode d = root.putObject("data");
        d.put("id", "pay_test_" + UUID.randomUUID()).put("reference", reference)
                .put("status", completed ? "completed" : expired ? "expired" : "failed")
                .put("amount", amount).put("currency", currency).put("commission", 0).put("net_amount", amount);
        d.putObject("metadata");
        d.put("completed_at", completed ? now : null).put("expires_at", now).put("expired_at", expired ? now : null);
        String body = Json.write(root);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("body", body);
        out.put(Signature.HEADER, Signature.sign(body, secrets().webhookSecret()));
        return out;
    }

    /**
     * Routes toutes faites pour votre app ({@code technas_pay_flutter}) : voir {@link AppRoutes}.
     *
     * @param authorize votre authentification : l'utilisateur, ou {@code null} (401)
     * @param resolveOrder la commande de CET utilisateur relue EN BASE, ou {@code null} (404)
     */
    public <U> AppRoutes.Builder<U> appRoutes(Function<AppRequest, U> authorize, BiFunction<String, U, AppOrder> resolveOrder) {
        return AppRoutes.builder(this, authorize, resolveOrder);
    }

    public static final class Builder {
        private String secret;
        private String baseUrl = DEFAULT_BASE_URL;
        private Transport transport;
        private long webhookTolerance = Signature.DEFAULT_TOLERANCE_SECONDS;
        private String defaultCurrency;

        private Builder() {
        }

        public Builder secret(String v) {
            secret = v;
            return this;
        }

        public Builder baseUrl(String v) {
            baseUrl = v;
            return this;
        }

        public Builder transport(Transport v) {
            transport = v;
            return this;
        }

        /** Fenêtre d'acceptation d'un avis, de 1 s à 1 h (défaut 300 s) : au-delà, un rejeu passerait. */
        public Builder webhookToleranceSeconds(long v) {
            if (v < 1 || v > 3600) {
                throw new TechnasPayException("invalid_request", "webhookToleranceSeconds : de 1 à 3600");
            }
            webhookTolerance = v;
            return this;
        }

        public Builder defaultCurrency(String v) {
            defaultCurrency = v;
            return this;
        }

        public TechnasPay build() {
            return new TechnasPay(secret, baseUrl, transport == null ? new JdkTransport() : transport, webhookTolerance, defaultCurrency);
        }
    }
}
