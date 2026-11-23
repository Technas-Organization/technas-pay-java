package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Le devis d'un paiement ({@code pay.quote(...)}) : ce que le client paiera et ce que vous recevrez.
 * Rien n'est créé. Un paiement créé ensuite avec le même montant, le même pays et le même
 * {@code fees} porte les mêmes montants.
 *
 * @param method {@code mobile_money} ou {@code card}
 * @param country pays du client, ou {@code null} (tous les pays de la devise)
 * @param feesPaidBy qui paie les frais : {@code partner} ou {@code customer}
 * @param amount votre prix
 * @param customerAmount ce que Technas demande au réseau de paiement (avant ses frais)
 * @param customerTotal ce que le client paie, frais du réseau compris (le plus haut des réseaux
 *     possibles) : le montant à lui afficher. Par carte : {@code amount} (les frais de la carte sont
 *     pris sur votre compte carte)
 * @param processingFee les frais du réseau de paiement (le plus haut des réseaux) ; {@code null} par carte
 * @param commission la commission Technas
 * @param netAmount ce que vous recevez ; {@code null} par carte (dépend de la carte du client)
 * @param networks réseau par réseau (mobile money) ; vide par carte
 */
public record Quote(String method, String currency, String country, String feesPaidBy, BigDecimal amount,
                    BigDecimal customerAmount, BigDecimal customerTotal, BigDecimal processingFee,
                    BigDecimal commission, BigDecimal netAmount, List<QuoteNetwork> networks) {

    public static Quote fromJson(JsonNode r) {
        String method = Json.nstr(r, "method");
        String feesPaidBy = Json.nstr(r, "fees_paid_by");
        BigDecimal amount = Json.num(r, "amount");
        BigDecimal customerAmount = Json.nnum(r, "customer_amount");
        BigDecimal customerTotal = Json.nnum(r, "customer_total");
        List<QuoteNetwork> networks = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("networks");
        if (list != null && list.isArray()) {
            list.forEach(n -> {
                if (n.isObject()) {
                    networks.add(QuoteNetwork.fromJson(n));
                }
            });
        }
        return new Quote(method == null ? "mobile_money" : method, Json.nstr(r, "currency"), Json.nstr(r, "country"),
                feesPaidBy == null ? "partner" : feesPaidBy, amount, customerAmount == null ? amount : customerAmount,
                customerTotal == null ? amount : customerTotal, Json.nnum(r, "processing_fee"), Json.num(r, "commission"),
                Json.nnum(r, "net_amount"), List.copyOf(networks));
    }
}
