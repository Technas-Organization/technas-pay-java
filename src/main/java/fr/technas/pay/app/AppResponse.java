package fr.technas.pay.app;

import fr.technas.pay.internal.Json;

/** Statut HTTP et corps à renvoyer en JSON. */
public record AppResponse(int status, Object body) {
    public String json() {
        return Json.writeForHttp(body);
    }
}
