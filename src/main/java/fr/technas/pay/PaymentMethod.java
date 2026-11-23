package fr.technas.pay;

import java.util.List;

/**
 * Les moyens de paiement : {@code mobile_money} (défaut) ou {@code card}. Des chaînes, comme
 * {@code Payment.method()}.
 */
public final class PaymentMethod {
    private PaymentMethod() {
    }

    public static final String MOBILE_MONEY = "mobile_money";
    public static final String CARD = "card";

    /** Tous les moyens, à énumérer. */
    public static final List<String> ALL = List.of(MOBILE_MONEY, CARD);
}
