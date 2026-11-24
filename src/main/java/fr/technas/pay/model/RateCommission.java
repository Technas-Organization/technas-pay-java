package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * La commission Technas d'un pays.
 *
 * @param percent le pourcentage
 * @param fixed la part fixe
 */
public record RateCommission(BigDecimal percent, BigDecimal fixed) {

    public static RateCommission fromJson(JsonNode r) {
        return new RateCommission(Json.num(r, "percent"), Json.num(r, "fixed"));
    }
}
