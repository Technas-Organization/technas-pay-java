package fr.technas.pay.model;

import java.math.BigDecimal;

/**
 * Ce qu'on demande à {@code pay.quote(...)} : un montant, et où le client paie.
 *
 * <pre>{@code
 * Quote q = pay.quote(QuoteRequest.builder().amount(200).country("TG").fees("customer").build());
 * q.customerTotal(); // 214 : le client paie 214, vous recevez 200
 * }</pre>
 *
 * @param amount votre prix, en unité principale (5000 = 5 000 F CFA)
 * @param method {@code mobile_money} (défaut, {@code null}) ou {@code card}
 * @param country pays du client (ISO, ex. {@code TG}) ; {@code null} : tous les pays de la devise
 * @param network un réseau précis ({@code network} d'un devis précédent) ; {@code null} : tous ceux du pays
 * @param currency sans {@code country} : {@code XOF} ou {@code XAF}. Obligatoire par carte
 * @param fees {@code partner} ou {@code customer} ; {@code null} : le réglage de votre compte
 */
public record QuoteRequest(BigDecimal amount, String method, String country, String network, String currency,
                           String fees) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private BigDecimal amount;
        private String method;
        private String country;
        private String network;
        private String currency;
        private String fees;

        private Builder() {
        }

        public Builder amount(long v) {
            amount = BigDecimal.valueOf(v);
            return this;
        }

        public Builder amount(BigDecimal v) {
            amount = v;
            return this;
        }

        /** {@code mobile_money} (défaut) ou {@code card}. */
        public Builder method(String v) {
            method = v;
            return this;
        }

        public Builder country(String v) {
            country = v;
            return this;
        }

        public Builder network(String v) {
            network = v;
            return this;
        }

        public Builder currency(String v) {
            currency = v;
            return this;
        }

        /** {@code "customer"} (vous recevez exactement {@code amount}) ou {@code "partner"} (le client paie {@code amount}). */
        public Builder fees(String v) {
            fees = v;
            return this;
        }

        public QuoteRequest build() {
            return new QuoteRequest(amount, method, country, network, currency, fees);
        }
    }
}
