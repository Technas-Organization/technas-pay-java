package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import fr.technas.pay.app.AppOrder;
import fr.technas.pay.app.AppRequest;
import fr.technas.pay.app.AppResponse;
import fr.technas.pay.app.AppRoutes;
import fr.technas.pay.http.JdkTransport;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.Customer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Un test par constat de la relecture de sécurité de la PR #1 (M = moyen, F = faible, I = info). */
class RevueDeSecuriteTest {
    static final String SECRET = "tps_test_pk_abc.ws_secret";
    static final AppOrder ORDER = AppOrder.of(5000, Customer.of("a@b.c", "A"));

    static AppRoutes routes(FakeTransport gw, Set<String> owned, Function<AppRequest, Object> authorize) {
        TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(gw).build();
        return pay.<Object>appRoutes(authorize, (id, u) -> owned.contains(id) ? ORDER : null).build();
    }

    static Map<String, Object> payment(String reference, Map<String, Object> metadata) {
        Map<String, Object> p = new LinkedHashMap<>(ClientTest.payment(Map.of()));
        p.put("reference", reference);
        p.put("metadata", metadata);
        return p;
    }

    static AppResponse get(AppRoutes r, String path) {
        return r.handle(new AppRequest("GET", path, Map.of(), Map.of(), "", null));
    }

    @Test
    void m1LaReferenceAmbigueNOuvrePasLaCommandeDUnAutre() {
        // A possède « x ». Le paiement de B porte la référence « x-card-1 » pour SA commande « x-card ».
        FakeTransport gw = new FakeTransport().reply(200, payment("x-card-1", Map.of("order_id", "x-card")));
        assertEquals(404, get(routes(gw, Set.of("x"), r -> "A"), "/payments/pay_b").status());
        // Métadonnée et référence qui ne concordent pas : refusé.
        FakeTransport gw2 = new FakeTransport().reply(200, payment("y-1", Map.of("order_id", "x")));
        assertEquals(404, get(routes(gw2, Set.of("x", "y"), r -> "A"), "/payments/pay_b").status());
        // Référence sans essai (créée hors de ces routes) : refusé.
        FakeTransport gw3 = new FakeTransport().reply(200, payment("x", Map.of()));
        assertEquals(404, get(routes(gw3, Set.of("x"), r -> "A"), "/payments/pay_b").status());
        // Le cas légitime passe.
        FakeTransport gw4 = new FakeTransport().reply(200, payment("x-card-1", Map.of("order_id", "x")));
        assertEquals(200, get(routes(gw4, Set.of("x"), r -> "A"), "/payments/pay_a").status());
    }

    @Test
    void m2EtF1IdentifiantsHorsFormeRefusesSansAppel() {
        FakeTransport gw = new FakeTransport();
        AppRoutes r = routes(gw, Set.of(".."), x -> "A");
        assertEquals(404, r.handle(new AppRequest("POST", "/payments", Map.of(), Map.of(), "{\"orderId\":\"..\"}", null)).status());
        assertEquals(404, r.handle(new AppRequest("GET", "/methods", Map.of("orderId", " 42"), Map.of(), "", null)).status());
        assertEquals(404, get(r, "/payments/%2E%2E").status());
        assertEquals(404, get(r, "/payments/..%2F..%2Fbalance").status());
        assertTrue(gw.calls.isEmpty());
    }

    @Test
    void m4LaCleNeSortJamaisDansUneException() throws IOException {
        // Secret mal collé (caractère de contrôle) : refusé comme mal formé, sans le citer.
        TechnasPayException e = assertThrows(TechnasPayException.class,
                () -> TechnasPay.builder().secret("tps_test_pk\u0007abc.ws").transport(new FakeTransport()).build().getBalance());
        assertEquals("invalid_secret", e.code());
        // En-tête refusé par le JDK : son message citerait la valeur ; il est remplacé.
        TechnasPayException h = assertThrows(TechnasPayException.class,
                () -> new JdkTransport().send("GET", "http://127.0.0.1:9/", Map.of("Authorization", "Bearer cle\nsecrete"), null));
        assertFalse((h.getMessage() + h.getCause()).contains("secrete"));
    }

    @Test
    void f2UnRefusDeLaCleMarchandNEstPasUn401PourLApp() {
        FakeTransport gw = new FakeTransport().reply(401, Map.of("code", "invalid_api_key", "message", "Clé refusée"));
        AppResponse r = get(routes(gw, Set.of("x"), x -> "A"), "/payments/pay_1");
        assertEquals(502, r.status());
        assertEquals("upstream_error", Json.parse(r.json()).get("code").asText());
    }

    @Test
    void f3OptionalVideRefuse() {
        FakeTransport gw = new FakeTransport();
        assertEquals(401, get(routes(gw, Set.of("x"), x -> Optional.empty()), "/payments/pay_1").status());
        TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(gw).build();
        AppRoutes admin = pay.appRoutes(x -> "A", (id, u) -> ORDER).authorizeAdmin(x -> Optional.empty()).build();
        assertEquals(403, get(admin, "/card/account").status());
        assertTrue(gw.calls.isEmpty());
    }

    @Test
    void iToleranceDesAvisBornee() {
        for (long bad : new long[] {0, -1, 3601, Long.MAX_VALUE}) {
            assertThrows(TechnasPayException.class, () -> TechnasPay.builder().webhookToleranceSeconds(bad));
        }
    }

    @Test
    void iReponseDePlusDUnMoNonLue() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gros", ex -> {
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                byte[] chunk = new byte[65_536];
                for (int i = 0; i < 20; i++) {
                    out.write(chunk);
                }
            }
        });
        server.start();
        try {
            TechnasPayException e = assertThrows(TechnasPayException.class,
                    () -> new JdkTransport().send("GET", "http://127.0.0.1:" + server.getAddress().getPort() + "/gros", Map.of(), null));
            assertEquals("invalid_response", e.code());
        } finally {
            server.stop(0);
        }
    }
}
