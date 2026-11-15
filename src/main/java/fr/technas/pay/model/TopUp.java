package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/** @param checkoutUrl ouvrez cette page pour payer la recharge */
public record TopUp(String id, BigDecimal amount, String currency, String status, String checkoutUrl, boolean testMode,
                    String createdAt, String completedAt) {
    public static TopUp fromJson(JsonNode r) {
        return new TopUp(Json.str(r, "id"), Json.num(r, "amount"), Json.str(r, "currency"), Json.str(r, "status"),
                Json.str(r, "checkout_url"), Json.bool(r, "test_mode"), Json.nstr(r, "created_at"), Json.nstr(r, "completed_at"));
    }
}
