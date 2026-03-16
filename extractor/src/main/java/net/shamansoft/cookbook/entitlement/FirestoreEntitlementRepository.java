package net.shamansoft.cookbook.entitlement;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Repository
@Primary
@RequiredArgsConstructor
@Slf4j
public class FirestoreEntitlementRepository implements EntitlementRepository {

    private static final String QUOTA_COLLECTION = "quota_windows";
    private static final String USERS_COLLECTION = "users";
    private static final DateTimeFormatter WINDOW_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private final Firestore firestore;
    private final EntitlementPlanConfig planConfig;

    /**
     * Atomically checks and increments the quota counter for the given user/operation/window.
     *
     * Per RFC §2.3: TimeoutException is NOT caught here — it propagates to the service
     * which maps it to CIRCUIT_OPEN. This is intentional (no .exceptionally).
     */
    @Override
    public CompletableFuture<QuotaWindow> checkAndIncrement(
            String userId, Operation operation, Instant windowStart, int limit) {
        String windowKey = WINDOW_KEY_FORMAT.format(windowStart);
        String docId = userId + "::" + operation.name() + "::" + windowKey;
        Instant resetAt = windowStart.plus(Duration.ofDays(1));
        Instant expireAt = resetAt.plus(Duration.ofDays(2));
        DocumentReference docRef = firestore.collection(QUOTA_COLLECTION).document(docId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return firestore.runTransaction(tx -> {
                    DocumentSnapshot snapshot = tx.get(docRef).get();
                    int currentCount = 0;
                    if (snapshot.exists()) {
                        Long stored = snapshot.getLong("count");
                        currentCount = stored != null ? stored.intValue() : 0;
                    }
                    boolean withinLimit = limit < 0 || currentCount < limit;
                    int newCount = withinLimit ? currentCount + 1 : currentCount;

                    if (withinLimit) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("userId", userId);
                        data.put("operation", operation.name());
                        data.put("windowKey", windowKey);
                        data.put("count", newCount);
                        data.put("limit", limit);
                        data.put("resetAt", Timestamp.ofTimeSecondsAndNanos(
                                resetAt.getEpochSecond(), resetAt.getNano()));
                        data.put("expireAt", Timestamp.ofTimeSecondsAndNanos(
                                expireAt.getEpochSecond(), expireAt.getNano()));
                        tx.set(docRef, data);
                    }

                    return new QuotaWindow(userId, operation, windowKey, newCount, limit, resetAt, withinLimit);
                }).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("checkAndIncrement interrupted for userId=" + userId, e);
            } catch (ExecutionException e) {
                log.error("checkAndIncrement Firestore error for userId={}, operation={}: {}",
                        userId, operation, e.getMessage());
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException("checkAndIncrement failed for userId=" + userId, cause);
            }
        }, EXECUTOR).orTimeout(planConfig.timeouts().incrementMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Atomically deducts one credit from users/{userId}.
     *
     * Per RFC §2.3: timeout maps to false (fail-safe, not CIRCUIT_OPEN).
     * .exceptionally handles TimeoutException from orTimeout.
     */
    @Override
    public CompletableFuture<Boolean> deductCredit(String userId) {
        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return firestore.runTransaction(tx -> {
                    DocumentSnapshot snapshot = tx.get(docRef).get();
                    if (!snapshot.exists()) {
                        return false;
                    }
                    Long credits = snapshot.getLong("credits");
                    if (credits == null || credits <= 0) {
                        return false;
                    }
                    tx.update(docRef, "credits", FieldValue.increment(-1));
                    return true;
                }).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("deductCredit interrupted for userId={}", userId);
                return false;
            } catch (ExecutionException e) {
                log.warn("deductCredit Firestore error for userId={}: {}", userId, e.getMessage());
                return false;
            }
        }, EXECUTOR).orTimeout(planConfig.timeouts().incrementMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("deductCredit timeout or error for userId={}: {}", userId, ex.getMessage());
                    return false;
                });
    }

    /**
     * Updates tier and credits on the users/{userId} document.
     */
    @Override
    public CompletableFuture<Void> updateTierAndCredits(String userId, UserTier tier, int credits) {
        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("tier", tier.name());
        updates.put("credits", credits);

        return CompletableFuture.runAsync(() -> {
            try {
                docRef.update(updates).get();
                log.debug("Updated tier and credits for userId={}: tier={}, credits={}", userId, tier, credits);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("updateTierAndCredits interrupted for userId=" + userId, e);
            } catch (ExecutionException e) {
                log.error("updateTierAndCredits Firestore error for userId={}: {}", userId, e.getMessage(), e);
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException("updateTierAndCredits failed for userId=" + userId, cause);
            }
        }, EXECUTOR);
    }
}
