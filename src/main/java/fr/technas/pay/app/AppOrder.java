package fr.technas.pay.app;

import fr.technas.pay.model.Customer;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Ce que {@code resolveOrder} rend : relu EN BASE, jamais pris de la requête de l'app.
 *
 * @param amount unité principale (5000 = 5 000 FCFA ; 12.5 = 12,50 € par carte) ; entier pour le mobile money
 * @param currency facultative : sans elle, la devise suit le pays du client (zone franc CFA) ; obligatoire pour la carte
 * @param returnUrl page de retour (lien qui rouvre l'app) ; sinon {@code returnUrl} des routes
 */
public record AppOrder(BigDecimal amount, Customer customer, String currency, String description, String returnUrl,
                       Map<String, Object> metadata) {
    public static AppOrder of(long amount, Customer customer) {
        return new AppOrder(BigDecimal.valueOf(amount), customer, null, null, null, Map.of());
    }

    public static AppOrder of(BigDecimal amount, String currency, Customer customer) {
        return new AppOrder(amount, customer, currency, null, null, Map.of());
    }
}
