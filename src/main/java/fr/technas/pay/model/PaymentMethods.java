package fr.technas.pay.model;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Les moyens et réseaux ouverts pour votre compte ({@code pay.getPaymentMethods(...)}), ce que vous
 * avez activé, et votre ordre d'affichage.
 *
 * @param methods les moyens, et s'ils sont activés
 * @param networks les réseaux ouverts, par pays, dans VOTRE ordre d'affichage
 * @param networkOrder votre ordre d'affichage des réseaux
 */
public record PaymentMethods(List<MethodSetting> methods, List<NetworkSetting> networks, List<String> networkOrder) {

    public static PaymentMethods fromJson(JsonNode r) {
        List<MethodSetting> methods = new ArrayList<>();
        JsonNode m = r == null ? null : r.get("methods");
        if (m != null && m.isArray()) {
            m.forEach(n -> {
                if (n.isObject()) {
                    methods.add(MethodSetting.fromJson(n));
                }
            });
        }
        List<NetworkSetting> networks = new ArrayList<>();
        JsonNode list = r == null ? null : r.get("networks");
        if (list != null && list.isArray()) {
            list.forEach(n -> {
                if (n.isObject()) {
                    networks.add(NetworkSetting.fromJson(n));
                }
            });
        }
        return new PaymentMethods(List.copyOf(methods), List.copyOf(networks), Json.strings(r, "network_order"));
    }
}
