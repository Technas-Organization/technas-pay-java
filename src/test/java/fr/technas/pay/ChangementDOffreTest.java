package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.technas.pay.model.Payment;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Le client ouvre la page, ne paie pas, revient avec une AUTRE offre : nouvel essai, jamais un refus. */
class ChangementDOffreTest {
    @Test
    void essaiExpirePuisNouvelleOffre() {
        FakeTransport t = new FakeTransport()
                .reply(201, ClientTest.payment(Map.of("id", "pay_1", "reference", "F-1", "amount", 5000)))
                .reply(303, Map.of())
                .reply(200, ClientTest.payment(Map.of("id", "pay_1", "reference", "F-1", "amount", 5000, "status", "expired")))
                .reply(200, ClientTest.payment(Map.of("id", "pay_1", "reference", "F-1", "amount", 5000, "status", "expired")))
                .reply(201, ClientTest.payment(Map.of("id", "pay_2", "reference", "F-2", "amount", 7500)))
                .reply(409, Map.of("code", "reference_conflict", "message", "m"));
        TechnasPay pay = ClientTest.pay(t);

        Payment p1 = pay.askForPayment(ClientTest.req(Status.retryReference("F", 1)).amount(5000).build());
        assertEquals("expired", pay.simulateTestPayment(p1.id(), "expired").status());
        Payment relu = pay.getPayment(p1.id()); // au retour du client : on relit avant de répondre
        assertEquals("expired", relu.status());
        assertEquals(true, Status.canRetry(relu.status()));

        Payment p2 = pay.askForPayment(ClientTest.req(Status.retryReference("F", 2)).amount(7500).build());
        assertEquals("pending", p2.status());
        assertEquals("F-2", t.calls.get(4).json().get("reference").asText());
        assertEquals(0, new BigDecimal("7500").compareTo(t.calls.get(4).json().get("amount").decimalValue()));

        // Réutiliser la référence d'un autre montant : refus explicite, à ne pas rejouer tel quel.
        TechnasPayException e = assertThrows(TechnasPayException.class,
                () -> pay.askForPayment(ClientTest.req("F-1").amount(7500).build()));
        assertEquals("reference_conflict", e.code());
        assertEquals(409, e.status());
        assertFalse(e.retryable());
    }

    @Test
    void annulerLAncienEssaiPuisCreerLeNouveau() {
        Map<String, Object> annule = new java.util.LinkedHashMap<>(ClientTest.payment(Map.of("id", "pay_1", "reference", "F-1", "status", "expired",
                "canceled_at", "2026-11-16T09:00:00Z", "cancel_reason", "canceled_by_partner")));
        annule.put("checkout_url", null);
        FakeTransport t = new FakeTransport()
                .reply(200, annule)
                .reply(200, annule)
                .reply(409, Map.of("code", "payment_in_progress", "message", "m"))
                .reply(409, Map.of("code", "payment_already_completed", "message", "m"));
        TechnasPay pay = ClientTest.pay(t);
        Payment p = pay.cancelPayment("pay_1");
        assertEquals("POST", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/payments/pay_1/cancel", t.calls.get(0).url());
        assertEquals("expired", p.status());
        assertEquals(null, p.checkoutUrl());
        assertEquals("2026-11-16T09:00:00Z", p.canceledAt());
        assertEquals("canceled_by_partner", p.cancelReason());
        assertEquals(true, Status.canRetry(p.status()));
        assertEquals("2026-11-16T09:00:00Z", pay.cancelPayment("pay_1").canceledAt(), "rejouer rend le même résultat");
        TechnasPayException enCours = assertThrows(TechnasPayException.class, () -> pay.cancelPayment("pay_1"));
        assertEquals("payment_in_progress", enCours.code());
        TechnasPayException paye = assertThrows(TechnasPayException.class, () -> pay.cancelPayment("pay_1"));
        assertEquals("payment_already_completed", paye.code());
        assertFalse(paye.retryable());
        assertThrows(TechnasPayException.class, () -> pay.cancelPayment("../balance"));
        assertEquals(4, t.calls.size());
    }

    @Test
    void avisExpireDitPourquoi() {
        TechnasPay pay = ClientTest.pay(new FakeTransport());
        java.util.List<String> seen = new java.util.ArrayList<>();
        fr.technas.pay.webhook.WebhookHandlers h = fr.technas.pay.webhook.WebhookHandlers.builder()
                .onExpired(e -> seen.add(e.payment().reason() + "/" + e.payment().canceledAt())).build();
        for (String data : new String[] {
            "{\"id\":\"p\",\"reference\":\"F-1\",\"status\":\"expired\",\"amount\":1,\"currency\":\"XOF\",\"reason\":\"canceled\",\"canceled_at\":\"2026-11-16T09:00:00Z\"}",
            "{\"id\":\"p\",\"reference\":\"F-1\",\"status\":\"expired\",\"amount\":1,\"currency\":\"XOF\",\"reason\":\"timeout\"}",
            "{\"id\":\"p\",\"reference\":\"F-1\",\"status\":\"expired\",\"amount\":1,\"currency\":\"XOF\"}"}) {
            String body = "{\"id\":\"e\",\"type\":\"payment.expired\",\"created_at\":\"x\",\"data\":" + data + "}";
            pay.handleWebhook(body, fr.technas.pay.webhook.Signature.sign(body, ClientTest.WEBHOOK_SECRET), h);
        }
        assertEquals(java.util.List.of("canceled/2026-11-16T09:00:00Z", "timeout/null", "null/null"), seen);
    }
}
