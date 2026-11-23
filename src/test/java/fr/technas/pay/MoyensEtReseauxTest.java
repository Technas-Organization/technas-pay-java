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
import fr.technas.pay.model.PaymentMethods;
import fr.technas.pay.model.PaymentMethodsRequest;
import fr.technas.pay.model.QuoteRequest;
import fr.technas.pay.webhook.Signature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Les moyens et les réseaux : listes fermées, réglages du compte, réseaux par paiement. */
class MoyensEtReseauxTest {

    static Map<String, Object> moyens() {
        Map<String, Object> mixx = new LinkedHashMap<>();
        mixx.put("network", "mixx");
        mixx.put("name", "Mixx by Yas");
        mixx.put("country", "TG");
        mixx.put("currency", "XOF");
        mixx.put("enabled", true);
        Map<String, Object> moov = new LinkedHashMap<>(mixx);
        moov.put("network", "moov");
        moov.put("name", "Moov Money");
        moov.put("enabled", false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("methods", List.of(Map.of("method", "mobile_money", "enabled", true), Map.of("method", "card", "enabled", false)));
        m.put("networks", List.of(mixx, moov));
        m.put("network_order", List.of("mixx", "moov"));
        m.put("all_networks", List.of("mixx", "moov"));
        return m;
    }

    @Test
    void enumerationsFermeesDansLOrdreDuServeur() {
        assertEquals(List.of("mobile_money", "card"), TechnasPay.PAYMENT_METHODS);
        assertEquals(23, TechnasPay.NETWORKS.size());
        assertEquals(List.of("mixx", "moov"), TechnasPay.NETWORKS.subList(0, 2));
        assertEquals("tpesa", TechnasPay.NETWORKS.get(22));
        assertFalse(TechnasPay.NETWORKS.contains("togocel"));
        assertEquals("orange_money", Network.ORANGE_MONEY);
        assertEquals(Network.ALL, TechnasPay.NETWORKS);
        assertEquals("card", PaymentMethod.CARD);
    }

    @Test
    void networksEnvoyeParPaiementSIlNEstPasVideEtLuSurLePaiement() {
        Map<String, Object> restreint = new LinkedHashMap<>();
        restreint.put("networks", List.of("mixx", "moov"));
        Map<String, Object> paye = new LinkedHashMap<>();
        paye.put("status", "completed");
        paye.put("network", "mixx");
        FakeTransport t = new FakeTransport().reply(201, payment(restreint)).reply(201, payment(Map.of()))
                .reply(200, payment(paye));
        TechnasPay pay = pay(t);
        Payment a = pay.askForPayment(req("r-1").networks(List.of(Network.MIXX, Network.MOOV)).build());
        Jsons.assertJsonEquals(Json.parse("[\"mixx\",\"moov\"]"), t.calls.get(0).json().get("networks"));
        assertNull(a.network());
        assertEquals(List.of("mixx", "moov"), a.networks());
        Payment b = pay.askForPayment(req("r-2").networks(List.of()).build());
        assertFalse(t.calls.get(1).json().has("networks"), "vide : pas envoyé");
        assertNull(b.network(), "serveur ancien : null");
        assertNull(b.networks(), "serveur ancien : null");
        Payment lu = pay.getPayment("pay_1");
        assertEquals("mixx", lu.network());
        assertNull(lu.networks());
        TechnasPayException e = assertThrows(TechnasPayException.class,
                () -> pay.askForPayment(req("r-3").networks(List.of("togocel")).build()));
        assertEquals("invalid_request", e.code());
        assertEquals(3, t.calls.size(), "refusé avant tout appel");
    }

    @Test
    void lireEtReglerLesMoyensEtLesReseaux() {
        FakeTransport t = new FakeTransport().reply(200, moyens()).reply(200, moyens()).reply(200, moyens()).reply(200, moyens());
        TechnasPay pay = pay(t);
        PaymentMethods m = pay.getPaymentMethods(" tg ");
        assertEquals("GET", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/payment-methods?country=TG", t.calls.get(0).url());
        assertEquals("mobile_money", m.methods().get(0).method());
        assertTrue(m.methods().get(0).enabled());
        assertFalse(m.methods().get(1).enabled());
        assertEquals("mixx", m.networks().get(0).network());
        assertEquals("Mixx by Yas", m.networks().get(0).name());
        assertEquals("TG", m.networks().get(0).country());
        assertEquals("XOF", m.networks().get(0).currency());
        assertFalse(m.networks().get(1).enabled());
        assertEquals(List.of("mixx", "moov"), m.networkOrder());
        pay.getPaymentMethods();
        assertEquals("https://gw.test/v1/payment-methods", t.calls.get(1).url());

        pay.setPaymentMethods(PaymentMethodsRequest.builder().methods(List.of(PaymentMethod.MOBILE_MONEY))
                .networkOrder(List.of(Network.MIXX, Network.MOOV)).build());
        assertEquals("PUT", t.calls.get(2).method());
        assertEquals("https://gw.test/v1/payment-methods", t.calls.get(2).url());
        Jsons.assertJsonEquals(Json.parse("{\"methods\":[\"mobile_money\"],\"network_order\":[\"mixx\",\"moov\"]}"),
                t.calls.get(2).json());
        pay.setPaymentMethods(PaymentMethodsRequest.builder().allNetworks().build());
        Jsons.assertJsonEquals(Json.parse("{\"networks\":null}"), t.calls.get(3).json());

        for (PaymentMethodsRequest bad : List.of(
                PaymentMethodsRequest.builder().methods(List.of("cash")).build(),
                PaymentMethodsRequest.builder().networks(List.of("togocel")).build(),
                PaymentMethodsRequest.builder().networkOrder(List.of("mixx", "flooz")).build())) {
            assertEquals("invalid_request", assertThrows(TechnasPayException.class, () -> pay.setPaymentMethods(bad)).code());
        }
        assertEquals(4, t.calls.size(), "refusé avant tout appel");
    }

    @Test
    void devisUnReseauInconnuDeCeSdkEstGardeTelQuel() {
        Map<String, Object> reseau = new LinkedHashMap<>();
        reseau.put("network", "nouveau_reseau");
        reseau.put("customer_total", 214);
        Map<String, Object> devis = new LinkedHashMap<>();
        devis.put("currency", "XOF");
        devis.put("amount", 200);
        devis.put("networks", List.of(reseau));
        FakeTransport t = new FakeTransport().reply(200, devis);
        assertEquals("nouveau_reseau",
                pay(t).quote(QuoteRequest.builder().amount(200).country("TG").build()).networks().get(0).network());
    }

    @ParameterizedTest
    @ValueSource(strings = {"method_not_enabled", "network_not_enabled", "network_not_available"})
    void refusDeMoyenOuDeReseau(String code) {
        FakeTransport t = new FakeTransport().reply(422, Map.of("code", code, "message", "refusé"));
        TechnasPayException e = assertThrows(TechnasPayException.class,
                () -> pay(t).askForPayment(req("r-1").networks(List.of(Network.MIXX)).build()));
        assertEquals(code, e.code());
        assertEquals(422, e.status());
    }

    @Test
    void avisDePaiementPorteLeReseauPaye() {
        TechnasPay pay = pay(new FakeTransport());
        String body = "{\"id\":\"e\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"r\","
                + "\"status\":\"completed\",\"amount\":200,\"currency\":\"XOF\",\"network\":\"mixx\"}}";
        assertEquals("mixx", pay.verifyWebhook(body, Signature.sign(body, WEBHOOK_SECRET)).payment().network());
        String ancien = "{\"id\":\"e\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"r\","
                + "\"status\":\"completed\",\"amount\":1000}}";
        assertNull(pay.verifyWebhook(ancien, Signature.sign(ancien, WEBHOOK_SECRET)).payment().network());
    }
}
