package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * @param kind {@code payout} (renvoyé par Technas), {@code card} (carte, depuis VOTRE compte carte ; l'ancienne valeur {@code stripe} est lue {@code card}), {@code external} (hors plateforme)
 * @param status {@code pending}, {@code completed}, {@code failed}
 */
public record Refund(
        String id, String paymentId, String paymentReference, BigDecimal amount, BigDecimal sentAmount, BigDecimal fee,
        String fees, BigDecimal balanceDebit, String currency, String status, String failure, String kind,
        String method, String note, String recordedBy, String createdAt) {

    public static Refund fromJson(JsonNode r) {
        String fees = Json.nstr(r, "fees");
        String kind = Json.nstr(r, "kind");
        return new Refund(
                Json.str(r, "id"), Json.str(r, "payment_id"), Json.str(r, "payment_reference"), Json.num(r, "amount"),
                Json.num(r, "sent_amount"), Json.num(r, "fee"), fees == null ? "partner" : fees,
                Json.num(r, "balance_debit"), Json.str(r, "currency"), Json.str(r, "status"), Json.nstr(r, "failure"),
                kind == null ? "payout" : "stripe".equals(kind) ? "card" : kind, Json.nstr(r, "method"), Json.nstr(r, "note"),
                Json.nstr(r, "recorded_by"), Json.nstr(r, "created_at"));
    }
}
