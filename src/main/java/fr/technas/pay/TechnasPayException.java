package fr.technas.pay;

import java.util.Collections;
import java.util.Map;

/**
 * Une erreur rendue par Technas, ou détectée avant tout appel ({@code status} vaut alors 0).
 *
 * <p>Testez {@link #code()} (stable), jamais le message : {@code payment_expired},
 * {@code reference_conflict}, {@code risk_not_accepted}, {@code card_account_not_ready}…
 * {@link #retryable()} : refaire LE MÊME appel, mêmes valeurs, a du sens. Le message ne cite
 * jamais le secret.
 */
public final class TechnasPayException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;
    private final transient Map<String, Object> details;

    public TechnasPayException(String code, String message) {
        this(code, message, 0, Map.of());
    }

    public TechnasPayException(String code, String message, int status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
    }

    /** Le code stable : c'est lui qu'il faut tester. */
    public String code() {
        return code;
    }

    /** Statut HTTP de Technas, 0 si le service n'a pas été joint. */
    public int status() {
        return status;
    }

    /**
     * Précisions selon le code : {@code missingAmount} et {@code currency} ({@code insufficient_balance}),
     * {@code refundableAmount}, {@code reason}, {@code risk}, {@code accountStatus}.
     */
    public Map<String, Object> details() {
        return Map.copyOf(details);
    }

    /** Vrai si refaire le même appel, avec la même référence, a du sens. */
    public boolean retryable() {
        return "network_error".equals(code) || "payment_in_progress".equals(code) || status == 429 || status >= 500;
    }
}
