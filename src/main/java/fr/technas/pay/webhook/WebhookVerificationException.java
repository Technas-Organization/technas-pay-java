package fr.technas.pay.webhook;

/** Signature absente, mal formée, trop ancienne, fausse, ou corps illisible : répondez 400. */
public final class WebhookVerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WebhookVerificationException(String message) {
        super(message);
    }
}
