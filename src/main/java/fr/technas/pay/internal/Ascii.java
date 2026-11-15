package fr.technas.pay.internal;

/**
 * Casse ASCII seulement. {@code toUpperCase}/{@code toLowerCase} appliquent les règles Unicode
 * (« ß » devient « SS », « ı » devient « I ») : une adresse, une devise ou un en-tête trafiqués
 * pourraient alors passer pour une valeur attendue. Ici, seuls A-Z et a-z changent.
 *
 * <p>Usage interne au SDK.
 */
public final class Ascii {
    private Ascii() {
    }

    public static String lower(String s) {
        if (s == null) {
            return null;
        }
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] >= 'A' && c[i] <= 'Z') {
                c[i] = (char) (c[i] + 32);
            }
        }
        return new String(c);
    }

    public static String upper(String s) {
        if (s == null) {
            return null;
        }
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] >= 'a' && c[i] <= 'z') {
                c[i] = (char) (c[i] - 32);
            }
        }
        return new String(c);
    }
}
