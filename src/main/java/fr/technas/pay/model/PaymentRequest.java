package fr.technas.pay.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Une demande de paiement.
 *
 * <pre>{@code
 * PaymentRequest.builder()
 *     .amount(5000)                                        // entier : 5 000 FCFA, jamais × 100
 *     .reference(Status.retryReference(orderId, attempt))  // une référence = un essai
 *     .customer(Customer.of(email, name))
 *     .returnUrl("https://votre-site.com/merci")
 *     .build();
 * }</pre>
 *
 * Par carte : {@code .method("card").currency("EUR")}, décimales permises.
 *
 * <p>{@code .fees("customer")} : le client paie tous les frais (réseau de paiement et commission
 * Technas), vous recevez exactement {@code amount}. {@code .fees("partner")} : le client paie
 * {@code amount}, frais compris. Sans {@code fees} : le réglage de votre compte.
 *
 * <p>{@code .networks(List.of(Network.MIXX))} : les réseaux mobile money permis pour CE paiement
 * (parmi ceux de votre compte). Absent ou vide : tous ceux que vous avez activés.
 */
public record PaymentRequest(BigDecimal amount, String reference, Customer customer, String returnUrl, String method,
                             String currency, String description, Map<String, Object> metadata,
                             List<String> allowedCountries, List<String> excludedCountries, boolean askCountry,
                             String fees, List<String> networks) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private BigDecimal amount;
        private String reference;
        private Customer customer;
        private String returnUrl;
        private String method = "mobile_money";
        private String currency;
        private String description;
        private Map<String, Object> metadata = Map.of();
        private List<String> allowedCountries;
        private List<String> excludedCountries;
        private boolean askCountry;
        private String fees;
        private List<String> networks;

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

        public Builder reference(String v) {
            reference = v;
            return this;
        }

        public Builder customer(Customer v) {
            customer = v;
            return this;
        }

        public Builder returnUrl(String v) {
            returnUrl = v;
            return this;
        }

        /** {@code mobile_money} (défaut) ou {@code card} (exige {@code currency} et un compte carte actif). */
        public Builder method(String v) {
            method = v;
            return this;
        }

        /** Ne la passez pas en zone franc CFA : elle suit le pays du client. Obligatoire par carte. */
        public Builder currency(String v) {
            currency = v;
            return this;
        }

        public Builder description(String v) {
            description = v;
            return this;
        }

        public Builder metadata(Map<String, Object> v) {
            metadata = v == null ? Map.of() : Map.copyOf(v);
            return this;
        }

        public Builder allowedCountries(List<String> v) {
            allowedCountries = v == null ? null : List.copyOf(v);
            return this;
        }

        public Builder excludedCountries(List<String> v) {
            excludedCountries = v == null ? null : List.copyOf(v);
            return this;
        }

        public Builder askCountry(boolean v) {
            askCountry = v;
            return this;
        }

        /**
         * Qui paie les frais (mobile money en franc CFA) : {@code "customer"} (le client paie les frais
         * du réseau de paiement ET la commission Technas ; vous recevez EXACTEMENT {@code amount}) ou
         * {@code "partner"} (le client paie {@code amount}, frais compris). {@code null} : le réglage de
         * votre compte. Par carte, seulement {@code "partner"} ({@code fees_customer_unavailable}).
         * Les montants avant de créer le paiement : {@code pay.quote(...)}.
         */
        public Builder fees(String v) {
            fees = v;
            return this;
        }

        /**
         * Les réseaux mobile money permis pour CE paiement ({@code Network.MIXX}…, parmi ceux de votre
         * compte). {@code null} ou vide : tous ceux que vous avez activés. Une valeur hors de
         * {@code Network.ALL} est refusée avant tout appel ({@code invalid_request}).
         */
        public Builder networks(List<String> v) {
            networks = v == null ? null : List.copyOf(v);
            return this;
        }

        public PaymentRequest build() {
            return new PaymentRequest(amount, reference, customer, returnUrl, method, currency, description, metadata,
                    allowedCountries, excludedCountries, askCountry, fees, networks);
        }
    }
}
