package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un paiement. Montants en unité principale, en décimal exact (5000 = 5 000 FCFA ; 12.5 = 12,50 €).
 *
 * @param status {@code pending}, {@code completed}, {@code failed} (PAS définitif), {@code expired} (définitif), {@code amount_mismatch}
 * @param method {@code mobile_money} ou {@code card}
 * @param checkoutUrl {@code null} après expiration : n'envoyez jamais un ancien lien
 * @param canceledAt quand vous l'avez annulé ({@code cancelPayment}) ; {@code null} sinon. Un paiement
 *     annulé est {@code expired} : {@code Status.canRetry} et vos états marchent tels quels
 * @param cancelReason {@code canceled_by_partner}, ou {@code null}
 * @param reason dans l'avis {@code payment.expired} : {@code canceled} (annulé par vous) ou
 *     {@code timeout} (échéance passée) ; {@code null} dans un avis plus ancien
 * @param customerAmount ce que Technas demande au réseau de paiement, avant ses frais ({@code amount}
 *     sauf frais au client ; {@code amount} sur un serveur ancien)
 * @param feesPaidBy qui paie les frais : {@code partner} (le client paie {@code amount}, frais compris)
 *     ou {@code customer} (le client paie aussi les frais : vous recevez exactement {@code amount})
 * @param customerTotal ce que le client paie, frais du réseau de paiement compris (le plus haut des
 *     réseaux tant qu'il n'a pas choisi le sien) ; {@code null} : inconnu (carte, hors franc CFA,
 *     serveur ancien)
 * @param processingFee les frais du réseau de paiement dans {@code customerTotal} ; {@code null} : inconnus
 * @param network le réseau avec lequel le client a payé ({@code Network}, ex. {@code mixx}), une fois
 *     payé ; {@code null} avant, par carte, ou sur un serveur ancien. Un réseau plus récent que ce SDK
 *     est gardé tel quel
 * @param networks les réseaux permis pour ce paiement, s'il les a restreints ; {@code null} sinon
 */
public record Payment(
        String id, String reference, BigDecimal amount, String currency, BigDecimal commission, BigDecimal netAmount,
        String status, String method, BigDecimal refundedAmount, BigDecimal originalAmount, String originalCurrency,
        String checkoutUrl, Map<String, Object> metadata, boolean testMode,
        String createdAt, String completedAt, String expiresAt, String expiredAt,
        String canceledAt, String cancelReason, String reason,
        BigDecimal customerAmount, String feesPaidBy, BigDecimal customerTotal, BigDecimal processingFee,
        String network, List<String> networks) {

    public static Payment fromJson(JsonNode r) {
        String method = Json.nstr(r, "method");
        BigDecimal amount = Json.num(r, "amount");
        BigDecimal customerAmount = Json.nnum(r, "customer_amount");
        String feesPaidBy = Json.nstr(r, "fees_paid_by");
        return new Payment(
                Json.str(r, "id"), Json.str(r, "reference"), amount, Json.nstr(r, "currency"),
                Json.num(r, "commission"), Json.num(r, "net_amount"), Json.str(r, "status"),
                method == null ? "mobile_money" : method, Json.num(r, "refunded_amount"),
                Json.nnum(r, "original_amount"), Json.nstr(r, "original_currency"), Json.nstr(r, "checkout_url"),
                Json.map(r, "metadata"), Json.bool(r, "test_mode"), Json.nstr(r, "created_at"),
                Json.nstr(r, "completed_at"), Json.nstr(r, "expires_at"), Json.nstr(r, "expired_at"),
                Json.nstr(r, "canceled_at"), Json.nstr(r, "cancel_reason"), Json.nstr(r, "reason"),
                customerAmount == null ? amount : customerAmount, feesPaidBy == null ? "partner" : feesPaidBy,
                Json.nnum(r, "customer_total"), Json.nnum(r, "processing_fee"), Json.nstr(r, "network"),
                r != null && r.has("networks") && r.get("networks").isArray() ? Json.strings(r, "networks") : null);
    }

    /**
     * Ce que VOTRE backend renvoie à votre app ({@code {"payment": toAppJson()}}) : ni commission,
     * ni montant net, ni métadonnées. C'est le contrat que lit {@code technas_pay_flutter}.
     */
    public Map<String, Object> toAppJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("reference", reference);
        m.put("status", status);
        m.put("checkout_url", checkoutUrl);
        m.put("amount", amount);
        m.put("currency", currency);
        m.put("expires_at", expiresAt);
        m.put("method", method);
        return m;
    }
}
