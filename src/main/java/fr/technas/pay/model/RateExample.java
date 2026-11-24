package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Un exemple à 1 000 dans un mode de frais.
 *
 * @param customerTotal ce que le client paie
 * @param processingFee les frais du réseau de paiement
 * @param commission la commission Technas
 * @param netAmount ce que vous recevez
 */
public record RateExample(BigDecimal customerTotal, BigDecimal processingFee, BigDecimal commission,
                          BigDecimal netAmount) {

    /** {@code null} si l'exemple manque (pas payable dans ce mode). */
    public static RateExample fromJson(JsonNode r) {
        if (r == null || !r.isObject()) {
            return null;
        }
        return new RateExample(Json.num(r, "customer_total"), Json.num(r, "processing_fee"), Json.num(r, "commission"),
                Json.num(r, "net_amount"));
    }
}
