package exemple

import fr.technas.pay.Status
import fr.technas.pay.TechnasPay
import fr.technas.pay.app.AppOrder
import fr.technas.pay.model.Customer
import fr.technas.pay.model.PaymentRequest
import fr.technas.pay.servlet.TechnasPayServlet
import fr.technas.pay.webhook.Signature
import fr.technas.pay.webhook.WebhookHandlers
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Boutique

fun main(args: Array<String>) {
    runApplication<Boutique>(*args)
}

data class Order(val id: String, val owner: String, val total: Long, val email: String, val name: String, var attempt: Int = 1)

val orders = ConcurrentHashMap<String, Order>()

@RestController
class Paiement {
    private val pay = TechnasPay.fromEnv() // lit TECHNAS_PAY_SECRET au premier appel

    @PostMapping("/commandes/{id}/payer")
    fun payer(@PathVariable id: String): ResponseEntity<Void> {
        val o = orders.getValue(id) // relue EN BASE
        val p = pay.askForPayment(
            PaymentRequest.builder()
                .amount(o.total)
                .reference(Status.retryReference(o.id, o.attempt))
                .customer(Customer.of(o.email, o.name))
                .returnUrl("https://votre-site.com/merci")
                .build(),
        )
        return ResponseEntity.status(303).location(URI.create(p.checkoutUrl())).build()
    }

    @PostMapping("/technas/webhook")
    fun webhook(@RequestBody raw: String, @RequestHeader(Signature.HEADER, required = false) signature: String?): ResponseEntity<Map<String, Any>> {
        val r = pay.handleWebhook(
            raw, signature,
            WebhookHandlers.builder()
                .onCompleted { e -> println("payée : ${e.payment().reference()}") } // idempotent
                .build(),
        )
        return ResponseEntity.status(r.status()).body(r.body())
    }

    private val routes = pay.appRoutes<String>(
        { req -> (req.original() as HttpServletRequest).userPrincipal?.name }, // null → 401
        { orderId, user ->
            orders[orderId]?.takeIf { it.owner == user }?.let { AppOrder.of(it.total, Customer.of(it.email, it.name)) }
        },
    ).returnUrl("https://votre-app.com/paiement/retour").build()

    @RequestMapping("/api/technas-pay/**")
    fun app(req: HttpServletRequest, res: HttpServletResponse) = TechnasPayServlet.handle(routes, req, res, "/api/technas-pay")
}
