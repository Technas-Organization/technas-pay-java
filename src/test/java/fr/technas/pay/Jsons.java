package fr.technas.pay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import fr.technas.pay.internal.Json;

final class Jsons {
    private Jsons() {
    }

    /** Égalité JSON où 5000, 5000.0 et 5E+3 sont le même nombre. */
    static void assertJsonEquals(JsonNode expected, JsonNode actual) {
        boolean same = expected.equals((a, b) -> {
            if (a.isNumber() && b.isNumber()) {
                return a.decimalValue().compareTo(b.decimalValue());
            }
            return a.equals(b) ? 0 : 1;
        }, actual);
        assertTrue(same, () -> "attendu " + Json.write(expected) + "\nobtenu  " + Json.write(actual));
    }
}
