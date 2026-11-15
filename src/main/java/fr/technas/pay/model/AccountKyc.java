package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.math.BigDecimal;

/**
 * Le seuil d'identité du compte ({@code GET /v1/account/kyc}). En réel, un compte sans pièce
 * vérifiée voit ses NOUVEAUX paiements mobile money refusés ({@code kyc_required}) au-delà de
 * {@code thresholdXof} F CFA encaissés ou de {@code graceDays} jours après son premier paiement réel.
 * Rien de ce qui existe n'est bloqué ; la carte n'est pas concernée. Montrez-le au MARCHAND,
 * jamais au client final. Les chiffres viennent de l'API : ne les écrivez pas en dur.
 *
 * @param stage {@code ok}, {@code soon} (préavis), {@code blocked}, {@code verified}, {@code not_applicable}
 * @param level {@code none}, {@code identity}, {@code business}
 * @param reason {@code amount}, {@code duration}, ou {@code null} tant que rien n'est bloqué
 * @param consoleUrl où le responsable dépose sa pièce
 */
public record AccountKyc(
        boolean applies, String stage, String level, Integer daysElapsed, BigDecimal warnFromXof, String warnFromDate,
        BigDecimal collectedXof, BigDecimal thresholdXof, Integer graceDays, String firstCollectedAt, String deadline,
        String waiverUntil, boolean blocked, String reason, String consoleUrl) {

    public static AccountKyc fromJson(JsonNode r) {
        return new AccountKyc(
                Json.bool(r, "applies"), Json.str(r, "stage"), Json.str(r, "level"), integer(r, "days_elapsed"),
                Json.num(r, "warn_from_xof"), Json.nstr(r, "warn_from_date"), Json.num(r, "collected_xof"),
                Json.num(r, "threshold_xof"), integer(r, "grace_days"), Json.nstr(r, "first_collected_at"),
                Json.nstr(r, "deadline"), Json.nstr(r, "waiver_until"), Json.bool(r, "blocked"), Json.nstr(r, "reason"),
                Json.nstr(r, "console_url"));
    }

    static Integer integer(JsonNode r, String k) {
        BigDecimal v = Json.nnum(r, k);
        return v == null ? null : v.intValue();
    }
}
