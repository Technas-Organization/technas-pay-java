package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Un réseau du devis : ce que le client paie s'il paie avec lui.
 *
 * @param network le réseau ({@code Network}), ex. {@code mixx}, à redonner dans {@code QuoteRequest.network}.
 *     Un réseau plus récent que ce SDK est gardé tel quel
 * @param networkName le nom que le client connaît (ex. « Mixx by Yas »)
 * @param customerTotal ce que le client paie, frais du réseau de paiement compris : le montant à afficher
 * @param processingFee les frais du réseau de paiement dans {@code customerTotal}
 * @param commission la commission Technas
 * @param netAmount ce que vous recevez
 * @param operatorFeeEstimate les frais propres de l'opérateur du client, prélevés sur son téléphone EN
 *     PLUS, hors Technas : une estimation (« environ »), jamais promise ; {@code null} : inconnus
 * @param otpRequired le client doit obtenir un code sur son téléphone avant de payer ({@code otpInstructions})
 */
public record QuoteNetwork(String network, String networkName, BigDecimal customerTotal, BigDecimal processingFee,
                           BigDecimal commission, BigDecimal netAmount, BigDecimal operatorFeeEstimate,
                           boolean otpRequired, String otpInstructions) {

    public static QuoteNetwork fromJson(JsonNode r) {
        String network = Json.str(r, "network");
        String name = Json.nstr(r, "network_name");
        return new QuoteNetwork(network, name == null ? network : name, Json.num(r, "customer_total"),
                Json.num(r, "processing_fee"), Json.num(r, "commission"), Json.num(r, "net_amount"),
                Json.nnum(r, "operator_fee_estimate"), Json.bool(r, "otp_required"), Json.nstr(r, "otp_instructions"));
    }
}
