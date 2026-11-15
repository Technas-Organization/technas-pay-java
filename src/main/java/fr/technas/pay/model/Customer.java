package fr.technas.pay.model;

/** Le client qui paie : de VRAIS champs, jamais inventés. {@code phone} et {@code country} sont facultatifs. */
public record Customer(String email, String name, String phone, String country) {
    public static Customer of(String email, String name) {
        return new Customer(email, name, null, null);
    }
}
