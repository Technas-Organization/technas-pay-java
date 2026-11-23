package fr.technas.pay;

import static fr.technas.pay.ClientTest.WEBHOOK_SECRET;
import static fr.technas.pay.ClientTest.pay;
import static fr.technas.pay.ClientTest.payment;
import static fr.technas.pay.ClientTest.req;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.technas.pay.internal.Json;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.Quote;
import fr.technas.pay.model.QuoteNetwork;
import fr.technas.pay.model.QuoteRequest;
import fr.technas.pay.webhook.Event;
import fr.technas.pay.webhook.Signature;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Qui paie les frais ({@code fees}), et combien paie le client ({@code quote}). */
class FraisEtDevisTest {

    static void eq(long expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), () -> "attendu " + expected + ", obtenu " + actual);
    }

    static Map<String, Object> devis() {
        Map<String, Object> reseau = new LinkedHashMap<>();
        reseau.put("network", "mixx");
        reseau.put("network_name", "Mixx by Yas");
        reseau.put("customer_total", 214);
        reseau.put("processing_fee", 9);
        reseau.put("commission", 5);
        reseau.put("net_amount", 200);
        reseau.put("operator_fee_estimate", 10);
        reseau.put("otp_required", false);
        reseau.put("otp_instructions", null);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("method", "mobile_money");
        q.put("currency", "XOF");
        q.put("country", "TG");
        q.put("fees_paid_by", "customer");
        q.put("amount", 200);
        q.put("customer_amount", 205);
        q.put("customer_total", 214);
        q.put("processing_fee", 9);
        q.put("commission", 5);
        q.put("net_amount", 200);
        q.put("networks", List.of(reseau));
        return q;
    }

    @Test
    void feesEnvoyeSeulementSIlEstDonne() {
        FakeTransport t = new FakeTransport().reply(201, payment(Map.of())).reply(201, payment(Map.of()));
        TechnasPay pay = pay(t);
        pay.askForPayment(req("r-1").fees("customer").build());
        assertEquals("customer", t.calls.get(0).json().get("fees").asText());
        pay.askForPayment(req("r-2").build());
        assertFalse(t.calls.get(1).json().has("fees"), "absent : le réglage du compte");
        TechnasPayException e = assertThrows(TechnasPayException.class, () -> pay.askForPayment(req("r-3").fees("client").build()));
        assertEquals("invalid_request", e.code());
        assertEquals(2, t.calls.size(), "refusé avant tout appel");
    }

    @Test
    void paiementLitLesFraisEtUnServeurAncienRendLesDefauts() {
        Map<String, Object> over = new LinkedHashMap<>();
        over.put("amount", 200);
        over.put("customer_amount", 205);
        over.put("fees_paid_by", "customer");
        over.put("customer_total", 214);
        over.put("processing_fee", 9);
        over.put("net_amount", 200);
        FakeTransport t = new FakeTransport().reply(200, payment(over)).reply(200, payment(Map.of()));
        TechnasPay pay = pay(t);
        Payment p = pay.getPayment("pay_1");
        eq(200, p.amount());
        eq(205, p.customerAmount());
        assertEquals("customer", p.feesPaidBy());
        eq(214, p.customerTotal());
        eq(9, p.processingFee());
        eq(200, p.netAmount());
        Payment ancien = pay.getPayment("pay_1");
        eq(5000, ancien.customerAmount());
        assertEquals("partner", ancien.feesPaidBy());
        assertNull(ancien.customerTotal());
        assertNull(ancien.processingFee());
    }

    @Test
    void avisDePaiementLitLesFrais() {
        TechnasPay pay = pay(new FakeTransport());
        String body = "{\"id\":\"e\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"r\","
                + "\"status\":\"completed\",\"amount\":200,\"currency\":\"XOF\",\"commission\":5,\"net_amount\":200,"
                + "\"customer_amount\":205,\"fees_paid_by\":\"customer\",\"customer_total\":214,\"processing_fee\":9}}";
        Event e = pay.verifyWebhook(body, Signature.sign(body, WEBHOOK_SECRET));
        eq(205, e.payment().customerAmount());
        assertEquals("customer", e.payment().feesPaidBy());
        eq(214, e.payment().customerTotal());
        eq(9, e.payment().processingFee());
        String ancien = "{\"id\":\"e\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"r\","
                + "\"status\":\"completed\",\"amount\":1000}}";
        Payment p = pay.verifyWebhook(ancien, Signature.sign(ancien, WEBHOOK_SECRET)).payment();
        eq(1000, p.customerAmount());
        assertEquals("partner", p.feesPaidBy());
        assertNull(p.customerTotal());
        assertNull(p.processingFee());
    }

    @Test
    void quoteEtGetFeesEnvoientLeMontantLePaysEtLeModeEtLisentChaqueReseau() {
        FakeTransport t = new FakeTransport().reply(200, devis()).reply(200, devis());
        TechnasPay pay = pay(t);
        Quote q = pay.quote(QuoteRequest.builder().amount(200).country(" tg ").fees("customer").build());
        assertEquals("POST", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/quotes", t.calls.get(0).url());
        Jsons.assertJsonEquals(Json.parse("{\"amount\":200,\"country\":\"TG\",\"fees\":\"customer\"}"), t.calls.get(0).json());
        assertEquals("mobile_money", q.method());
        assertEquals("XOF", q.currency());
        assertEquals("TG", q.country());
        assertEquals("customer", q.feesPaidBy());
        eq(200, q.amount());
        eq(205, q.customerAmount());
        eq(214, q.customerTotal());
        eq(9, q.processingFee());
        eq(5, q.commission());
        eq(200, q.netAmount());
        assertEquals(1, q.networks().size());
        QuoteNetwork n = q.networks().get(0);
        assertEquals("mixx", n.network());
        assertEquals("Mixx by Yas", n.networkName());
        eq(214, n.customerTotal());
        eq(9, n.processingFee());
        eq(5, n.commission());
        eq(200, n.netAmount());
        eq(10, n.operatorFeeEstimate());
        assertFalse(n.otpRequired());
        assertNull(n.otpInstructions());

        pay.getFees(QuoteRequest.builder().amount(1000).country("TG").network("togocel").build());
        Jsons.assertJsonEquals(Json.parse("{\"amount\":1000,\"country\":\"TG\",\"network\":\"togocel\"}"), t.calls.get(1).json());
    }

    @Test
    void devisParCarteSansFraisDuReseauNiNet() {
        Map<String, Object> carte = new LinkedHashMap<>();
        carte.put("method", "card");
        carte.put("currency", "EUR");
        carte.put("country", null);
        carte.put("fees_paid_by", "partner");
        carte.put("amount", 20);
        carte.put("customer_amount", 20);
        carte.put("customer_total", 20);
        carte.put("processing_fee", null);
        carte.put("commission", 0.2);
        carte.put("net_amount", null);
        carte.put("networks", List.of());
        FakeTransport t = new FakeTransport().reply(200, carte);
        Quote q = pay(t).quote(QuoteRequest.builder().amount(new BigDecimal("20")).method("card").currency("eur").build());
        Jsons.assertJsonEquals(Json.parse("{\"amount\":20,\"method\":\"card\",\"currency\":\"EUR\"}"), t.calls.get(0).json());
        assertEquals("card", q.method());
        assertNull(q.country());
        eq(20, q.customerTotal());
        assertNull(q.processingFee());
        assertNull(q.netAmount());
        assertEquals(0, new BigDecimal("0.2").compareTo(q.commission()));
        assertTrue(q.networks().isEmpty());
    }

    @Test
    void montantTropPetitRendLeMinimum() {
        FakeTransport t = new FakeTransport().reply(422, Map.of("code", "amount_too_low", "message", "trop petit", "minimum_amount", 209));
        TechnasPayException e = assertThrows(TechnasPayException.class,
                () -> pay(t).quote(QuoteRequest.builder().amount(200).country("TG").fees("partner").build()));
        assertEquals("amount_too_low", e.code());
        assertEquals(422, e.status());
        assertEquals(0, new BigDecimal("209").compareTo((BigDecimal) e.details().get("minimumAmount")));
    }

    @Test
    void devisRefuseAvantToutAppel() {
        FakeTransport t = new FakeTransport();
        TechnasPay pay = pay(t);
        assertEquals("invalid_amount", assertThrows(TechnasPayException.class,
                () -> pay.quote(QuoteRequest.builder().amount(0).build())).code());
        assertEquals("invalid_amount", assertThrows(TechnasPayException.class,
                () -> pay.quote(QuoteRequest.builder().amount(new BigDecimal("200.5")).build())).code());
        assertEquals("invalid_request", assertThrows(TechnasPayException.class,
                () -> pay.quote(QuoteRequest.builder().amount(200).fees("client").build())).code());
        assertEquals("invalid_request", assertThrows(TechnasPayException.class,
                () -> pay.getFees(QuoteRequest.builder().amount(200).method("cheque").build())).code());
        assertTrue(t.calls.isEmpty());
    }
}
