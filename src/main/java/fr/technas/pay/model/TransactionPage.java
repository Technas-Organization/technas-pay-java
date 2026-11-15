package fr.technas.pay.model;

import java.util.List;

/** Une page de l'historique ; {@code nextCursor} est {@code null} à la dernière. */
public record TransactionPage(List<Payment> items, String nextCursor) {
}
