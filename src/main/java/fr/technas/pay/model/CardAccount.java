package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Où en est votre compte de paiement par carte : {@code not_started}, {@code onboarding},
 * {@code under_review}, {@code restricted} ({@code requirements}, {@code disabledReason}) ou
 * {@code enabled}. {@code canAcceptPayments} est la seule condition pour payer par carte.
 */
public record CardAccount(
        String status, boolean canAcceptPayments, boolean payoutsEnabled, boolean detailsSubmitted, String country,
        List<String> requirements, String disabledReason, boolean testMode, String updatedAt) {

    public static CardAccount fromJson(JsonNode r) {
        return new CardAccount(
                Json.str(r, "status"), Json.bool(r, "can_accept_payments"), Json.bool(r, "payouts_enabled"),
                Json.bool(r, "details_submitted"), Json.nstr(r, "country"), Json.strings(r, "requirements"),
                Json.nstr(r, "disabled_reason"), Json.bool(r, "test_mode"), Json.nstr(r, "updated_at"));
    }

    /** Ce que l'app voit du compte carte : ni pays, ni identifiants. */
    public Map<String, Object> toAppJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("can_accept_payments", canAcceptPayments);
        m.put("payouts_enabled", payoutsEnabled);
        m.put("requirements", requirements);
        m.put("disabled_reason", disabledReason);
        return m;
    }
}
