package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Contenu des avis {@code settlement.completed} et {@code settlement.failed} : l'issue d'un
 * reversement de votre solde vers votre numéro mobile money. Un champ absent de l'avis est
 * {@code null}.
 *
 * @param amount ce qui a quitté le solde ; recrédité sur le solde si le reversement échoue
 * @param fee frais du versement mobile money, à votre charge
 * @param netAmount ce qui arrive sur le numéro
 * @param status {@code completed} ou {@code failed}
 */
public record Settlement(String id, BigDecimal amount, BigDecimal fee, BigDecimal netAmount, String currency,
                         String status, String createdAt) {

    public static Settlement fromJson(JsonNode r) {
        return new Settlement(Json.nstr(r, "id"), Json.nnum(r, "amount"), Json.nnum(r, "fee"), Json.nnum(r, "net_amount"),
                Json.nstr(r, "currency"), Json.nstr(r, "status"), Json.nstr(r, "created_at"));
    }
}
