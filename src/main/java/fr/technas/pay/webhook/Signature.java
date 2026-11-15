package fr.technas.pay.webhook;

import fr.technas.pay.internal.Ascii;
import fr.technas.pay.internal.Json;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code X-Technas-Payment-Signature: t=<horodatage>,s=<hmac_sha256_hex>}, HMAC de
 * {@code t + "." + corpsBrut} avec la partie webhook du secret. Toujours sur le corps BRUT, jamais
 * sur un JSON re-sérialisé.
 */
public final class Signature {
    public static final String HEADER = "X-Technas-Payment-Signature";
    public static final long DEFAULT_TOLERANCE_SECONDS = 300;
    private static final Pattern FORMAT = Pattern.compile("^t=(\\d{1,12}),s=([0-9a-fA-F]{64})$");

    private Signature() {
    }

    /** Valeur de l'en-tête pour ce corps (sert aux tests). */
    public static String sign(String body, String webhookSecret, long timestamp) {
        return "t=" + timestamp + ",s=" + hmac(webhookSecret, timestamp + "." + body);
    }

    public static String sign(String body, String webhookSecret) {
        return sign(body, webhookSecret, System.currentTimeMillis() / 1000);
    }

    /** Vérifie la signature sur le corps BRUT puis rend l'événement. */
    public static Event verify(String rawBody, String header, String webhookSecret, long toleranceSeconds, long nowSeconds) {
        Matcher m = FORMAT.matcher(header == null ? "" : header.trim());
        if (!m.matches()) {
            throw new WebhookVerificationException("Signature absente ou mal formée");
        }
        long ts = Long.parseLong(m.group(1));
        if (Math.abs(nowSeconds - ts) > toleranceSeconds) {
            throw new WebhookVerificationException("Signature trop ancienne");
        }
        byte[] expected = hmac(webhookSecret, ts + "." + rawBody).getBytes(StandardCharsets.US_ASCII);
        byte[] given = Ascii.lower(m.group(2)).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, given)) {
            throw new WebhookVerificationException("Signature invalide");
        }
        try {
            return Event.parse(Json.parse(rawBody));
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException("Webhook illisible");
        }
    }

    private static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
