package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le prix rond à afficher pour recevoir au moins {@code receive}, quel que soit le réseau du client
 * ({@code pay.displayPrice(...)}). Créez ensuite le paiement avec {@code amount(price)} et
 * {@code fees("partner")} : le client paie exactement ce prix, et le surplus vous reste. Jamais
 * {@code "customer"} : le client paierait le prix PLUS les frais.
 *
 * @param price le prix à afficher (le plus haut des pays demandés)
 * @param currency la devise
 * @param worstNetwork le réseau qui vous laisse le moins ; {@code null} s'il manque
 * @param netByNetwork ce que vous recevez, réseau par réseau
 * @param feesPaidBy toujours {@code partner} : un prix affiché fixe se paie en « vous payez les frais »
 * @param receive ce que vous voulez recevoir au moins
 * @param roundTo le pas d'arrondi
 * @param countries les pays demandés
 * @param byCountry un prix par pays (avec {@code country} ou {@code countries})
 * @param everywhere un seul prix valable dans tous ces pays ; {@code null} si XOF et XAF sont mélangés
 */
public record DisplayPrice(BigDecimal price, String currency, DisplayPriceNetwork worstNetwork,
                           List<DisplayPriceNetwork> netByNetwork, String feesPaidBy, BigDecimal receive,
                           BigDecimal roundTo, List<String> countries, Map<String, DisplayPriceForCountry> byCountry,
                           DisplayPriceForCountry everywhere) {

    public static DisplayPrice fromJson(JsonNode r) {
        Map<String, DisplayPriceForCountry> byCountry = new LinkedHashMap<>();
        JsonNode bc = r == null ? null : r.get("by_country");
        if (bc != null && bc.isObject()) {
            bc.properties().forEach(e -> {
                if (e.getValue().isObject()) {
                    byCountry.put(e.getKey(), DisplayPriceForCountry.fromJson(e.getValue()));
                }
            });
        }
        JsonNode ev = r == null ? null : r.get("everywhere");
        return new DisplayPrice(Json.num(r, "price"), Json.nstr(r, "currency"), DisplayPriceForCountry.worst(r),
                DisplayPriceForCountry.byNetwork(r), "partner", Json.num(r, "receive"), Json.num(r, "round_to"),
                Json.strings(r, "countries"), Collections.unmodifiableMap(byCountry),
                ev != null && ev.isObject() ? DisplayPriceForCountry.fromJson(ev) : null);
    }
}
