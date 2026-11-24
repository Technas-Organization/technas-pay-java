package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Ce que vous recevez, réseau par réseau, au prix affiché.
 *
 * @param country le pays (ISO), ou {@code null}
 * @param network le réseau ({@code Network}), ex. {@code mixx}. Un réseau plus récent que ce SDK est gardé tel quel
 * @param networkName le nom que le client connaît (ex. « Mixx by Yas »)
 * @param customerTotal ce que le client paie : jamais plus que le prix affiché
 * @param processingFee les frais du réseau de paiement
 * @param commission la commission Technas
 * @param netAmount ce que vous recevez (au moins {@code receive})
 * @param operatorFeeEstimate les frais propres de l'opérateur du client, hors Technas : une estimation
 *     (« environ ») ; {@code null} : inconnus
 */
public record DisplayPriceNetwork(String country, String network, String networkName, BigDecimal customerTotal,
                                  BigDecimal processingFee, BigDecimal commission, BigDecimal netAmount,
                                  BigDecimal operatorFeeEstimate) {

    public static DisplayPriceNetwork fromJson(JsonNode r) {
        String network = Json.str(r, "network");
        String name = Json.nstr(r, "network_name");
        return new DisplayPriceNetwork(Json.nstr(r, "country"), network, name == null ? network : name,
                Json.num(r, "customer_total"), Json.num(r, "processing_fee"), Json.num(r, "commission"),
                Json.num(r, "net_amount"), Json.nnum(r, "operator_fee_estimate"));
    }
}
