package fr.technas.pay.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce qu'on demande à {@code pay.displayPrice(...)} : ce que vous voulez recevoir, et où.
 *
 * <pre>{@code
 * DisplayPrice d = pay.displayPrice(DisplayPriceRequest.builder()
 *     .receive(1000).countries(List.of("TG", "BJ")).roundTo(50).build());
 * d.byCountry().get("TG").price(); // le prix à afficher au Togo
 * }</pre>
 *
 * @param receive ce que vous voulez recevoir au moins, en unité principale (1000 = 1 000 F CFA)
 * @param country un pays (ISO, ex. {@code TG}) ; {@code null} : aucun
 * @param countries plusieurs pays : un prix par pays ({@code byCountry}) et un prix valable partout
 *     ({@code everywhere}) ; {@code null} ou vide : aucun
 * @param currency sans pays : {@code XOF} (défaut) ou {@code XAF}
 * @param roundTo le prix est un multiple de ce pas ; {@code null} : 50
 */
public record DisplayPriceRequest(BigDecimal receive, String country, List<String> countries, String currency,
                                  Integer roundTo) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private BigDecimal receive;
        private String country;
        private List<String> countries;
        private String currency;
        private Integer roundTo;

        private Builder() {
        }

        public Builder receive(long v) {
            receive = BigDecimal.valueOf(v);
            return this;
        }

        public Builder receive(BigDecimal v) {
            receive = v;
            return this;
        }

        public Builder country(String v) {
            country = v;
            return this;
        }

        public Builder countries(List<String> v) {
            countries = v == null ? null : List.copyOf(v);
            return this;
        }

        /** Sans pays : {@code XOF} (défaut) ou {@code XAF}. */
        public Builder currency(String v) {
            currency = v;
            return this;
        }

        /** Le prix est un multiple de ce pas (défaut 50). */
        public Builder roundTo(int v) {
            roundTo = v;
            return this;
        }

        public DisplayPriceRequest build() {
            return new DisplayPriceRequest(receive, country, countries, currency, roundTo);
        }
    }
}
