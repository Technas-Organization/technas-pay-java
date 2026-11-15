package fr.technas.pay.http;

import fr.technas.pay.TechnasPayException;
import java.io.IOException;
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        headers.forEach(b::header);
        b.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new TechnasPayException("network_error", "Service de paiement injoignable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TechnasPayException("network_error", "Service de paiement injoignable");
        }
    }
}
