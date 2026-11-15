package fr.technas.pay.app;

import fr.technas.pay.internal.Ascii;
import java.util.Map;

/**
 * La requête, vue indépendamment du framework. {@code path} est relatif au préfixe de montage
 * ({@code /payments}…) ; {@code headers} a des clés en minuscules ; {@code original} est la requête
 * du framework, passée telle quelle à {@code authorize}.
 */
public record AppRequest(String method, String path, Map<String, String> query, Map<String, String> headers, String rawBody,
                         Object original) {
    public String header(String name) {
        return headers.get(Ascii.lower(name));
    }
}
