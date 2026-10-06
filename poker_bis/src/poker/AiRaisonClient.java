package poker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client HTTP pour l'API ai-raison.com.
 *
 * Pour activer : dans DecisionEngine.decide(), remplacer l'appel a
 * applyPolicy() par AiRaisonClient.query(elements).
 *
 * Format d'appel attendu (a adapter selon la doc raison.com) :
 *   POST /api/decide
 *   Body : {"elements": ["low_opponent_card", "strong_opponent_bet", "big_pot"]}
 *   Response : {"decision": "call"}
 */
public final class AiRaisonClient {

    private static final String  API_URL = "https://www.ai-raison.com/api/decide";
    private static final int     TIMEOUT_SECONDS = 5;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    private AiRaisonClient() {}

    /**
     * Interroge ai-raison avec une liste d'elements de contexte.
     *
     * @param elements ex: "low_opponent_card,strong_opponent_bet,big_pot"
     * @return "fold", "call" ou "bet" — retourne "call" en cas d'erreur
     */
    public static String query(String elements) {
        try {
            String jsonBody = buildRequestBody(elements);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    // .header("Authorization", "Bearer VOTRE_CLE_API")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseDecision(response.body());
            }

            System.err.println("[AiRaison] HTTP " + response.statusCode());

        } catch (Exception e) {
            System.err.println("[AiRaison] Erreur : " + e.getMessage());
        }

        return "call"; // fallback securitaire
    }

    private static String buildRequestBody(String elements) {
        StringBuilder sb = new StringBuilder("{\"elements\":[");
        boolean first = true;
        for (String el : elements.split(",")) {
            String trimmed = el.trim();
            if (trimmed.isEmpty()) continue;
            if (!first) sb.append(",");
            sb.append("\"").append(trimmed).append("\"");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String parseDecision(String responseBody) {
        // Parsing simple — adapter au format reel de la reponse
        String lower = responseBody.toLowerCase();
        if (lower.contains("\"fold\"") || lower.contains("fold")) return "fold";
        if (lower.contains("\"bet\"")  || lower.contains("bet"))  return "bet";
        if (lower.contains("\"call\"") || lower.contains("call")) return "call";
        return "call";
    }
}
