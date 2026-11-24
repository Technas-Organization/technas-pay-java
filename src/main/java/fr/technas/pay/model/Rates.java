package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * La grille des frais ({@code pay.getRates(...)}), par pays ouvert et par réseau : pourcentage,
 * minimum, commission Technas, estimation des frais de l'opérateur, exemple à 1 000 dans chaque mode.
 *
 * @param feesPaidBy le mode de votre compte : {@code partner} ou {@code customer}
 * @param countries les pays ouverts
 */
public record Rates(String feesPaidBy, List<RateCountry> countries) {

    public static Rates fromJson(JsonNode r) {
        List<RateCountry> countries = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("countries");
        if (list != null && list.isArray()) {
            list.forEach(c -> {
                if (c.isObject()) {
                    countries.add(RateCountry.fromJson(c));
                }
            });
        }
        String feesPaidBy = Json.nstr(r, "fees_paid_by");
        return new Rates(feesPaidBy == null ? "partner" : feesPaidBy, List.copyOf(countries));
    }
}
