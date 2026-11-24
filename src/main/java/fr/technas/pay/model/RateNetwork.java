package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Un réseau de la grille des frais.
 *
 * @param network le réseau ({@code Network}), ex. {@code mixx}. Un réseau plus récent que ce SDK est gardé tel quel
 * @param networkName le nom que le client connaît (ex. « Mixx by Yas »)
 * @param percent le pourcentage du réseau de paiement (palier de 1 000)
 * @param fixed la part fixe
 * @param feeTiers les paliers
 * @param minimumAmount le plus petit montant que le réseau accepte ; {@code null} : inconnu
 * @param operatorFeeEstimate les frais propres de l'opérateur du client sur 1 000, hors Technas : une
 *     estimation (« environ ») ; {@code null} : inconnus
 * @param example1000 un exemple à 1 000, dans chaque mode
 */
public record RateNetwork(String network, String networkName, BigDecimal percent, BigDecimal fixed,
                          List<FeeTier> feeTiers, BigDecimal minimumAmount, BigDecimal operatorFeeEstimate,
                          RateExamples example1000) {

    public static RateNetwork fromJson(JsonNode r) {
        String network = Json.str(r, "network");
        String name = Json.nstr(r, "network_name");
        List<FeeTier> tiers = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("fee_tiers");
        if (list != null && list.isArray()) {
            list.forEach(t -> {
                if (t.isObject()) {
                    tiers.add(FeeTier.fromJson(t));
                }
            });
        }
        return new RateNetwork(network, name == null ? network : name, Json.num(r, "percent"), Json.num(r, "fixed"),
                List.copyOf(tiers), Json.nnum(r, "minimum_amount"), Json.nnum(r, "operator_fee_estimate"),
                RateExamples.fromJson(r == null ? null : r.get("example_1000")));
    }
}
