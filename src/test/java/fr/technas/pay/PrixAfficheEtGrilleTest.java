package fr.technas.pay;

import static fr.technas.pay.ClientTest.pay;
import static fr.technas.pay.FraisEtDevisTest.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.technas.pay.internal.Json;
import fr.technas.pay.model.DisplayPrice;
import fr.technas.pay.model.DisplayPriceNetwork;
import fr.technas.pay.model.DisplayPriceRequest;
import fr.technas.pay.model.FeeTier;
import fr.technas.pay.model.RateNetwork;
import fr.technas.pay.model.Rates;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Le prix affiché fixe ({@code displayPrice}) et la grille des frais ({@code getRates}). */
class PrixAfficheEtGrilleTest {

    static String ligne(String pays, String reseau, int net) {
        return "{\"country\":\"" + pays + "\",\"network\":\"" + reseau + "\",\"network_name\":\"" + reseau
                + "\",\"customer_total\":1100,\"processing_fee\":44,\"commission\":22,\"net_amount\":" + net
                + ",\"operator_fee_estimate\":null}";
    }

    static String pays(String pays, String reseau, int prix, int net) {
        return "{\"price\":" + prix + ",\"worst_network\":" + ligne(pays, reseau, net) + ",\"net_by_network\":["
                + ligne(pays, reseau, net) + "],\"currency\":\"XOF\"}";
    }

