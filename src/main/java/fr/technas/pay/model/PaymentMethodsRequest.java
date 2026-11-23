package fr.technas.pay.model;

import java.util.List;

/**
 * Ce qu'on change avec {@code pay.setPaymentMethods(...)} : un champ non donné ne change pas.
 *
 * <pre>{@code
 * pay.setPaymentMethods(PaymentMethodsRequest.builder()
 *     .methods(List.of(PaymentMethod.MOBILE_MONEY))           // éteindre la carte
 *     .networkOrder(List.of(Network.MIXX, Network.MOOV))      // Mixx en premier
 *     .build());
 * }</pre>
 *
 * @param methods les moyens activés ({@code PaymentMethod}) ; {@code null} : inchangés (sauf {@code allMethods})
 * @param allMethods tous les moyens
 * @param networks les réseaux activés ({@code Network}) ; {@code null} : inchangés (sauf {@code allNetworks})
 * @param allNetworks tous les réseaux
 * @param networkOrder l'ordre d'affichage sur la page de paiement ; {@code null} : inchangé
 */
public record PaymentMethodsRequest(List<String> methods, boolean allMethods, List<String> networks, boolean allNetworks,
                                    List<String> networkOrder) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> methods;
        private boolean allMethods;
        private List<String> networks;
        private boolean allNetworks;
        private List<String> networkOrder;

        private Builder() {
        }

        /** Les moyens activés ({@code PaymentMethod.MOBILE_MONEY}, {@code PaymentMethod.CARD}). */
        public Builder methods(List<String> v) {
            methods = v == null ? null : List.copyOf(v);
            allMethods = false;
            return this;
        }

        /** Tous les moyens. */
        public Builder allMethods() {
            methods = null;
            allMethods = true;
            return this;
        }

        /** Les réseaux activés ({@code Network.MIXX}…). */
        public Builder networks(List<String> v) {
            networks = v == null ? null : List.copyOf(v);
            allNetworks = false;
            return this;
        }

        /** Tous les réseaux. */
        public Builder allNetworks() {
            networks = null;
            allNetworks = true;
            return this;
        }

        /** L'ordre d'affichage des réseaux sur la page de paiement. */
        public Builder networkOrder(List<String> v) {
            networkOrder = v == null ? null : List.copyOf(v);
            return this;
        }

        public PaymentMethodsRequest build() {
            return new PaymentMethodsRequest(methods, allMethods, networks, allNetworks, networkOrder);
        }
    }
}
