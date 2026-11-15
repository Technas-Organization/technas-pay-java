package exemple;

import fr.technas.pay.Status;
import fr.technas.pay.TechnasPay;
import fr.technas.pay.app.AppOrder;
import fr.technas.pay.app.AppRoutes;
import fr.technas.pay.model.Customer;
import fr.technas.pay.model.Payment;
import fr.technas.pay.model.PaymentRequest;
import fr.technas.pay.servlet.TechnasPayServlet;
import fr.technas.pay.webhook.Signature;
import fr.technas.pay.webhook.WebhookHandlers;
import fr.technas.pay.webhook.WebhookResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Boutique {
    public static void main(String[] args) {
        SpringApplication.run(Boutique.class, args);
    }

    /** Un seul client, qui lit TECHNAS_PAY_SECRET au premier appel. */
    @Bean
    TechnasPay technasPay() {
        return TechnasPay.fromEnv();
    }

    /** Votre table des commandes (ici en mémoire). */
    record Order(String id, String owner, long total, String email, String name, int attempt, boolean paid) {
    }

    static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();

    /** Site web : « Payer » redirige (303) vers la page Technas. */
    @RestController
    static class Paiement {
        private final TechnasPay pay;

        Paiement(TechnasPay pay) {
            this.pay = pay;
        }

        @PostMapping("/commandes/{id}/payer")
        ResponseEntity<Void> payer(@PathVariable String id, Principal user) {
            Order o = ORDERS.get(id); // relue EN BASE : le montant ne vient jamais du navigateur
            if (o == null || user == null || !o.owner().equals(user.getName())) {
                return ResponseEntity.notFound().build(); // jamais la commande d'un autre
            }
            Payment p = pay.askForPayment(PaymentRequest.builder()
                    .amount(o.total())                                    // 5000 = 5 000 FCFA
                    .reference(Status.retryReference(o.id(), o.attempt())) // "42-1" : une référence = un essai
                    .customer(Customer.of(o.email(), o.name()))
                    .returnUrl("https://votre-site.com/merci")
                    .metadata(Map.of("order_id", o.id()))                  // relu dans l'avis
                    .build());
            return ResponseEntity.status(303).location(URI.create(p.checkoutUrl())).build();
        }

        /** L'avis signé : corps BRUT (String), jamais un objet re-sérialisé. */
        @PostMapping("/technas/webhook")
        ResponseEntity<Map<String, Object>> webhook(@RequestBody String raw,
                @RequestHeader(value = Signature.HEADER, required = false) String signature) {
            WebhookResult r = pay.handleWebhook(raw, signature, WebhookHandlers.builder()
                    .onCompleted(e -> ORDERS.computeIfPresent(e.payment().metadata().getOrDefault("order_id", "").toString(),
                            (k, o) -> new Order(o.id(), o.owner(), o.total(), o.email(), o.name(), o.attempt(), true)))
                    .onExpired(e -> { /* seul moment où relancer : nouvelle référence (attempt + 1) */ })
                    .build());
            return ResponseEntity.status(r.status()).body(r.body());
        }
    }

    /** App mobile (technas_pay_flutter) : les routes toutes faites sous /api/technas-pay. */
    @RestController
    static class AppMobile {
        private final AppRoutes routes;

        AppMobile(TechnasPay pay) {
            this.routes = pay.<Principal>appRoutes(
                            req -> ((HttpServletRequest) req.original()).getUserPrincipal(), // null → 401 (Spring Security)
                            (orderId, user) -> {
                                Order o = ORDERS.get(orderId);
                                return o == null || !o.owner().equals(user.getName()) ? null // pas à lui → 404
                                        : AppOrder.of(o.total(), Customer.of(o.email(), o.name()));
                            })
                    .returnUrl("https://votre-app.com/paiement/retour")
                    .build();
        }

        @RequestMapping("/api/technas-pay/**")
        void handle(HttpServletRequest req, HttpServletResponse res) throws IOException {
            TechnasPayServlet.handle(routes, req, res, "/api/technas-pay");
        }
    }
}
