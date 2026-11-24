package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Un pays de la grille des frais.
 *
 * @param country le pays (ISO)
 * @param currency la devise
 * @param commission la commission Technas
 * @param networks les réseaux ouverts
 */
public record RateCountry(String country, String currency, RateCommission commission, List<RateNetwork> networks) {

    public static RateCountry fromJson(JsonNode r) {
        List<RateNetwork> networks = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("networks");
        if (list != null && list.isArray()) {
            list.forEach(n -> {
                if (n.isObject()) {
                    networks.add(RateNetwork.fromJson(n));
                }
            });
        }
        return new RateCountry(Json.str(r, "country"), Json.str(r, "currency"),
                RateCommission.fromJson(Json.obj(r, "commission")), List.copyOf(networks));
    }
}
