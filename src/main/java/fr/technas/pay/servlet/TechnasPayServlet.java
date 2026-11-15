package fr.technas.pay.servlet;

import fr.technas.pay.internal.Ascii;
import fr.technas.pay.app.AppRequest;
import fr.technas.pay.app.AppResponse;
import fr.technas.pay.app.AppRoutes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Les routes toutes faites sur l'API Servlet (Jakarta) : Spring Boot (MVC), Quarkus, Tomcat, Jetty.
 *
 * <p>Spring Boot :
 *
 * <pre>{@code
 * @RestController
 * class TechnasPayController {
 *     private final AppRoutes routes = ...;
 *     @RequestMapping("/api/technas-pay/**")
 *     void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
 *         TechnasPayServlet.handle(routes, req, res, "/api/technas-pay");
 *     }
 * }
 * }</pre>
 */
public final class TechnasPayServlet {
    /** Un avis Technas pèse quelques Ko ; une requête d'app, moins encore. Au-delà : 413, sans tout lire. */
    private static final int MAX_BODY_BYTES = 65_536;

    private TechnasPayServlet() {
    }

    public static void handle(AppRoutes routes, HttpServletRequest req, HttpServletResponse res, String basePath) throws IOException {
        String prefix = "/" + basePath.replaceAll("^/+|/+$", "");
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        if (!uri.equals(prefix) && !uri.startsWith(prefix + "/")) {
            // Hors du préfixe (ou « /api/technas-payX ») : rien n'est routé.
            res.setStatus(404);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.getWriter().write(new AppResponse(404, Map.of("code", "not_found", "message", "Route inconnue", "retryable", false)).json());
            return;
        }
        String path = uri.substring(prefix.length());

        byte[] raw;
        try (InputStream in = req.getInputStream()) {
            raw = in.readNBytes(MAX_BODY_BYTES + 1); // corps BRUT : la signature des avis porte dessus
        }
        AppResponse r;
        if (raw.length > MAX_BODY_BYTES) {
            r = new AppResponse(413, Map.of("code", "too_large", "message", "Corps trop grand", "retryable", false));
        } else {
            Map<String, String> headers = new HashMap<>();
            for (String name : Collections.list(req.getHeaderNames())) {
                headers.put(Ascii.lower(name), req.getHeader(name));
            }
            Map<String, String> query = new HashMap<>();
            req.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    query.put(k, v[0]);
                }
            });
            r = routes.handle(new AppRequest(req.getMethod(), path, query, headers, new String(raw, StandardCharsets.UTF_8), req));
        }
        res.setStatus(r.status());
        res.setContentType("application/json");
        res.setHeader("X-Content-Type-Options", "nosniff"); // jamais lu comme du HTML
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(r.json());
    }
}
