package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import fr.technas.pay.http.JdkTransport;
import fr.technas.pay.http.Transport;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookVerificationException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Les exigences de sécurité, une par test : un avis falsifié, rejoué ou trop vieux est REFUSÉ. */
class SecurityTest {
    static final String WEBHOOK_SECRET = "ws_secret_789";
    static final String SECRET = "tps_live_pk_live_123." + WEBHOOK_SECRET;
    static final String BODY = "{\"id\":\"evt_1\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":"
            + "{\"id\":\"pay_1\",\"reference\":\"42-1\",\"status\":\"completed\",\"amount\":5000,\"currency\":\"XOF\"}}";

    static long now() {
        return System.currentTimeMillis() / 1000;
    }

    static TechnasPay pay() {
        return TechnasPay.builder().secret(SECRET).transport(new FakeTransport()).build();
    }

    @Test
    void avisFalsifieRefuse() {
        String good = Signature.sign(BODY, WEBHOOK_SECRET);
        // Corps modifié après signature, autre secret, signature tronquée ou bricolée : refusés.
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY.replace("5000", "5"), good));
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, Signature.sign(BODY, "autre")));
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, good.substring(0, good.length() - 1)));
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, good + ",s=" + "0".repeat(64)));
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, null));
        assertEquals("evt_1", pay().verifyWebhook(BODY, good).id());
    }

    @Test
    void avisRejoueOuTropVieuxRefuse() {
        // Une vieille signature rejouée (même corps, horodatage d'il y a 6 min) et une signature du futur.
        String old = Signature.sign(BODY, WEBHOOK_SECRET, now() - 360);
        String future = Signature.sign(BODY, WEBHOOK_SECRET, now() + 360);
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, old));
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, future));
        // L'horodatage est signé : le remplacer par un récent casse la signature.
        String forged = old.replaceFirst("t=\\d+", "t=" + now());
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(BODY, forged));
        assertEquals(400, pay().handleWebhook(BODY, old, fr.technas.pay.webhook.WebhookHandlers.builder().build()).status());
    }

    @Test
    void comparaisonEnTempsConstant() throws IOException {
        String src = Files.readString(Path.of("src/main/java/fr/technas/pay/webhook/Signature.java"));
        assertTrue(src.contains("MessageDigest.isEqual"));
        assertFalse(src.matches("(?s).*expected\\.equals\\(.*"));
    }

    @Test
    void corpsJsonPiegeRefuse() {
        String deep = "{\"id\":\"e\",\"type\":\"x\",\"data\":" + "[".repeat(200) + "]".repeat(200) + "}";
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(deep, Signature.sign(deep, WEBHOOK_SECRET)));
        String nan = "{\"id\":\"e\",\"type\":\"x\",\"data\":{\"a\":NaN}}";
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(nan, Signature.sign(nan, WEBHOOK_SECRET)));
        String dup = "{\"id\":\"e\",\"type\":\"payment.completed\",\"type\":\"x\",\"data\":{}}";
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(dup, Signature.sign(dup, WEBHOOK_SECRET)));
        String trailing = BODY + "{}";
        assertThrows(WebhookVerificationException.class, () -> pay().verifyWebhook(trailing, Signature.sign(trailing, WEBHOOK_SECRET)));
    }

    @Test
    void cleJamaisVersUneAdresseEnClair() {
        for (String url : List.of("http://pay-api.technas.fr", "ftp://x", "http://10.0.0.1", "file:///etc/passwd")) {
            TechnasPayException e = assertThrows(TechnasPayException.class, () -> TechnasPay.builder().secret(SECRET).baseUrl(url).build());
            assertEquals("invalid_base_url", e.code());
        }
        TechnasPay.builder().secret(SECRET).baseUrl("http://localhost:8080").build();
    }

    @Test
    void leClientNeSeSerialisePas() {
        assertThrows(java.io.NotSerializableException.class,
                () -> new ObjectOutputStream(OutputStream.nullOutputStream()).writeObject(pay()));
    }

    @Test
    void leSecretNApparaitNullePart() {
        FakeTransport t = new FakeTransport().reply(401, Map.of("code", "unauthorized", "message", "Clé refusée"));
        TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(t).build();
        TechnasPayException e = assertThrows(TechnasPayException.class, pay::getBalance);
        String seen = pay + e.getMessage() + e.details() + e;
        assertFalse(seen.contains("pk_live_123"));
        assertFalse(seen.contains(WEBHOOK_SECRET));
        assertFalse(t.calls.get(0).url().contains("pk_live_123"), "jamais dans l'URL");
    }

    @Test
    void reponseIllisibleNonInterpretee() {
        FakeTransport t = new FakeTransport().reply(200, null);
        TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(t).build();
        assertEquals("invalid_response", assertThrows(TechnasPayException.class, pay::getBalance).code());
        Transport raw = (m, u, h, b) -> new Transport.Response(200, "{\"id\":\"p\"} trailing");
        TechnasPay pay2 = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(raw).build();
        assertEquals("invalid_response", assertThrows(TechnasPayException.class, () -> pay2.getPayment("p")).code());
    }

    @Test
    void aucuneRedirectionSuivieEtDelaiPose() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger leaked = new AtomicInteger();
        server.createContext("/v1/balance", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + ex.getLocalAddress().getPort() + "/ailleurs");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/ailleurs", ex -> {
            if (ex.getRequestHeaders().containsKey("Authorization")) {
                leaked.incrementAndGet();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/lent", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl(base).build();
            TechnasPayException e = assertThrows(TechnasPayException.class, pay::getBalance);
            assertEquals(302, e.status());
            assertEquals(0, leaked.get(), "la clé n'a pas suivi la redirection");
            long t0 = System.nanoTime();
            TechnasPayException slow = assertThrows(TechnasPayException.class,
                    () -> new JdkTransport(Duration.ofSeconds(1)).send("GET", base + "/lent", Map.of(), null));
            assertEquals("network_error", slow.code());
            assertTrue(System.nanoTime() - t0 < 2_500_000_000L, "délai de réponse respecté");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tlsJamaisDesactive() throws IOException {
        // Aucun code du SDK ne fournit de TrustManager, de HostnameVerifier ni ne touche aux
        // propriétés qui désactivent la vérification.
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                for (String banned : List.of("TrustManager", "HostnameVerifier", "disableHostnameVerification", "SSLContext",
                        "setProperty(", "ObjectInputStream", "readObject", "Class.forName", "enableDefaultTyping", "activateDefaultTyping")) {
                    assertFalse(src.contains(banned), f + " contient " + banned);
                }
            }
        }
    }
}
