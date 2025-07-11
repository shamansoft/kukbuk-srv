package net.shamansoft.cookbook.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.model.Recipe;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class Transformer {

    public Recipe documentToRecipeCache(DocumentSnapshot document) {
        return Recipe.builder()
                .contentHash(document.getId())
                .sourceUrl(document.getString("sourceUrl"))
                .recipeYaml(document.getString("recipeYaml"))
                .createdAt(toInstant(document.get("createdAt")))
                .lastAccessedAt(toInstant(document.get("lastAccessedAt")))
                .accessCount(Optional.ofNullable(document.getLong("accessCount")).orElse(0L))
                .build();
    }

    public Instant toInstant(Object timestamp) {
        switch (timestamp) {
            case null -> {
                return null;
            }
            case com.google.cloud.Timestamp timestamp1 -> {
                return timestamp1.toSqlTimestamp().toInstant();
            }
            case java.sql.Timestamp timestamp1 -> {
                return timestamp1.toInstant();
            }
            case Instant instant -> {
                return instant;
            }
            case Long l -> {
                return Instant.ofEpochMilli(l);
            }
            case String s -> {
                return Instant.parse(s);
            }
            case Map map1 -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) timestamp;
                if (map.containsKey("epochSecond") && map.containsKey("nano")) {
                    long epochSecond = ((Number) map.get("epochSecond")).longValue();
                    int nano = ((Number) map.get("nano")).intValue();
                    return Instant.ofEpochSecond(epochSecond, nano);
                }
            }
            default -> {
            }
        }
        log.warn("Unknown timestamp type: {}, value: {}", timestamp.getClass().getName(), timestamp);
        return null;
    }
}
