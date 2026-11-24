package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Un exemple à 1 000, dans chaque mode de frais.
 *
 * @param customer en {@code fees("customer")} ; {@code null} : pas payable
 * @param partner en {@code fees("partner")} ; {@code null} : pas payable
 */
public record RateExamples(RateExample customer, RateExample partner) {

    public static RateExamples fromJson(JsonNode r) {
        return new RateExamples(RateExample.fromJson(r == null ? null : r.get("customer")),
                RateExample.fromJson(r == null ? null : r.get("partner")));
    }
}
