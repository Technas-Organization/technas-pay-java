package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un paiement. Montants en unité principale, en décimal exact (5000 = 5 000 FCFA ; 12.5 = 12,50 €).
 *
 * @param status {@code pending}, {@code completed}, {@code failed} (PAS définitif), {@code expired} (définitif), {@code amount_mismatch}
 * @param method {@code mobile_money} ou {@code card}
 * @param checkoutUrl {@code null} après expiration : n'envoyez jamais un ancien lien
 */
public record Payment(
        String id, String reference, BigDecimal amount, String currency, BigDecimal commission, BigDecimal netAmount,
        String status, String method, BigDecimal refundedAmount, BigDecimal originalAmount, String originalCurrency,
        String checkoutUrl, Map<String, Object> metadata, boolean testMode,
        String createdAt, String completedAt, String expiresAt, String expiredAt) {

    public static Payment fromJson(JsonNode r) {
        String method = Json.nstr(r, "method");
        return new Payment(
                Json.str(r, "id"), Json.str(r, "reference"), Json.num(r, "amount"), Json.nstr(r, "currency"),
                Json.num(r, "commission"), Json.num(r, "net_amount"), Json.str(r, "status"),
                method == null ? "mobile_money" : method, Json.num(r, "refunded_amount"),
                Json.nnum(r, "original_amount"), Json.nstr(r, "original_currency"), Json.nstr(r, "checkout_url"),
                Json.map(r, "metadata"), Json.bool(r, "test_mode"), Json.nstr(r, "created_at"),
                Json.nstr(r, "completed_at"), Json.nstr(r, "expires_at"), Json.nstr(r, "expired_at"));
    }

    /**
     * Ce que VOTRE backend renvoie à votre app ({@code {"payment": toAppJson()}}) : ni commission,
     * ni montant net, ni métadonnées. C'est le contrat que lit {@code technas_pay_flutter}.
     */
    public Map<String, Object> toAppJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("reference", reference);
        m.put("status", status);
        m.put("checkout_url", checkoutUrl);
        m.put("amount", amount);
        m.put("currency", currency);
        m.put("expires_at", expiresAt);
        m.put("method", method);
        return m;
    }
}
