package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Un palier de la grille du réseau de paiement.
 *
 * @param minAmount à partir de ce montant
 * @param maxAmount jusqu'à ce montant ; {@code null} : sans limite
 * @param percent le pourcentage
 * @param fixed la part fixe
 */
public record FeeTier(BigDecimal minAmount, BigDecimal maxAmount, BigDecimal percent, BigDecimal fixed) {

    public static FeeTier fromJson(JsonNode r) {
        return new FeeTier(Json.num(r, "min_amount"), Json.nnum(r, "max_amount"), Json.num(r, "percent"),
                Json.num(r, "fixed"));
    }
}
