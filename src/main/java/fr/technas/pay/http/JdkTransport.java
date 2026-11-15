package fr.technas.pay.http;

import fr.technas.pay.TechnasPayException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Transport par défaut, sur le client HTTP du JDK : TLS toujours vérifié (jamais désactivable),
 * délai de connexion et de réponse posés, AUCUNE redirection suivie (la clé ne repart jamais
 * vers une autre adresse).
 */
public final class JdkTransport implements Transport {
    /** Une réponse de Technas pèse quelques Ko : au-delà de 1 Mo, elle n'est pas lue. */
    private static final int MAX_RESPONSE_BYTES = 1_048_576;

    private final HttpClient client;
    private final Duration timeout;

    public JdkTransport() {
        this(Duration.ofSeconds(30));
    }

    public JdkTransport(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, Math.max(1, timeout.toSeconds()))))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Response send(String method, String url, Map<String, String> headers, String body) {
        HttpRequest request;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
            headers.forEach(b::header);
            b.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            request = b.build();
        } catch (IllegalArgumentException e) {
            // Le message du JDK cite la valeur de l'en-tête refusé, donc la clé : on ne le garde pas.
            throw new TechnasPayException("invalid_request", "Requête impossible à construire (secret ou adresse mal formés)");
        }
        try {
            HttpResponse<InputStream> r = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] raw;
            try (InputStream in = r.body()) {
                raw = in.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (raw.length > MAX_RESPONSE_BYTES) {
                throw new TechnasPayException("invalid_response", "Réponse trop grande du service de paiement", r.statusCode(), Map.of());
            }
            return new Response(r.statusCode(), new String(raw, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TechnasPayException("network_error", "Service de paiement injoignable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TechnasPayException("network_error", "Service de paiement injoignable");
        }
    }
}
