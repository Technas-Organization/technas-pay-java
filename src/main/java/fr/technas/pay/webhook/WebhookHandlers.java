package fr.technas.pay.webhook;

import java.util.function.Consumer;

/**
 * Vos fonctions, toutes facultatives. Une exception doit REMONTER (réponse 5xx) : Technas relivre.
 * Un avis peut arriver plusieurs fois : soyez idempotents (gardez {@code event.id()}).
 *
 * <pre>{@code
 * WebhookHandlers.builder()
 *     .onCompleted(e -> orders.markPaid(e.payment().reference()))
 *     .onExpired(e -> orders.nextAttempt(e.payment().reference()))
 *     .build();
 * }</pre>
 */
public final class WebhookHandlers {
    private final Consumer<Event> onCompleted;
    private final Consumer<Event> onFailed;
    private final Consumer<Event> onExpired;
    private final Consumer<Event> onPayoutDestination;
    private final Consumer<Event> onRefund;
    private final Consumer<Event> onTopUp;
    private final Consumer<Event> onBalanceConverted;
    private final Consumer<Event> onFxModeUpdated;
    private final Consumer<Event> onCardAccountUpdated;
    private final Consumer<Event> onKycRequired;

    private WebhookHandlers(Builder b) {
        onCompleted = b.onCompleted;
        onFailed = b.onFailed;
        onExpired = b.onExpired;
        onPayoutDestination = b.onPayoutDestination;
        onRefund = b.onRefund;
        onTopUp = b.onTopUp;
        onBalanceConverted = b.onBalanceConverted;
        onFxModeUpdated = b.onFxModeUpdated;
        onCardAccountUpdated = b.onCardAccountUpdated;
        onKycRequired = b.onKycRequired;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** La fonction pour ce type d'événement ; {@code null} pour un type inconnu (acquitté sans rien appeler). */
    public Consumer<Event> forType(String type) {
        if ("payment.completed".equals(type)) {
            return onCompleted;
        }
        if ("payment.failed".equals(type)) {
            return onFailed;
        }
        if ("payment.expired".equals(type)) {
            return onExpired;
        }
        if (type.startsWith("payout_destination.")) {
            return onPayoutDestination;
        }
        if (type.startsWith("refund.")) {
            return onRefund;
        }
        if ("balance.topped_up".equals(type)) {
            return onTopUp;
        }
        if ("balance.converted".equals(type)) {
            return onBalanceConverted;
        }
        if ("fx_mode.updated".equals(type)) {
            return onFxModeUpdated;
        }
        if ("card_account.updated".equals(type)) {
            return onCardAccountUpdated;
        }
        if ("account.kyc_required".equals(type)) {
            return onKycRequired;
        }
        return null;
    }

    public static final class Builder {
        private Consumer<Event> onCompleted;
        private Consumer<Event> onFailed;
        private Consumer<Event> onExpired;
        private Consumer<Event> onPayoutDestination;
        private Consumer<Event> onRefund;
        private Consumer<Event> onTopUp;
        private Consumer<Event> onBalanceConverted;
        private Consumer<Event> onFxModeUpdated;
        private Consumer<Event> onCardAccountUpdated;
        private Consumer<Event> onKycRequired;

        private Builder() {
        }

        /** Marquez la commande payée. */
        public Builder onCompleted(Consumer<Event> f) {
            onCompleted = f;
            return this;
        }

        /** PAS définitif : n'annulez rien, ne relancez rien. */
        public Builder onFailed(Consumer<Event> f) {
            onFailed = f;
            return this;
        }

        /** Définitif : seul moment où relancer, avec une NOUVELLE référence. */
        public Builder onExpired(Consumer<Event> f) {
            onExpired = f;
            return this;
        }

        public Builder onPayoutDestination(Consumer<Event> f) {
            onPayoutDestination = f;
            return this;
        }

        /** Prévenez le client (Technas ne lui écrit pas). */
        public Builder onRefund(Consumer<Event> f) {
            onRefund = f;
            return this;
        }

        public Builder onTopUp(Consumer<Event> f) {
            onTopUp = f;
            return this;
        }

        public Builder onBalanceConverted(Consumer<Event> f) {
            onBalanceConverted = f;
            return this;
        }

        /** Si ce n'est pas vous qui avez changé d'option, prévenez Technas. */
        public Builder onFxModeUpdated(Consumer<Event> f) {
            onFxModeUpdated = f;
            return this;
        }

        public Builder onCardAccountUpdated(Consumer<Event> f) {
            onCardAccountUpdated = f;
            return this;
        }

        /** Seuil d'identité ({@code event.kyc()}) : prévenez le responsable, lien {@code consoleUrl()}. */
        public Builder onKycRequired(Consumer<Event> f) {
            onKycRequired = f;
            return this;
        }

        public WebhookHandlers build() {
            return new WebhookHandlers(this);
        }
    }
}
