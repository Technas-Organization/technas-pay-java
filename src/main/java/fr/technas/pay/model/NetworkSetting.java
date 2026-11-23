package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;

/**
 * Un réseau ouvert pour votre compte, dans un pays, et s'il est activé (votre réglage).
 *
 * @param network le réseau ({@code Network}), ex. {@code mixx}
 * @param name le nom que le client connaît (ex. « Mixx by Yas »)
 * @param country le pays (ISO)
 * @param currency la devise du réseau, ou {@code null}
 * @param enabled activé sur votre compte
 */
public record NetworkSetting(String network, String name, String country, String currency, boolean enabled) {

    public static NetworkSetting fromJson(JsonNode r) {
        String network = Json.str(r, "network");
        String name = Json.nstr(r, "name");
        return new NetworkSetting(network, name == null ? network : name, Json.nstr(r, "country"),
                Json.nstr(r, "currency"), Json.bool(r, "enabled"));
    }
}
