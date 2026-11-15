package fr.technas.pay.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.CardAccount;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.Refund;
import java.util.Map;

/**
 * Un événement vérifié. Selon {@code type}, un seul champ de contenu est rempli ; {@code data}
 * garde le brut. {@code id} est stable d'une livraison à l'autre : servez-vous-en pour ignorer un
 * doublon.
 */
public record Event(String id, String type, String createdAt, Map<String, Object> data,
                    Payment payment, Refund refund, CardAccount cardAccount) {

    static Event parse(JsonNode raw) {
        if (raw == null || !raw.isObject() || Json.nstr(raw, "id") == null || Json.nstr(raw, "type") == null
                || raw.get("data") == null || !raw.get("data").isObject()) {
            throw new WebhookVerificationException("Webhook illisible");
        }
        JsonNode d = raw.get("data");
        String type = Json.str(raw, "type");
        Payment payment = null;
        Refund refund = null;
        CardAccount card = null;
        switch (type) {
            case "payment.completed", "payment.failed", "payment.expired" -> {
                if (Json.nstr(d, "id") == null) {
                    throw new WebhookVerificationException("Webhook illisible");
                }
                payment = Payment.fromJson(d);
            }
            case "refund.completed", "refund.failed", "refund.recorded" -> refund = Refund.fromJson(d);
            case "card_account.updated" -> card = CardAccount.fromJson(d);
            default -> {
                // payout_destination.*, balance.*, fx_mode.updated, types inconnus : `data`.
            }
        }
        return new Event(Json.str(raw, "id"), type, Json.nstr(raw, "created_at"), Json.map(raw, "data"), payment, refund, card);
    }
}
