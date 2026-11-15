package fr.technas.pay.app;

import fr.technas.pay.internal.Ascii;
import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.Status;
import fr.technas.pay.TechnasPay;
import fr.technas.pay.TechnasPayException;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.CardOnboardingLink;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.PaymentRequest;
import fr.technas.pay.webhook.WebhookHandlers;
import fr.technas.pay.webhook.WebhookResult;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes toutes faites pour une app ({@code technas_pay_flutter}, {@code TechnasPayClient}) : contrat
 * {@code contract/app-contract.v1.json}, rejoué ici ET par les SDK Node, Python, Dart, PHP et le client
 * Flutter.
 *
 * <p>Routes : {@code GET /methods?orderId=}, {@code POST /payments} {@code {orderId, method?}},
 * {@code GET /payments/{id}}, {@code POST /webhook}, et pour le marchand {@code GET /card/account},
 * {@code POST /card/onboarding}. Vous ne fournissez que {@code authorize} et {@code resolveOrder}. Le
 * montant ne vient jamais de l'app. Les essais expirés sont sautés tout seuls.
 */
public final class AppRoutes {
    private static final Pattern PAYMENT_PATH = Pattern.compile("^/payments/([^/]+)$");
    private static final Pattern ATTEMPT = Pattern.compile("^(.+?)-(?:card-)?\\d+$");
    /** Identifiant de commande ou de paiement accepté de l'app : court, sans « / », « . » ni espace. */
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    private final TechnasPay pay;
    private final Function<AppRequest, ?> authorize;
    private final BiFunction<String, Object, AppOrder> resolveOrder;
    private final String returnUrl;
    private final WebhookHandlers webhooks;
    private final boolean card;
    private final Function<AppRequest, ?> authorizeAdmin;
    private final int maxAttempts;

    @SuppressWarnings("unchecked")
    private <U> AppRoutes(Builder<U> b) {
        this.pay = b.pay;
        this.authorize = b.authorize;
        this.resolveOrder = (id, user) -> b.resolveOrder.apply(id, (U) user);
        this.returnUrl = b.returnUrl;
        this.webhooks = b.webhooks;
        this.card = b.card;
        this.authorizeAdmin = b.authorizeAdmin;
        this.maxAttempts = b.maxAttempts;
    }

    public static <U> Builder<U> builder(TechnasPay pay, Function<AppRequest, U> authorize, BiFunction<String, U, AppOrder> resolveOrder) {
        return new Builder<>(pay, authorize, resolveOrder);
    }

