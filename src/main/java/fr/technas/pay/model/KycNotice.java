package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Contenu de l'avis {@code account.kyc_required} : envoyé à 80 % du seuil, 7 jours avant
 * l'échéance, puis au premier refus. Prévenez le responsable du compte.
 *
 * @param reason {@code amount_80}, {@code deadline_7d} ou {@code blocked}
 */
public record KycNotice(String reason, BigDecimal collectedXof, BigDecimal thresholdXof, String deadline, boolean blocked,
                        String consoleUrl) {

    public static KycNotice fromJson(JsonNode r) {
        return new KycNotice(Json.str(r, "reason"), Json.num(r, "collected_xof"), Json.num(r, "threshold_xof"),
                Json.nstr(r, "deadline"), Json.bool(r, "blocked"), Json.nstr(r, "console_url"));
    }
}
