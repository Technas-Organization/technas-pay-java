package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Contenu de l'avis {@code account.kyc_required} : envoyé à 80 % du seuil, 7 jours avant
 * l'échéance, puis au premier refus. Prévenez le responsable du compte.
 *
 * <p>Les champs {@code stage}, {@code blockReason}, {@code daysElapsed}, {@code graceDays} et
 * {@code firstCollectedAt} reprennent {@code GET /v1/account/kyc} ; ils sont {@code null} dans un
 * avis plus ancien.
 *
 * @param reason le DÉCLENCHEUR de l'avis : {@code amount_80}, {@code deadline_7d} ou {@code blocked}
 * @param stage {@code ok}, {@code soon}, {@code blocked}, {@code verified}, {@code not_applicable}
 * @param blockReason {@code amount}, {@code duration}, ou {@code null}
 */
public record KycNotice(String reason, BigDecimal collectedXof, BigDecimal thresholdXof, String deadline, boolean blocked,
                        String consoleUrl, String stage, String blockReason, Integer daysElapsed, Integer graceDays,
                        String firstCollectedAt) {

    public static KycNotice fromJson(JsonNode r) {
        return new KycNotice(Json.str(r, "reason"), Json.num(r, "collected_xof"), Json.num(r, "threshold_xof"),
                Json.nstr(r, "deadline"), Json.bool(r, "blocked"), Json.nstr(r, "console_url"), Json.nstr(r, "stage"),
                Json.nstr(r, "block_reason"), AccountKyc.integer(r, "days_elapsed"), AccountKyc.integer(r, "grace_days"),
                Json.nstr(r, "first_collected_at"));
    }
}
