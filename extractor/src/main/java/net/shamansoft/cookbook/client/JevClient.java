package net.shamansoft.cookbook.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asks TypeSafe's Jev model typed questions about a state ({@code POST /v1/systemone}).
 *
 * <p>Request and response are plain {@code Map}s, so the native image needs no reflection hints.
 * See https://docs.typesafe.ai/api/system-one.md for the contract.
 */
@Slf4j
@Component
public class JevClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final String model;

    public JevClient(@Qualifier("typesafeRestClient") RestClient restClient,
                     @Value("${cookbook.typesafe.model:jev-1.14.0}") String model) {
        this.restClient = restClient;
        this.model = model;
    }

    /**
     * Asks all {@code questions} about {@code state} in one request.
     *
     * @param state     a string, or a JSON-like map/list the questions refer to by backticked paths
     * @param questions question id → question ({@code type}, {@code instructions}, {@code criteria})
     * @throws JevException when the call fails or the response has no answers
     */
    public Answers ask(Object state, Map<String, ?> questions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("state", state);
        body.put("questions", questions);
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1/systemone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            throw new JevException("TypeSafe request failed: " + e.getMessage(), e);
        }
        if (response == null || !(response.get("answers") instanceof Map<?, ?> answers)) {
            throw new JevException("TypeSafe response has no answers", null);
        }
        int inputTokens = response.get("usage") instanceof Map<?, ?> usage
                && usage.get("input_tokens") instanceof Number n ? n.intValue() : 0;
        return new Answers(String.valueOf(response.get("model")), answers, inputTokens);
    }

    /** Jev's answers, keyed by question id. */
    public record Answers(String model, Map<?, ?> answers, int inputTokens) {

        /** Probability of yes for a Noul question. */
        public double noul(String id) {
            return number(answer(id).get("noul"), id);
        }

        /** Probability of each option of a Choice question. */
        public Map<String, Double> probabilities(String id) {
            if (!(answer(id).get("probabilities") instanceof Map<?, ?> probabilities)) {
                throw new JevException("Answer '" + id + "' has no probabilities", null);
            }
            Map<String, Double> result = new LinkedHashMap<>();
            probabilities.forEach((option, p) -> result.put(String.valueOf(option), number(p, id)));
            return result;
        }

        /** The most likely option of a Choice question. */
        public String choice(String id) {
            return String.valueOf(answer(id).get("choice"));
        }

        private Map<?, ?> answer(String id) {
            if (!(answers.get(id) instanceof Map<?, ?> answer)) {
                throw new JevException("No answer for question '" + id + "'", null);
            }
            return answer;
        }

        private static double number(Object value, String id) {
            if (!(value instanceof Number n)) {
                throw new JevException("Answer '" + id + "' is not a number: " + value, null);
            }
            return n.doubleValue();
        }
    }

    public static class JevException extends RuntimeException {
        public JevException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
