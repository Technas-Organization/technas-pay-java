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
}
