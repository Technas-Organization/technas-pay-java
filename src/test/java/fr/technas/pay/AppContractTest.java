package fr.technas.pay;

import static fr.technas.pay.Jsons.assertJsonEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.technas.pay.app.AppOrder;
import fr.technas.pay.app.AppRequest;
import fr.technas.pay.app.AppResponse;
import fr.technas.pay.app.AppRoutes;
import fr.technas.pay.internal.Json;
import fr.technas.pay.model.Customer;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookHandlers;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Rejoue contract/app-contract.v1.json : le même fichier que les SDK Node, Python, Dart, PHP et le
 * client Flutter.
 */
class AppContractTest {
    static final String WEBHOOK_SECRET = "ws_secret";
    static final String SECRET = "tps_test_pk_abc." + WEBHOOK_SECRET;

    static JsonNode contract() throws IOException {
        return Json.parse(Files.readString(Path.of("contract/app-contract.v1.json"), StandardCharsets.UTF_8));
    }

    static JsonNode caseNamed(String name) throws IOException {
        for (JsonNode c : contract().get("cases")) {
            if (name.equals(c.get("name").asText())) {
                return c;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static AppOrder order(JsonNode o) {
        JsonNode c = o.get("customer");
        return new AppOrder(o.get("amount").decimalValue(),
                new Customer(Json.nstr(c, "email"), Json.nstr(c, "name"), Json.nstr(c, "phone"), Json.nstr(c, "country")),
                Json.nstr(o, "currency"), Json.nstr(o, "description"), Json.nstr(o, "returnUrl"), Map.of());
    }

    static AppRoutes routes(JsonNode c, TechnasPay pay, WebhookHandlers webhooks) {
        String user = c.get("user").isNull() ? null : c.get("user").asText();
        JsonNode orders = c.get("orders");
        AppRoutes.Builder<String> b = pay.<String>appRoutes(
                        req -> user,
                        (id, u) -> u != null && u.equals(user) && orders.has(id) ? order(orders.get(id)) : null)
                .returnUrl("https://app.test/retour")
                .webhooks(webhooks)
                .card(c.path("options").path("card").asBoolean(false));
        if (c.has("admin")) {
            String admin = c.get("admin").isNull() ? null : c.get("admin").asText();
            b.authorizeAdmin(req -> admin);
        }
        return b.build();
    }

    static Map<String, String> query(JsonNode req) {
        Map<String, String> q = new HashMap<>();
        req.path("query").properties().forEach(e -> q.put(e.getKey(), e.getValue().asText()));
        return q;
    }

    @TestFactory
    Stream<DynamicTest> contrat() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : contract().get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                FakeTransport gw = new FakeTransport().replies(c.get("gateway"));
                TechnasPay pay = TechnasPay.builder().secret(SECRET).baseUrl("https://gw.test").transport(gw).build();
                JsonNode req = c.get("request");
                AppResponse r = routes(c, pay, null).handle(new AppRequest(req.get("method").asText(), req.get("path").asText(),
                        query(req), Map.of(), req.has("body") ? Json.write(req.get("body")) : "", null));
                assertEquals(c.get("expect").get("status").asInt(), r.status());
                JsonNode body = Json.parse(r.json());
                if (r.status() >= 400) {
                    assertTrue(body.get("message").isTextual());
                    ((ObjectNode) body).remove("message");
                }
                assertJsonEquals(c.get("expect").get("body"), body);
                JsonNode expectGw = c.get("expect_gateway");
                assertEquals(expectGw.size(), gw.calls.size(), "nombre d'appels à la passerelle");
                for (int i = 0; i < expectGw.size(); i++) {
                    JsonNode e = expectGw.get(i);
                    assertEquals(e.get("method").asText(), gw.calls.get(i).method());
                    assertEquals(e.get("path").asText(), URI.create(gw.calls.get(i).url()).getRawPath());
                    JsonNode sent = gw.calls.get(i).json();
                    e.path("body_includes").properties().forEach(kv -> assertJsonEquals(kv.getValue(), sent.path(kv.getKey())));
                    String rawQuery = URI.create(gw.calls.get(i).url()).getRawQuery();
                    Map<String, String> q = new HashMap<>();
                    if (rawQuery != null) {
                        for (String part : rawQuery.split("&")) {
                            String[] kv2 = part.split("=", 2);
                            q.put(java.net.URLDecoder.decode(kv2[0], StandardCharsets.UTF_8),
                                    kv2.length > 1 ? java.net.URLDecoder.decode(kv2[1], StandardCharsets.UTF_8) : "");
                        }
                    }
                    e.path("query_includes").properties().forEach(kv -> assertEquals(kv.getValue().asText(), q.get(kv.getKey()), kv.getKey()));
                }
            }));
        }
        assertEquals(30, tests.size(), "le contrat v1 a 30 cas");
        return tests.stream();
    }

    @Test
    void webhookSigneRefuseEtNonBranche() throws IOException {
        List<String> paid = new ArrayList<>();
        JsonNode c = Json.parse("{\"user\":\"u1\",\"orders\":{}}");
        TechnasPay pay = TechnasPay.of(SECRET);
        AppRoutes routes = routes(c, pay, WebhookHandlers.builder().onCompleted(e -> paid.add(e.payment().reference())).build());
        String body = "{\"id\":\"e1\",\"type\":\"payment.completed\",\"created_at\":\"x\",\"data\":{\"id\":\"p\",\"reference\":\"42-1\","
                + "\"status\":\"completed\",\"amount\":1,\"currency\":\"XOF\"}}";
        AppResponse ok = routes.handle(new AppRequest("POST", "/webhook", Map.of(),
                Map.of("x-technas-payment-signature", Signature.sign(body, WEBHOOK_SECRET)), body, null));
        assertEquals(200, ok.status());
        assertEquals(List.of("42-1"), paid);
        AppResponse bad = routes.handle(new AppRequest("POST", "/webhook", Map.of(), Map.of("x-technas-payment-signature", "t=1,s=00"), body, null));
        assertEquals(400, bad.status());
        assertEquals(List.of("42-1"), paid);
        assertEquals(404, routes(c, pay, null).handle(new AppRequest("POST", "/webhook", Map.of(), Map.of(), body, null)).status());
    }
}
