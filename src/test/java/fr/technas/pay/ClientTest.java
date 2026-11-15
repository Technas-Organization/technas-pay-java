package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.Customer;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.PaymentRequest;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookHandlers;
import fr.technas.pay.webhook.WebhookVerificationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientTest {
    static final String API_KEY = "pk_abc-DEF_123";
    static final String WEBHOOK_SECRET = "ws_webhook-secret_456";
    static final String SECRET = "tps_test_" + API_KEY + "." + WEBHOOK_SECRET;
    static final Customer CUSTOMER = Customer.of("awa@exemple.tg", "Awa Mensah");

    static Map<String, Object> payment(Map<String, Object> over) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", "pay_1");
        p.put("reference", "42-1");
        p.put("amount", 5000);
        p.put("currency", null);
        p.put("commission", 100);
        p.put("net_amount", 4900);
        p.put("status", "pending");
        p.put("checkout_url", "https://pay-api.technas.fr/checkout/pay_1");
        p.put("metadata", Map.of());
        p.put("test_mode", true);
        p.putAll(over);
        return p;
    }

    static TechnasPay pay(FakeTransport t) {
        return pay(t, SECRET);
    }

    static TechnasPay pay(FakeTransport t, String secret) {
        return TechnasPay.builder().secret(secret).baseUrl("https://gw.test/").transport(t).build();
    }

    static PaymentRequest.Builder req(String reference) {
        return PaymentRequest.builder().amount(5000).reference(reference).customer(CUSTOMER).returnUrl("https://p.test/merci");
    }

    @Test
    void seuleLaCleApiPartEtDansUnEnTete() {
        FakeTransport t = new FakeTransport().reply(201, payment(Map.of()));
        Payment p = pay(t).askForPayment(req("42-1").build());
        FakeTransport.Call call = t.calls.get(0);
        assertEquals("https://gw.test/v1/payments", call.url());
        assertEquals("Bearer " + API_KEY, call.headers().get("Authorization"));
        String all = call.url() + call.headers() + call.body();
        assertFalse(all.contains(WEBHOOK_SECRET));
        assertFalse(all.contains("tps_test_"));
        JsonNode body = call.json();
        assertFalse(body.has("currency"), "zone CFA : le client choisit son pays");
        assertEquals("https://p.test/merci", body.get("return_url").asText());
        assertEquals(0, new BigDecimal("4900").compareTo(p.netAmount()));
        assertEquals("mobile_money", p.method());
        assertFalse(pay(t).toString().contains(API_KEY));
    }

    @Test
    void laDeviseSuitLePaysCfa() {
        FakeTransport t = new FakeTransport().reply(201, payment(Map.of())).reply(201, payment(Map.of()));
        TechnasPay pay = pay(t);
        pay.askForPayment(req("r").customer(new Customer("a@b.c", "A", null, "cm")).build());
        assertEquals("XAF", t.calls.get(0).json().get("currency").asText());
        pay.askForPayment(req("r2").customer(new Customer("a@b.c", "A", null, "GH")).build());
        assertFalse(t.calls.get(1).json().has("currency"));
    }

    @Test
    void montantsRefusesAvantToutAppel() {
        FakeTransport t = new FakeTransport();
        for (BigDecimal bad : List.of(new BigDecimal("50.5"), BigDecimal.ZERO, BigDecimal.valueOf(-1))) {
            TechnasPayException e = assertThrows(TechnasPayException.class, () -> pay(t).askForPayment(req("r").amount(bad).build()));
            assertEquals("invalid_amount", e.code());
            assertEquals(0, e.status());
        }
        assertTrue(t.calls.isEmpty());
    }

    @Test
    void carteDeviseDecimalesEtCompte() {
        Map<String, Object> cardAccountNotReady = new LinkedHashMap<>();
        cardAccountNotReady.put("code", "card_account_not_ready");
        cardAccountNotReady.put("message", "m");
        cardAccountNotReady.put("account_status", "onboarding");
        FakeTransport t = new FakeTransport()
                .reply(201, payment(Map.of("method", "card", "amount", 12.5, "currency", "EUR")))
                .reply(200, Map.of("status", "enabled", "can_accept_payments", true))
                .reply(200, Map.of("url", "https://dashboard.stripe.com/"))
                .reply(409, cardAccountNotReady);
        TechnasPay pay = pay(t);
        TechnasPayException noCurrency = assertThrows(TechnasPayException.class,
                () -> pay.askForPayment(req("r").method("card").amount(new BigDecimal("12.5")).build()));
        assertEquals("currency_required", noCurrency.code());
        Payment p = pay.askForPayment(req("r-card-1").method("card").amount(new BigDecimal("12.5")).currency("eur").build());
        assertEquals("card", p.method());
        JsonNode sent = t.calls.get(0).json();
        assertEquals("card", sent.get("method").asText());
        assertEquals("EUR", sent.get("currency").asText());
        assertEquals(0, new BigDecimal("12.5").compareTo(sent.get("amount").decimalValue()));
        assertTrue(pay.getCardAccount().canAcceptPayments());
        assertEquals("https://dashboard.stripe.com/", pay.getCardDashboardLink());
        TechnasPayException e = assertThrows(TechnasPayException.class, () -> pay.startCardOnboarding("https://a", "https://b", "fr", null, null));
        assertEquals("onboarding", e.details().get("accountStatus"));
        assertEquals("FR", t.calls.get(3).json().get("country").asText());
    }

    @Test
    void erreursRejouablesOuNon() {
        FakeTransport t = new FakeTransport().reply(409, Map.of("code", "payment_expired", "message", "m")).reply(503, null).reply(0, null);
        TechnasPay pay = pay(t);
        for (Object[] x : new Object[][] {{"payment_expired", false, 409}, {"request_failed", true, 503}, {"network_error", true, 0}}) {
            TechnasPayException e = assertThrows(TechnasPayException.class, pay::getBalance);
            assertEquals(x[0], e.code());
            assertEquals(x[1], e.retryable());
            assertEquals(x[2], e.status());
        }
    }

    @Test
    void secretMalFormeNeFuitPas() {
        TechnasPayException e = assertThrows(TechnasPayException.class, () -> pay(new FakeTransport(), "mon-secret-en-clair").getBalance());
        assertEquals("invalid_secret", e.code());
        assertFalse(e.getMessage().contains("mon-secret"));
    }

    @Test
    void identifiantEchappeEtSimulation() {
        FakeTransport t = new FakeTransport()
                .reply(404, Map.of("code", "payment_not_found", "message", "m"))
                .reply(303, Map.of())
                .reply(200, payment(Map.of("status", "completed")));
        TechnasPay pay = pay(t);
        assertThrows(TechnasPayException.class, () -> pay.getPayment("../balance"));
        assertEquals("https://gw.test/v1/payments/..%2Fbalance", t.calls.get(0).url());
        assertEquals("completed", pay.simulateTestPayment("pay_1", "completed").status());
        assertFalse(t.calls.get(1).headers().containsKey("Authorization"), "la page de test ne reçoit pas la clé");
        TechnasPayException live = assertThrows(TechnasPayException.class,
                () -> pay(new FakeTransport(), SECRET.replace("tps_test_", "tps_live_")).simulateTestPayment("p", "completed"));
        assertTrue(live.getMessage().contains("tps_test_"));
    }

    @Test
    void webhooks() {
        TechnasPay pay = pay(new FakeTransport());
        Map<String, String> hook = pay.createTestWebhook("payment.completed", "42-1", BigDecimal.valueOf(5000), "XOF");
        List<String> seen = new ArrayList<>();
        var r = pay.handleWebhook(hook.get("body"), hook.get(Signature.HEADER),
                WebhookHandlers.builder().onCompleted(e -> seen.add(e.payment().reference())).build());
        assertEquals(200, r.status());
        assertEquals(List.of("42-1"), seen);
        WebhookHandlers never = WebhookHandlers.builder().onCompleted(e -> { throw new AssertionError("appelée"); }).build();
        for (String sig : new String[] {null, "nimporte", Signature.sign(hook.get("body"), "autre"),
                Signature.sign(hook.get("body"), WEBHOOK_SECRET, System.currentTimeMillis() / 1000 - 3600)}) {
            assertEquals(400, pay.handleWebhook(hook.get("body"), sig, never).status());
        }
        assertThrows(WebhookVerificationException.class,
                () -> pay.verifyWebhook(hook.get("body").replace("5000", "9"), hook.get(Signature.HEADER)));
    }

    @Test
    void famillesDEvenementsEtFonctionQuiEchoue() {
        TechnasPay pay = pay(new FakeTransport());
        List<String> seen = new ArrayList<>();
        WebhookHandlers h = WebhookHandlers.builder()
                .onRefund(e -> seen.add("refund:" + e.refund().kind()))
                .onCardAccountUpdated(e -> seen.add("card:" + (e.cardAccount().canAcceptPayments() ? "oui" : "non")))
                .onExpired(e -> seen.add("expired:" + e.payment().reference()))
                .onTopUp(e -> seen.add("topup:" + e.data().get("amount")))
                .build();
        String[][] events = {
            {"refund.completed", "{\"id\":\"re\",\"payment_id\":\"p\",\"payment_reference\":\"r\",\"amount\":1,\"currency\":\"EUR\",\"status\":\"completed\",\"kind\":\"card\"}"},
            {"refund.completed", "{\"id\":\"re2\",\"payment_id\":\"p\",\"payment_reference\":\"r\",\"amount\":1,\"currency\":\"EUR\",\"status\":\"completed\",\"kind\":\"stripe\"}"},
            {"card_account.updated", "{\"status\":\"enabled\",\"can_accept_payments\":true}"},
            {"payment.expired", "{\"id\":\"p\",\"reference\":\"r-1\",\"status\":\"expired\",\"amount\":1,\"currency\":\"XOF\"}"},
            {"balance.topped_up", "{\"id\":\"t\",\"amount\":5,\"currency\":\"XOF\"}"},
            {"nouveau.type", "{\"k\":1}"},
        };
        for (String[] ev : events) {
            String body = "{\"id\":\"e\",\"type\":\"" + ev[0] + "\",\"created_at\":\"x\",\"data\":" + ev[1] + "}";
            assertEquals(200, pay.handleWebhook(body, Signature.sign(body, WEBHOOK_SECRET), h).status());
        }
        assertEquals(List.of("refund:card", "refund:card", "card:oui", "expired:r-1", "topup:5"), seen);
        String body = "{\"id\":\"e\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"r\",\"status\":\"completed\"}}";
        WebhookHandlers failing = WebhookHandlers.builder().onCompleted(e -> { throw new IllegalStateException("base en panne"); }).build();
        assertThrows(IllegalStateException.class, () -> pay.handleWebhook(body, Signature.sign(body, WEBHOOK_SECRET), failing));
    }

    @Test
    void regles() {
        assertTrue(Status.canRetry("expired"));
        assertFalse(Status.canRetry("failed"));
        assertTrue(Status.isFinal("amount_mismatch"));
        assertEquals("CMD-42-2", Status.retryReference("CMD-42", 2));
        assertEquals("XOF", Status.currencyForCountry(" tg "));
        assertNull(Status.currencyForCountry("GH"));
    }

    @Test
    void reponseJsonExposeeTelleQuelle() {
        FakeTransport t = new FakeTransport().reply(200, Map.of("XOF", Map.of("available", 1000)));
        Object balance = pay(t).getBalance();
        assertEquals("{\"XOF\":{\"available\":1000}}", Json.write(balance));
    }
}
