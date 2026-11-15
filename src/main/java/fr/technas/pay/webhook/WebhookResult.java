package fr.technas.pay.webhook;

import java.util.Map;

/** La réponse à renvoyer : 200 {@code {"received":true}} ou 400 {@code {"error":"invalid_signature"}}. */
public record WebhookResult(int status, Map<String, Object> body, Event event) {
}
