package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;

/**
 * Un moyen de paiement ouvert pour votre compte, et s'il est activé.
 *
 * @param method {@code mobile_money} ou {@code card} ({@code PaymentMethod})
 * @param enabled activé sur votre compte
 */
public record MethodSetting(String method, boolean enabled) {

    public static MethodSetting fromJson(JsonNode r) {
        return new MethodSetting(Json.str(r, "method"), Json.bool(r, "enabled"));
    }
}
