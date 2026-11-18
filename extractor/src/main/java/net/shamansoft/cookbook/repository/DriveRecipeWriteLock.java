package net.shamansoft.cookbook.repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.exception.RecipeWriteUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Serializes SAR's Drive read/modify/write operations, including across Cloud Run instances. */
@Slf4j
@Repository
public class DriveRecipeWriteLock {

    static final String COLLECTION = "drive_recipe_write_locks";
    private static final int READ_LEASE_SECONDS = 30;
    private static final int ACQUIRE_ATTEMPTS = 4;
    private final ObjectProvider<Firestore> firestoreProvider;

    public DriveRecipeWriteLock(ObjectProvider<Firestore> firestoreProvider) {
        this.firestoreProvider = firestoreProvider;
    }

    public Lock forFile(String fileId) {
        return acquire("file:" + fileId);
    }

    public Lock forName(String folderId, String fileName) {
        return acquire("name:" + folderId + ":" + fileName);
    }

    private Lock acquire(String key) {
        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore == null) {
            throw new RecipeWriteUnavailableException("Drive write coordination is unavailable");
        }
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        DocumentReference ref = firestore.collection(COLLECTION).document(id);
        String owner = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= ACQUIRE_ATTEMPTS; attempt++) {
            boolean acquired = await(firestore.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref).get();
                if (current.exists() && !readLeaseExpired(current)) {
                    return false;
                }
                Timestamp now = current.getReadTime();
                tx.set(ref, Map.of("owner", owner, "readUntil", Timestamp.ofTimeSecondsAndNanos(
                        now.getSeconds() + READ_LEASE_SECONDS, now.getNanos())));
                return true;
            }));
            if (acquired) {
                return new Lock(firestore, ref, owner);
            }
            if (attempt < ACQUIRE_ATTEMPTS) {
                try {
                    Thread.sleep(40L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RecipeWriteUnavailableException("Interrupted while waiting for a Drive write", e);
                }
            }
        }
        throw new RecipeWriteUnavailableException("Another Drive write is pending: " + id);
    }

    private static boolean readLeaseExpired(DocumentSnapshot snapshot) {
        Timestamp until = snapshot.getTimestamp("readUntil");
        return until != null && until.compareTo(snapshot.getReadTime()) <= 0;
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecipeWriteUnavailableException("Interrupted while coordinating a Drive write", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RecipeWriteUnavailableException("Drive write coordination is unavailable", e);
        }
    }

    public static final class Lock implements AutoCloseable {
        private final Firestore firestore;
        private final DocumentReference ref;
        private final String owner;
        private boolean writeStarted;
        private boolean release = true;

        private Lock(Firestore firestore, DocumentReference ref, String owner) {
            this.firestore = firestore;
            this.ref = ref;
            this.owner = owner;
        }

        public <T> T write(Supplier<T> upload) {
            boolean promoted = await(firestore.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref).get();
                if (!owner.equals(current.getString("owner")) || current.getTimestamp("readUntil") == null
                        || readLeaseExpired(current)) {
                    return false;
                }
                // Never expire a write permit. Drive can finish a request after its caller times out.
                tx.set(ref, Map.of("owner", owner, "writingSince", current.getReadTime()));
                return true;
            }));
            if (!promoted) {
                throw new RecipeWriteUnavailableException("Drive write permit expired: " + ref.getId());
            }

            writeStarted = true;
            release = false;
            try {
                log.info("Starting Drive upload under lock {} (owner {})", ref.getId(), owner);
                T result = upload.get();
                release = true;
                return result;
            } catch (RuntimeException e) {
                release = wasRejected(e);
                if (!release) {
                    log.error("Drive upload outcome unknown. Retaining write lock {} (owner {})", ref.getId(), owner);
                    throw new RecipeWriteUnavailableException("Drive upload outcome is unknown for lock " + ref.getId(), e);
                }
                throw e;
            }
        }

        private static boolean wasRejected(Throwable e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof RestClientResponseException response) {
                    return response.getStatusCode().is4xxClientError() && response.getStatusCode().value() != 408;
                }
            }
            return false;
        }

        @Override
        public void close() {
            if (release) {
                try {
                    await(firestore.runTransaction(tx -> {
                        if (owner.equals(tx.get(ref).get().getString("owner"))) {
                            tx.delete(ref);
                        }
                        return null;
                    }));
                } catch (RecipeWriteUnavailableException e) {
                    log.error("Could not release Drive lock {} (owner {}, upload attempt settled: {})",
                            ref.getId(), owner, writeStarted, e);
                }
            }
        }
    }
}