    @Test
    void displayPriceEnvoieReceivePaysEtPasEtLitUnPrixParPaysEtPartout() {
        String tg = pays("TG", "mixx", 1100, 1034);
        String bj = pays("BJ", "mtn_momo", 1150, 1010);
        String reponse = "{\"price\":1150,\"worst_network\":" + ligne("BJ", "mtn_momo", 1010) + ",\"net_by_network\":["
                + ligne("BJ", "mtn_momo", 1010) + "],\"currency\":\"XOF\",\"fees_paid_by\":\"partner\",\"receive\":1000,"
                + "\"round_to\":50,\"countries\":[\"TG\",\"BJ\"],\"by_country\":{\"TG\":" + tg + ",\"BJ\":" + bj
                + "},\"everywhere\":" + bj + ",\"how_to_use\":\"x\",\"operator_fee_note\":\"y\"}";
        String mixte = "{\"price\":1100,\"currency\":\"XOF\",\"fees_paid_by\":\"partner\",\"receive\":1000,\"round_to\":50,"
                + "\"countries\":[\"TG\",\"CM\"],\"by_country\":{\"TG\":" + tg + "},\"everywhere\":null}";
        FakeTransport t = new FakeTransport().reply(200, Json.parse(reponse)).reply(200, Json.parse(reponse))
                .reply(200, Json.parse(mixte));
        TechnasPay pay = pay(t);

        DisplayPrice d = pay.displayPrice(DisplayPriceRequest.builder().receive(1000).countries(List.of("tg", " BJ"))
                .roundTo(50).build());
        assertEquals("POST", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/display-price", t.calls.get(0).url());
        Jsons.assertJsonEquals(Json.parse("{\"receive\":1000,\"countries\":[\"TG\",\"BJ\"],\"round_to\":50}"),
                t.calls.get(0).json());
        eq(1150, d.price());
        assertEquals("partner", d.feesPaidBy());
        eq(1100, d.byCountry().get("TG").price());
        eq(1150, d.byCountry().get("BJ").price());
        eq(1150, d.everywhere().price());
        eq(1000, d.receive());
        eq(50, d.roundTo());
        assertEquals(List.of("TG", "BJ"), d.countries());
        DisplayPriceNetwork w = d.worstNetwork();
        assertEquals(new DisplayPriceNetwork("BJ", "mtn_momo", "mtn_momo", BigDecimal.valueOf(1100), BigDecimal.valueOf(44),
                BigDecimal.valueOf(22), BigDecimal.valueOf(1010), null), w);
        assertEquals(1, d.byCountry().get("TG").netByNetwork().size());
        eq(1034, d.byCountry().get("TG").worstNetwork().netAmount());

        pay.getDisplayPrice(DisplayPriceRequest.builder().receive(1000).country("TG").build());
        Jsons.assertJsonEquals(Json.parse("{\"receive\":1000,\"country\":\"TG\"}"), t.calls.get(1).json());

        DisplayPrice m = pay.displayPrice(DisplayPriceRequest.builder().receive(1000).countries(List.of("TG", "CM")).build());
        assertNull(m.everywhere(), "XOF et XAF mélangés : pas de prix valable partout");
        assertNull(m.worstNetwork());
        assertTrue(m.netByNetwork().isEmpty());
    }

    @Test
    void displayPriceRefuseAvantToutAppel() {
        FakeTransport t = new FakeTransport();
        TechnasPay pay = pay(t);
        assertEquals("invalid_amount", assertThrows(TechnasPayException.class,
                () -> pay.displayPrice(DisplayPriceRequest.builder().country("TG").build())).code());
        assertEquals("invalid_amount", assertThrows(TechnasPayException.class,
                () -> pay.displayPrice(DisplayPriceRequest.builder().receive(0).build())).code());
        assertEquals("invalid_amount", assertThrows(TechnasPayException.class,
                () -> pay.displayPrice(DisplayPriceRequest.builder().receive(new BigDecimal("999.5")).build())).code());
        assertTrue(t.calls.isEmpty());
    }

    @Test
    void getRatesEtRatesLisentPaysReseauxCommissionEtExemples() {
        String grille = """
                {"fees_paid_by":"customer","countries":[{"country":"TG","currency":"XOF","commission":{"percent":2,"fixed":0},
                  "networks":[{"network":"mixx","network_name":"Mixx by Yas","percent":4.1,"fixed":0,
                    "fee_tiers":[{"min_amount":200,"max_amount":100000000,"percent":4.1,"fixed":0},
                                 {"min_amount":100000001,"max_amount":null,"percent":3,"fixed":0}],
                    "minimum_amount":200,"operator_fee_estimate":10,"example_1000":{
                      "customer":{"customer_total":1064,"processing_fee":44,"commission":20,"net_amount":1000},
                      "partner":null}}]}],"note":"x"}""";
        FakeTransport t = new FakeTransport().reply(200, Json.parse(grille)).reply(200, Json.parse(grille))
                .reply(200, Json.parse("{}"));
        TechnasPay pay = pay(t);
        Rates g = pay.getRates(" tg");
        assertEquals("GET", t.calls.get(0).method());
        assertEquals("https://gw.test/v1/rates?country=TG", t.calls.get(0).url());
        assertEquals("customer", g.feesPaidBy());
        assertEquals("TG", g.countries().get(0).country());
        assertEquals("XOF", g.countries().get(0).currency());
        eq(2, g.countries().get(0).commission().percent());
        RateNetwork mixx = g.countries().get(0).networks().get(0);
        assertEquals("mixx", mixx.network());
        assertEquals("Mixx by Yas", mixx.networkName());
        assertEquals(0, new BigDecimal("4.1").compareTo(mixx.percent()));
        eq(200, mixx.minimumAmount());
        eq(10, mixx.operatorFeeEstimate());
        eq(1064, mixx.example1000().customer().customerTotal());
        eq(1000, mixx.example1000().customer().netAmount());
        assertNull(mixx.example1000().partner(), "pas payable dans ce mode");
        FeeTier palier = mixx.feeTiers().get(0);
        eq(200, palier.minAmount());
        eq(100000000, palier.maxAmount());
        assertNull(mixx.feeTiers().get(1).maxAmount());

        pay.rates();
        assertEquals("https://gw.test/v1/rates", t.calls.get(1).url());
        Rates vide = pay.getRates();
        assertEquals("partner", vide.feesPaidBy());
        assertTrue(vide.countries().isEmpty());
    }
}
