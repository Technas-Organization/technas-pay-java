package fr.technas.pay.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON borné et typé. Modèle en arbre SEULEMENT (aucune classe instanciée depuis la donnée reçue,
 * donc aucune désérialisation polymorphe) ; profondeur et taille limitées.
 *
 * <p>Usage interne au SDK.
 */
public final class Json {
    private Json() {
    }

    /**
     * JSON strict : pas de NaN, pas de clé en double (deux lectures possibles d'un même avis), rien
     * après la valeur, profondeur 64, chaînes d'1 Mo, nombres de 64 chiffres au plus.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(64)
                            .maxStringLength(1_000_000)
                            .maxNumberLength(64)
                            .build())
                    .build())
            .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    /** Lit un JSON ; lève {@link IllegalArgumentException} s'il est illisible. */
    public static JsonNode parse(String raw) {
        try {
            JsonNode n = MAPPER.readTree(raw);
            if (n == null) {
                throw new IllegalArgumentException("JSON vide");
            }
            return n;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON illisible", e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON impossible à écrire", e);
        }
    }

    /**
     * JSON à renvoyer à un navigateur ou une app : {@code < > & '} sont écrits {@code \\u003c}…, si
     * bien qu'une réponse lue par erreur comme du HTML n'ouvre aucune balise.
     */
    public static String writeForHttp(Object value) {
        try {
            return HTTP_WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON impossible à écrire", e);
        }
    }

    private static final ObjectWriter HTTP_WRITER = MAPPER.writer().with(new HtmlSafeEscapes());

    private static final class HtmlSafeEscapes extends CharacterEscapes {
        private static final long serialVersionUID = 1L;
        private final int[] escapes;

        HtmlSafeEscapes() {
            escapes = CharacterEscapes.standardAsciiEscapesForJSON();
            for (char c : new char[] {'<', '>', '&', '\''}) {
                escapes[c] = CharacterEscapes.ESCAPE_STANDARD;
            }
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return escapes.clone();
        }

        @Override
        public SerializableString getEscapeSequence(int ch) {
            return null;
        }
    }

    public static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    public static String str(JsonNode n, String k) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && v.isTextual() ? v.asText() : v != null && v.isIntegralNumber() ? v.asText() : "";
    }

    public static String nstr(JsonNode n, String k) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    /** Un montant : décimal exact (jamais un double), 0 s'il manque ou n'est pas un nombre. */
    public static BigDecimal num(JsonNode n, String k) {
        BigDecimal v = nnum(n, k);
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal nnum(JsonNode n, String k) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && v.isNumber() ? v.decimalValue() : null;
    }

    public static boolean bool(JsonNode n, String k) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && v.isBoolean() && v.asBoolean();
    }

    public static JsonNode obj(JsonNode n, String k) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && v.isObject() ? v : object();
    }

    /** Un objet JSON en carte Java (types simples, listes, cartes), pour les métadonnées. */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> map(JsonNode n, String k) {
        JsonNode v = obj(n, k);
        return java.util.Collections.unmodifiableMap(MAPPER.convertValue(v, java.util.LinkedHashMap.class));
    }

    /** Un nœud quelconque en types Java simples (cartes, listes, chaînes, nombres, booléens). */
    public static Object plain(JsonNode n) {
        return n == null ? null : MAPPER.convertValue(n, Object.class);
    }

    public static List<String> strings(JsonNode n, String k) {
        List<String> out = new ArrayList<>();
        JsonNode v = n == null ? null : n.get(k);
        if (v != null && v.isArray()) {
            v.forEach(x -> {
                if (x.isTextual()) {
                    out.add(x.asText());
                }
            });
        }
        return List.copyOf(out);
    }
}
