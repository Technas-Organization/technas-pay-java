package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.technas.pay.model.AccountKyc;
import fr.technas.pay.model.KycNotice;
import fr.technas.pay.model.PaymentRequest;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookHandlers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Le seuil d'identité : GET /v1/account/kyc, l'avis account.kyc_required, le refus 409 kyc_required. */
class SeuilKycTest {
    static TechnasPay pay(FakeTransport t) {
        return ClientTest.pay(t);
    }

    @Test
    void etatDuSeuilLuEnEntier() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("applies", true);
        r.put("stage", "soon");
        r.put("level", "none");
        r.put("days_elapsed", 24);
        r.put("warn_from_xof", 800000);
        r.put("warn_from_date", "2026-11-28T00:00:00+00:00");
        r.put("collected_xof", 850000);
        r.put("threshold_xof", 1000000);
        r.put("grace_days", 30);
        r.put("first_collected_at", "2026-11-05T10:00:00+00:00");
        r.put("deadline", "2026-12-05T10:00:00+00:00");
        r.put("waiver_until", null);
        r.put("blocked", false);
        r.put("reason", null);
        r.put("console_url", "https://console.pay.technas.fr/identite");
        FakeTransport t = new FakeTransport().reply(200, r);
        AccountKyc k = pay(t).getAccountKyc();
        assertEquals("GET", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/account/kyc", t.calls.get(0).url());
        assertEquals("Bearer " + ClientTest.API_KEY, t.calls.get(0).headers().get("Authorization"));
        assertTrue(k.applies());
        assertEquals("soon", k.stage());
        assertEquals("none", k.level());
        assertEquals(24, k.daysElapsed());
        assertEquals(0, new BigDecimal("850000").compareTo(k.collectedXof()));
        assertEquals(0, new BigDecimal("1000000").compareTo(k.thresholdXof()));
        assertEquals(0, new BigDecimal("800000").compareTo(k.warnFromXof()));
        assertEquals(30, k.graceDays());
        assertEquals("2026-12-05T10:00:00+00:00", k.deadline());
        assertNull(k.waiverUntil());
        assertNull(k.reason());
        assertFalse(k.blocked());
        assertEquals("https://console.pay.technas.fr/identite", k.consoleUrl());
    }

    @Test
    void compteHorsSeuilEtValeursInconnues() {
        FakeTransport t = new FakeTransport().reply(200, Map.of("applies", false, "stage", "nouvelle_etape", "level", "business",
                "blocked", false, "console_url", "https://c"));
        AccountKyc k = pay(t).getAccountKyc();
        assertFalse(k.applies());
        assertEquals("nouvelle_etape", k.stage());
        assertNull(k.daysElapsed());
        assertNull(k.graceDays());
        assertNull(k.deadline());
    }

    @Test
    void avisSigneAppelleOnKycRequired() {
        TechnasPay pay = pay(new FakeTransport());
        List<KycNotice> seen = new ArrayList<>();
        String body = "{\"id\":\"e\",\"type\":\"account.kyc_required\",\"created_at\":\"x\",\"data\":{\"reason\":\"amount_80\","
                + "\"collected_xof\":800000,\"threshold_xof\":1000000,\"deadline\":\"2026-12-05T10:00:00+00:00\",\"blocked\":false,"
                + "\"console_url\":\"https://c\"}}";
        var r = pay.handleWebhook(body, Signature.sign(body, ClientTest.WEBHOOK_SECRET),
                WebhookHandlers.builder().onKycRequired(e -> seen.add(e.kyc())).build());
        assertEquals(200, r.status());
        assertEquals(1, seen.size());
        assertEquals("amount_80", seen.get(0).reason());
        assertEquals(0, new BigDecimal("800000").compareTo(seen.get(0).collectedXof()));
        assertEquals("https://c", seen.get(0).consoleUrl());
        assertFalse(seen.get(0).blocked());
        assertNull(r.event().payment());
        assertNull(seen.get(0).stage(), "avis ancien : champs complémentaires nuls");
        assertNull(seen.get(0).daysElapsed());
    }

    @Test
    void avisCompletLuEnEntier() {
        TechnasPay pay = pay(new FakeTransport());
        List<KycNotice> seen = new ArrayList<>();
        String body = "{\"id\":\"e2\",\"type\":\"account.kyc_required\",\"created_at\":\"x\",\"data\":{\"reason\":\"blocked\","
                + "\"collected_xof\":1000000,\"threshold_xof\":1000000,\"deadline\":null,\"blocked\":true,\"console_url\":\"https://c\","
                + "\"stage\":\"blocked\",\"block_reason\":\"amount\",\"days_elapsed\":12,\"grace_days\":30,"
                + "\"first_collected_at\":\"2026-11-03T10:00:00+00:00\"}}";
        pay.handleWebhook(body, Signature.sign(body, ClientTest.WEBHOOK_SECRET), WebhookHandlers.builder().onKycRequired(e -> seen.add(e.kyc())).build());
        KycNotice k = seen.get(0);
        assertEquals("blocked", k.reason());
        assertEquals("blocked", k.stage());
        assertEquals("amount", k.blockReason());
        assertEquals(12, k.daysElapsed());
        assertEquals(30, k.graceDays());
        assertEquals("2026-11-03T10:00:00+00:00", k.firstCollectedAt());
        assertTrue(k.blocked());
        assertNull(k.deadline());
    }

    @Test
    void refusKycRequiredAvecSesPrecisions() {
        Map<String, Object> refus = new LinkedHashMap<>();
        refus.put("code", "kyc_required");
        refus.put("message", "Identity verification is required");
        refus.put("console_url", "https://c");
        refus.put("reason", "amount");
        Map<String, Object> contrat = new LinkedHashMap<>();
        contrat.put("code", "contract_required");
        contrat.put("message", "Accept the agreement");
        contrat.put("version", "2026-11");
        FakeTransport t = new FakeTransport().reply(409, refus).reply(409, contrat);
        TechnasPay pay = pay(t);
        PaymentRequest req = ClientTest.req("42-1").build();
        TechnasPayException e = assertThrows(TechnasPayException.class, () -> pay.askForPayment(req));
        assertEquals("kyc_required", e.code());
        assertEquals("https://c", e.details().get("consoleUrl"));
        assertEquals("amount", e.details().get("reason"));
        assertFalse(e.retryable());
        TechnasPayException c = assertThrows(TechnasPayException.class, () -> pay.askForPayment(req));
        assertEquals("contract_required", c.code());
        assertEquals("2026-11", c.details().get("version"));
    }
}
