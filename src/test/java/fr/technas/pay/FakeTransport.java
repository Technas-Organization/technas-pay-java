package fr.technas.pay;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.http.Transport;
import fr.technas.pay.internal.Json;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Rend les réponses prévues, dans l'ordre, et note chaque appel. */
final class FakeTransport implements Transport {
    record Call(String method, String url, Map<String, String> headers, String body) {
        JsonNode json() {
            return body == null ? null : Json.parse(body);
        }
    }

    final List<Call> calls = new ArrayList<>();
    private final Deque<Object[]> queue = new ArrayDeque<>();

    FakeTransport() {
    }

    /** {@code status} 0 = réseau coupé ; {@code body} {@code null} = page HTML de panne. */
    FakeTransport reply(int status, Object body) {
        queue.add(new Object[] {status, body});
        return this;
    }

    FakeTransport replies(JsonNode gateway) {
        gateway.forEach(g -> queue.add(new Object[] {g.get("status").asInt(), g.get("body").isNull() ? null : g.get("body")}));
        return this;
    }

    @Override
    public Response send(String method, String url, Map<String, String> headers, String body) {
        calls.add(new Call(method, url, Map.copyOf(headers), body));
        Object[] next = queue.poll();
        if (next == null) {
            throw new IllegalStateException("appel imprévu à la passerelle : " + method + " " + url);
        }
        int status = (int) next[0];
        if (status == 0) {
            throw new TechnasPayException("network_error", "Service de paiement injoignable");
        }
        return new Response(status, next[1] == null ? "<html>panne</html>" : Json.write(next[1]));
    }
}
