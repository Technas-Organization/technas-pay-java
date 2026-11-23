package fr.technas.pay;

import java.util.List;

/**
 * Les réseaux mobile money : le porte-monnaie que le client a dans sa poche, jamais l'intermédiaire
 * qui fait passer le paiement. {@link #MIXX} = Mixx by Yas (ex-T-Money, Togocel), {@link #MOOV} =
 * Moov Money (Flooz au Togo). Ceux réellement ouverts, par pays : {@code pay.getPaymentMethods(country)}.
 *
 * <p>Un réseau se lit comme une chaîne : un réseau plus récent que ce SDK est gardé tel quel.
 */
public final class Network {
    private Network() {
    }

    public static final String MIXX = "mixx";
    public static final String MOOV = "moov";
    public static final String MTN_MOMO = "mtn_momo";
    public static final String ORANGE_MONEY = "orange_money";
    public static final String WAVE = "wave";
    public static final String AIRTEL_MONEY = "airtel_money";
    public static final String FREE_MONEY = "free_money";
    public static final String CELTIIS_CASH = "celtiis_cash";
    public static final String DJAMO = "djamo";
    public static final String EXPRESSO = "expresso";
    public static final String WIZALL = "wizall";
    public static final String MOBICASH = "mobicash";
    public static final String MPESA = "mpesa";
    public static final String TELECEL_CASH = "telecel_cash";
    public static final String AIRTELTIGO_MONEY = "airteltigo_money";
    public static final String AFRIMONEY = "afrimoney";
    public static final String QMONEY = "qmoney";
    public static final String TNM_MPAMBA = "tnm_mpamba";
    public static final String EMOLA = "emola";
    public static final String ZAMTEL_KWACHA = "zamtel_kwacha";
    public static final String EZYPESA = "ezypesa";
    public static final String HALOPESA = "halopesa";
    public static final String TPESA = "tpesa";

    /** Tous les réseaux, à énumérer, dans l'ordre du serveur. */
    public static final List<String> ALL = List.of(
            MIXX, MOOV, MTN_MOMO, ORANGE_MONEY, WAVE, AIRTEL_MONEY, FREE_MONEY, CELTIIS_CASH,
            DJAMO, EXPRESSO, WIZALL, MOBICASH, MPESA, TELECEL_CASH, AIRTELTIGO_MONEY, AFRIMONEY,
            QMONEY, TNM_MPAMBA, EMOLA, ZAMTEL_KWACHA, EZYPESA, HALOPESA, TPESA);
}
