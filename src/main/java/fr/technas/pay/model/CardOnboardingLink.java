package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;

/**
 * @param url à ouvrir par le RESPONSABLE du compte ; expire en minutes : à générer au clic
 * @param kind {@code onboarding} ou {@code dashboard}
 */
public record CardOnboardingLink(String url, String kind, String expiresAt, CardAccount account) {
    public static CardOnboardingLink fromJson(JsonNode r) {
        return new CardOnboardingLink(Json.str(r, "url"), Json.str(r, "kind"), Json.nstr(r, "expires_at"),
                CardAccount.fromJson(Json.obj(r, "account")));
    }
}
