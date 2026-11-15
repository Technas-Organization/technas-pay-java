package fr.technas.pay;

import static fr.technas.pay.Jsons.assertJsonEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.app.AppRoutes;
import fr.technas.pay.internal.Json;
import fr.technas.pay.servlet.TechnasPayServlet;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** L'adaptateur Servlet (Spring Boot MVC, Tomcat, Jetty), sans conteneur : requête et réponse simulées. */
class ServletTest {
    static HttpServletRequest request(String method, String uri, Map<String, String> params, Map<String, String> headers, byte[] body) {
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        ServletInputStream sis = new ServletInputStream() {
            @Override
            public int read() {
                return in.read();
            }

            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener l) {
            }
        };
        Map<String, String[]> pm = new HashMap<>();
        params.forEach((k, v) -> pm.put(k, new String[] {v}));
        return (HttpServletRequest) Proxy.newProxyInstance(ServletTest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getInputStream" -> sis;
                    case "getHeaderNames" -> Collections.enumeration(headers.keySet());
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getParameterMap" -> pm;
                    default -> null;
                });
    }

    record Res(HttpServletResponse res, AtomicInteger status, StringWriter out) {
    }

    static Res response() {
        AtomicInteger status = new AtomicInteger();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        HttpServletResponse res = (HttpServletResponse) Proxy.newProxyInstance(ServletTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, m, args) -> {
                    if ("setStatus".equals(m.getName())) {
                        status.set((int) args[0]);
                    }
                    return "getWriter".equals(m.getName()) ? pw : null;
                });
        return new Res(res, status, out);
    }

    @Test
    void sousUnPrefixeAvecLeCorpsBrut() throws IOException {
        JsonNode c = AppContractTest.caseNamed("démarrer : premier essai");
        FakeTransport gw = new FakeTransport().replies(c.get("gateway"));
        TechnasPay pay = TechnasPay.builder().secret(AppContractTest.SECRET).baseUrl("https://gw.test").transport(gw).build();
        AtomicReference<String> seen = new AtomicReference<>();
        AppRoutes routes = pay.<String>appRoutes(r -> {
            seen.set(r.header("Authorization"));
            return "u1";
        }, (id, u) -> AppContractTest.order(c.get("orders").get(id))).build();
        Res res = response();
        TechnasPayServlet.handle(routes, request("POST", "/api/technas-pay/payments", Map.of(), Map.of("Authorization", "Bearer jeton"),
                "{\"orderId\":\"42\"}".getBytes(StandardCharsets.UTF_8)), res.res(), "/api/technas-pay/");
        assertEquals(200, res.status().get());
        assertJsonEquals(c.get("expect").get("body"), Json.parse(res.out().toString()));
        assertEquals("Bearer jeton", seen.get());
    }

    @Test
    void parametresDeRequeteEtCorpsTropGrand() throws IOException {
        JsonNode c = AppContractTest.caseNamed("moyens ouverts pour une commande");
        TechnasPay pay = TechnasPay.builder().secret(AppContractTest.SECRET).baseUrl("https://gw.test").transport(new FakeTransport()).build();
        AppRoutes routes = AppContractTest.routes(c, pay, null);
        Res res = response();
        TechnasPayServlet.handle(routes, request("GET", "/api/technas-pay/methods", Map.of("orderId", "42"), Map.of(), new byte[0]),
                res.res(), "api/technas-pay");
        assertEquals(200, res.status().get());
        assertJsonEquals(c.get("expect").get("body"), Json.parse(res.out().toString()));
        Res big = response();
        TechnasPayServlet.handle(routes, request("POST", "/api/technas-pay/webhook", Map.of(), Map.of(), new byte[70_000]), big.res(), "/api/technas-pay");
        assertEquals(413, big.status().get());
    }

    @Test
    void reponseSansBaliseHtml() {
        String json = new fr.technas.pay.app.AppResponse(200, Map.of("m", "<script>alert('x')</script> & co")).json();
        org.junit.jupiter.api.Assertions.assertFalse(json.matches("(?s).*[<>&'].*"), json);
        assertEquals("<script>alert('x')</script> & co", Json.parse(json).get("m").asText());
    }
}