    private static AppResponse err(int status, String code, String message, boolean retryable) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("message", message);
        b.put("retryable", retryable);
        return new AppResponse(status, b);
    }

    private static AppResponse err(int status, String code, String message) {
        return err(status, code, message, false);
    }

    private static AppResponse fromPayError(TechnasPayException e) {
        // Panne ou réseau côté Technas : 502, à relire plus tard. Sinon le refus est transmis tel quel.
        if ("network_error".equals(e.code()) || e.status() >= 500 || e.status() == 0) {
            return err(502, e.code(), e.getMessage(), true);
        }
        // Clé du MARCHAND refusée par Technas : jamais un 401/403 à l'app (elle croirait l'utilisateur
        // déconnecté), ni le détail du compte marchand.
        if (e.status() == 401 || e.status() == 403) {
            return err(502, "upstream_error", "Service de paiement indisponible", false);
        }
        return err(e.status(), e.code(), e.getMessage(), e.retryable());
    }

    /**
     * La commande d'un paiement : {@code metadata.order_id} (posé par ces routes) d'abord, sinon lue
     * dans la référence. La référence doit être EXACTEMENT {@code orderId-N} ou {@code orderId-card-N} :
     * sinon {@code null} (« x-card-1 » ne peut pas être pris pour une commande « x » si le paiement
     * dit « x-card »).
     */
    private static String orderIdOf(Payment payment) {
        Object meta = payment.metadata() == null ? null : payment.metadata().get("order_id");
        String reference = payment.reference();
        String candidate;
        if (meta instanceof String s) {
            candidate = s;
        } else {
            Matcher m = ATTEMPT.matcher(reference);
            if (!m.matches()) {
                return null;
            }
            candidate = m.group(1);
        }
        return reference.matches(Pattern.quote(candidate) + "-(?:card-)?\\d+") ? candidate : null;
    }

    /** {@code null}, {@code false} et {@code Optional.empty()} refusent ; un {@code Optional} plein rend sa valeur. */
    private static Object granted(Object v) {
        if (v instanceof java.util.Optional<?> o) {
            return o.orElse(null);
        }
        return Boolean.FALSE.equals(v) ? null : v;
    }

    private Payment start(String orderId, AppOrder order, String method) {
        // Une référence = un essai : orderId-1, -2… (carte : orderId-card-1…). Un essai expiré est sauté ;
        // un essai en cours (ou payé) est rendu tel quel : jamais deux paiements.
        for (int attempt = 1; ; attempt++) {
            Map<String, Object> meta = new HashMap<>(order.metadata() == null ? Map.of() : order.metadata());
            meta.put("order_id", orderId);
            PaymentRequest.Builder r = PaymentRequest.builder()
                    .amount(order.amount())
                    .reference("card".equals(method) ? orderId + "-card-" + attempt : Status.retryReference(orderId, attempt))
                    .customer(order.customer())
                    .returnUrl(order.returnUrl() != null ? order.returnUrl() : returnUrl == null ? "" : returnUrl)
                    .method(method)
                    .currency(order.currency())
                    .description(order.description())
                    .metadata(meta);
            try {
                return pay.askForPayment(r.build());
            } catch (TechnasPayException e) {
                // Essai expiré, ou référence d'un autre montant (le client a changé d'offre) : essai
                // suivant. Un essai non payé ne bloque jamais un nouvel essai.
                if (("payment_expired".equals(e.code()) || "reference_conflict".equals(e.code())) && attempt < maxAttempts) {
                    continue;
                }
                throw e;
            }
        }
    }

    private Map<String, Object> cardMethod(AppOrder order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", "card");
        if (!card) {
            m.put("available", false);
            m.put("reason", "not_enabled");
        } else if (order.currency() == null) {
            m.put("available", false);
            m.put("reason", "currency_required");
        } else if (pay.getCardAccount().canAcceptPayments()) {
            m.put("available", true);
            m.put("reason", null);
        } else {
            m.put("available", false);
            m.put("reason", "card_account_not_ready");
        }
        return m;
    }

    private static JsonNode body(AppRequest req) {
        if (req.rawBody() == null || req.rawBody().isEmpty()) {
            return Json.object();
        }
        try {
            JsonNode n = Json.parse(req.rawBody());
            return n.isObject() ? n : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AppResponse cardRoutes(AppRequest req, String path) {
        if (authorizeAdmin == null) {
            return err(404, "not_found", "Route inconnue");
        }
        Object admin = granted(authorizeAdmin.apply(req));
        if (admin == null) {
            return err(403, "forbidden", "Réservé au marchand");
        }
        try {
            if ("/card/account".equals(path)) {
                return new AppResponse(200, Map.of("account", pay.getCardAccount().toAppJson()));
            }
            JsonNode b = body(req);
            if (b == null) {
                return err(400, "invalid_request", "Corps JSON illisible");
            }
            String ret = Json.nstr(b, "returnUrl");
            String refresh = Json.nstr(b, "refreshUrl");
            if (ret == null || refresh == null) {
                return err(400, "invalid_request", "returnUrl et refreshUrl sont obligatoires");
            }
            CardOnboardingLink link = pay.startCardOnboarding(ret, refresh, Json.nstr(b, "country"), Json.nstr(b, "email"), Json.nstr(b, "businessType"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("url", link.url());
            out.put("kind", link.kind());
            out.put("expires_at", link.expiresAt());
            out.put("account", link.account().toAppJson());
            return new AppResponse(200, out);
        } catch (TechnasPayException e) {
            return fromPayError(e);
        }
    }

    /** Le cœur, sans framework. */
    public AppResponse handle(AppRequest req) {
        String path = "/" + req.path().replaceAll("^/+|/+$", "");
        String method = Ascii.upper(req.method());

        if ("POST".equals(method) && "/webhook".equals(path)) {
            if (webhooks == null) {
                return err(404, "not_found", "Webhook non branché");
            }
            WebhookResult r = pay.handleWebhook(req.rawBody() == null ? "" : req.rawBody(), req.header("x-technas-payment-signature"), webhooks);
            return new AppResponse(r.status(), r.body());
        }
        if (("GET".equals(method) && "/card/account".equals(path)) || ("POST".equals(method) && "/card/onboarding".equals(path))) {
            return cardRoutes(req, path);
        }
        Matcher pm = PAYMENT_PATH.matcher(path);
        boolean isPayment = pm.matches();
        boolean known = ("GET".equals(method) && ("/methods".equals(path) || isPayment)) || ("POST".equals(method) && "/payments".equals(path));
        if (!known) {
            return err(404, "not_found", "Route inconnue");
        }
        Object user = granted(authorize.apply(req));
        if (user == null) {
            return err(401, "unauthorized", "Connexion requise");
        }
        try {
            if ("/methods".equals(path)) {
                String orderId = req.query().getOrDefault("orderId", "");
                if (orderId.isEmpty()) {
                    return err(400, "invalid_request", "orderId manquant");
                }
                if (!ID.matcher(orderId).matches()) {
                    return err(404, "order_not_found", "Commande introuvable");
                }
                AppOrder order = resolveOrder.apply(orderId, user);
                if (order == null) {
                    return err(404, "order_not_found", "Commande introuvable");
                }
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("method", "mobile_money");
                mm.put("available", true);
                mm.put("reason", null);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("amount", order.amount());
                out.put("currency", order.currency());
                out.put("methods", List.of(mm, cardMethod(order)));
                return new AppResponse(200, out);
            }
            if ("/payments".equals(path)) {
                JsonNode b = body(req);
                if (b == null) {
                    return err(400, "invalid_request", "Corps JSON illisible");
                }
                JsonNode idNode = b.get("orderId");
                String orderId = idNode != null && (idNode.isTextual() || idNode.isIntegralNumber()) ? idNode.asText() : "";
                if (orderId.isEmpty()) {
                    return err(400, "invalid_request", "orderId manquant");
                }
                if (!ID.matcher(orderId).matches()) {
                    return err(404, "order_not_found", "Commande introuvable");
                }
                JsonNode mNode = b.get("method");
                String payMethod = mNode == null || mNode.isNull() ? "mobile_money" : mNode.isTextual() ? mNode.asText() : "?";
                if (!"mobile_money".equals(payMethod) && !"card".equals(payMethod)) {
                    return err(400, "invalid_request", "method vaut \"mobile_money\" ou \"card\"");
                }
                if ("card".equals(payMethod) && !card) {
                    return err(400, "method_not_available", "Le paiement par carte n'est pas ouvert");
                }
                AppOrder order = resolveOrder.apply(orderId, user);
                if (order == null) {
                    return err(404, "order_not_found", "Commande introuvable");
                }
                if ("card".equals(payMethod) && order.currency() == null) {
                    return err(400, "currency_required", "La commande n'a pas de devise : carte impossible");
                }
                return new AppResponse(200, Map.of("payment", start(orderId, order, payMethod).toAppJson()));
            }
            // GET /payments/{id} : seulement si la commande de ce paiement est à cet utilisateur.
            String id = URLDecoder.decode(pm.group(1), StandardCharsets.UTF_8);
            if (!ID.matcher(id).matches()) {
                return err(404, "payment_not_found", "Paiement introuvable");
            }
            Payment payment;
            try {
                payment = pay.getPayment(id);
            } catch (TechnasPayException e) {
                if ("payment_not_found".equals(e.code())) {
                    return err(404, "payment_not_found", "Paiement introuvable");
                }
                throw e;
            }
            String orderId = orderIdOf(payment);
            if (orderId == null || resolveOrder.apply(orderId, user) == null) {
                return err(404, "payment_not_found", "Paiement introuvable");
            }
            return new AppResponse(200, Map.of("payment", payment.toAppJson()));
        } catch (TechnasPayException e) {
            return fromPayError(e);
        }
    }

    public static final class Builder<U> {
        private final TechnasPay pay;
        private final Function<AppRequest, U> authorize;
        private final BiFunction<String, U, AppOrder> resolveOrder;
        private String returnUrl;
        private WebhookHandlers webhooks;
        private boolean card;
        private Function<AppRequest, ?> authorizeAdmin;
        private int maxAttempts = 20;

        private Builder(TechnasPay pay, Function<AppRequest, U> authorize, BiFunction<String, U, AppOrder> resolveOrder) {
            this.pay = pay;
            this.authorize = authorize;
            this.resolveOrder = resolveOrder;
        }

        /** Page de retour par défaut (https, ou lien qui rouvre l'app). */
        public Builder<U> returnUrl(String v) {
            returnUrl = v;
            return this;
        }

        /** Vos fonctions d'avis. Route : POST /webhook. Sans elles, la route n'existe pas. */
        public Builder<U> webhooks(WebhookHandlers v) {
            webhooks = v;
            return this;
        }

        /** Proposer la carte (si votre compte carte est prêt et que la commande a une {@code currency}). */
        public Builder<U> card(boolean v) {
            card = v;
            return this;
        }

        /** Le MARCHAND, pour les routes du compte carte ; {@code null} → 403. Sans lui, ces routes n'existent pas. */
        public Builder<U> authorizeAdmin(Function<AppRequest, ?> v) {
            authorizeAdmin = v;
            return this;
        }

        public Builder<U> maxAttempts(int v) {
            maxAttempts = v;
            return this;
        }

        public AppRoutes build() {
            return new AppRoutes(this);
        }
    }
}
