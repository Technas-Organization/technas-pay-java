package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Un prix affiché, avec le réseau qui vous laisse le moins.
 *
 * @param price le prix à afficher
 * @param currency la devise
 * @param worstNetwork le réseau qui vous laisse le moins (au moins {@code receive}) ; {@code null} s'il manque
 * @param netByNetwork ce que vous recevez, réseau par réseau
 */
public record DisplayPriceForCountry(BigDecimal price, String currency, DisplayPriceNetwork worstNetwork,
                                     List<DisplayPriceNetwork> netByNetwork) {

    public static DisplayPriceForCountry fromJson(JsonNode r) {
        return new DisplayPriceForCountry(Json.num(r, "price"), Json.nstr(r, "currency"), worst(r), byNetwork(r));
    }

    static DisplayPriceNetwork worst(JsonNode r) {
        JsonNode w = r == null ? null : r.get("worst_network");
        return w != null && w.isObject() ? DisplayPriceNetwork.fromJson(w) : null;
    }

    static List<DisplayPriceNetwork> byNetwork(JsonNode r) {
        List<DisplayPriceNetwork> out = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("net_by_network");
        if (list != null && list.isArray()) {
            list.forEach(n -> {
                if (n.isObject()) {
                    out.add(DisplayPriceNetwork.fromJson(n));
                }
            });
        }
        return List.copyOf(out);
    }
}
