package fr.technas.pay.http;

import java.util.Map;

/** Envoie une requête HTTP. Injectable : un faux transport sert aux tests. */
public interface Transport {
    /** Une réponse : statut et corps brut. */
    record Response(int status, String body) {
    }

    /**
     * @throws fr.technas.pay.TechnasPayException {@code network_error} si le service est injoignable
     */
    Response send(String method, String url, Map<String, String> headers, String body);
}
