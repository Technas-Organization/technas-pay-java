package fr.technas.pay;

import fr.technas.pay.internal.Ascii;
import java.util.Map;

/** Règles sans secret, les mêmes dans tous les SDK Technas Pay. */
public final class Status {
    private Status() {
    }

    /** Vrai si le statut ne changera plus : {@code completed}, {@code expired}, {@code amount_mismatch}. Jamais {@code failed}. */
    public static boolean isFinal(String status) {
        return "completed".equals(status) || "expired".equals(status) || "amount_mismatch".equals(status);
    }

    /** Vrai seulement pour {@code expired} : relancez avec une NOUVELLE référence. Jamais sur {@code failed}. */
    public static boolean canRetry(String status) {
        return "expired".equals(status);
    }

    /** Une référence = un essai : {@code orderId + "-" + attempt}. Gardez {@code attempt} en base (1, 2, 3…). */
    public static String retryReference(String orderId, int attempt) {
        if (attempt < 1) {
            throw new TechnasPayException("invalid_request", "attempt est un entier >= 1");
        }
        return orderId + "-" + attempt;
    }

    private static final Map<String, String> CURRENCY_BY_COUNTRY = Map.ofEntries(
            Map.entry("BJ", "XOF"), Map.entry("BF", "XOF"), Map.entry("CI", "XOF"), Map.entry("GW", "XOF"),
            Map.entry("ML", "XOF"), Map.entry("NE", "XOF"), Map.entry("SN", "XOF"), Map.entry("TG", "XOF"),
            Map.entry("CM", "XAF"), Map.entry("CF", "XAF"), Map.entry("CG", "XAF"), Map.entry("GA", "XAF"),
            Map.entry("GQ", "XAF"), Map.entry("TD", "XAF"));

    /** La devise d'un pays de la zone franc CFA, sinon {@code null}. */
    public static String currencyForCountry(String country) {
        return country == null ? null : CURRENCY_BY_COUNTRY.get(Ascii.upper(country.trim()));
    }
}
