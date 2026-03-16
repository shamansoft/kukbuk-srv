package net.shamansoft.cookbook.entitlement;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import com.google.cloud.firestore.WriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirestoreEntitlementRepositoryTest {

    @Mock Firestore firestore;
    @Mock CollectionReference quotaCollection;
    @Mock CollectionReference usersCollection;
    @Mock DocumentReference quotaDocRef;
    @Mock DocumentReference userDocRef;
    @Mock DocumentSnapshot quotaSnapshot;
    @Mock DocumentSnapshot userSnapshot;
    @Mock Transaction transaction;
    @Mock WriteResult writeResult;
    @Mock EntitlementPlanConfig planConfig;
    @Mock EntitlementPlanConfig.Timeouts timeouts;

    private FirestoreEntitlementRepository repository;

    private static final String USER_ID = "user123";
    private static final Instant WINDOW_START = Instant.parse("2026-03-08T00:00:00Z");
    private static final int LIMIT = 5;
    private static final String EXPECTED_DOC_ID = "user123::RECIPE_EXTRACTION::20260308";

    @BeforeEach
    void setUp() {
        lenient().when(planConfig.timeouts()).thenReturn(timeouts);
        lenient().when(timeouts.incrementMs()).thenReturn(1000);
        lenient().when(timeouts.checkMs()).thenReturn(500);
        repository = new FirestoreEntitlementRepository(firestore, planConfig);
    }

    // ---- checkAndIncrement ----

    @Test
    void checkAndIncrement_firstRequest_withinQuota() throws Exception {
        when(firestore.collection("quota_windows")).thenReturn(quotaCollection);
        when(quotaCollection.document(EXPECTED_DOC_ID)).thenReturn(quotaDocRef);
        when(transaction.get(quotaDocRef)).thenReturn(ApiFutures.immediateFuture(quotaSnapshot));
        when(quotaSnapshot.exists()).thenReturn(false);
        setupTransactionMock();

        var window = repository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, LIMIT)
                .get(2, TimeUnit.SECONDS);

        assertThat(window.userId()).isEqualTo(USER_ID);
        assertThat(window.operation()).isEqualTo(Operation.RECIPE_EXTRACTION);
        assertThat(window.windowKey()).isEqualTo("20260308");
        assertThat(window.count()).isEqualTo(1);
        assertThat(window.limit()).isEqualTo(LIMIT);
        assertThat(window.withinLimit()).isTrue();
        assertThat(window.resetAt()).isEqualTo(Instant.parse("2026-03-09T00:00:00Z"));
    }

    @Test
    void checkAndIncrement_existingCountBelowLimit_incrementsAndAllows() throws Exception {
        when(firestore.collection("quota_windows")).thenReturn(quotaCollection);
        when(quotaCollection.document(EXPECTED_DOC_ID)).thenReturn(quotaDocRef);
        when(transaction.get(quotaDocRef)).thenReturn(ApiFutures.immediateFuture(quotaSnapshot));
        when(quotaSnapshot.exists()).thenReturn(true);
        when(quotaSnapshot.getLong("count")).thenReturn(3L);
        setupTransactionMock();

        var window = repository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, LIMIT)
                .get(2, TimeUnit.SECONDS);

        assertThat(window.count()).isEqualTo(4);
        assertThat(window.withinLimit()).isTrue();
    }

    @Test
    void checkAndIncrement_atLimit_deniedAndCountUnchanged() throws Exception {
        when(firestore.collection("quota_windows")).thenReturn(quotaCollection);
        when(quotaCollection.document(EXPECTED_DOC_ID)).thenReturn(quotaDocRef);
        when(transaction.get(quotaDocRef)).thenReturn(ApiFutures.immediateFuture(quotaSnapshot));
        when(quotaSnapshot.exists()).thenReturn(true);
        when(quotaSnapshot.getLong("count")).thenReturn(5L);
        setupTransactionMock();

        var window = repository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, LIMIT)
                .get(2, TimeUnit.SECONDS);

        assertThat(window.count()).isEqualTo(5);
        assertThat(window.withinLimit()).isFalse();
    }

    @Test
    void checkAndIncrement_unlimitedTier_alwaysAllowed() throws Exception {
        when(firestore.collection("quota_windows")).thenReturn(quotaCollection);
        when(quotaCollection.document(anyString())).thenReturn(quotaDocRef);
        when(transaction.get(quotaDocRef)).thenReturn(ApiFutures.immediateFuture(quotaSnapshot));
        when(quotaSnapshot.exists()).thenReturn(true);
        when(quotaSnapshot.getLong("count")).thenReturn(1000L);
        setupTransactionMock();

        var window = repository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, -1)
                .get(2, TimeUnit.SECONDS);

        assertThat(window.withinLimit()).isTrue();
        assertThat(window.limit()).isEqualTo(-1);
    }

    @Test
    void checkAndIncrement_firestoreThrows_exceptionPropagates() {
        when(firestore.collection("quota_windows")).thenReturn(quotaCollection);
        when(quotaCollection.document(EXPECTED_DOC_ID)).thenReturn(quotaDocRef);
        when(firestore.runTransaction(any()))
                .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("Firestore unavailable")));

        var future = repository.checkAndIncrement(USER_ID, Operation.RECIPE_EXTRACTION, WINDOW_START, LIMIT);

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    // ---- deductCredit ----

    @Test
    void deductCredit_creditAvailable_returnsTrue() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(transaction.get(userDocRef)).thenReturn(ApiFutures.immediateFuture(userSnapshot));
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getLong("credits")).thenReturn(3L);
        setupUserTransactionMock();

        Boolean result = repository.deductCredit(USER_ID).get(2, TimeUnit.SECONDS);

        assertThat(result).isTrue();
    }

    @Test
    void deductCredit_noCredits_returnsFalse() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(transaction.get(userDocRef)).thenReturn(ApiFutures.immediateFuture(userSnapshot));
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getLong("credits")).thenReturn(0L);
        setupUserTransactionMock();

        Boolean result = repository.deductCredit(USER_ID).get(2, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    @Test
    void deductCredit_nullCredits_returnsFalse() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(transaction.get(userDocRef)).thenReturn(ApiFutures.immediateFuture(userSnapshot));
        when(userSnapshot.exists()).thenReturn(true);
        when(userSnapshot.getLong("credits")).thenReturn(null);
        setupUserTransactionMock();

        Boolean result = repository.deductCredit(USER_ID).get(2, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    @Test
    void deductCredit_userNotFound_returnsFalse() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(transaction.get(userDocRef)).thenReturn(ApiFutures.immediateFuture(userSnapshot));
        when(userSnapshot.exists()).thenReturn(false);
        setupUserTransactionMock();

        Boolean result = repository.deductCredit(USER_ID).get(2, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    @Test
    void deductCredit_firestoreThrows_returnsFalse() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(firestore.runTransaction(any()))
                .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("timeout simulation")));

        Boolean result = repository.deductCredit(USER_ID).get(2, TimeUnit.SECONDS);

        assertThat(result).isFalse();
    }

    // ---- updateTierAndCredits ----

    @SuppressWarnings("unchecked")
    @Test
    void updateTierAndCredits_writesCorrectFields() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(userDocRef.update(any(Map.class))).thenReturn(ApiFutures.immediateFuture(writeResult));

        repository.updateTierAndCredits(USER_ID, UserTier.PRO, 10).get(2, TimeUnit.SECONDS);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(userDocRef).update(captor.capture());
        assertThat(captor.getValue()).containsEntry("tier", "PRO");
        assertThat(captor.getValue()).containsEntry("credits", 10);
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateTierAndCredits_freeZeroCredits_writesFields() throws Exception {
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.document(USER_ID)).thenReturn(userDocRef);
        when(userDocRef.update(any(Map.class))).thenReturn(ApiFutures.immediateFuture(writeResult));

        repository.updateTierAndCredits(USER_ID, UserTier.FREE, 0).get(2, TimeUnit.SECONDS);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(userDocRef).update(captor.capture());
        assertThat(captor.getValue()).containsEntry("tier", "FREE");
        assertThat(captor.getValue()).containsEntry("credits", 0);
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private void setupTransactionMock() {
        doAnswer(inv -> {
            Transaction.Function fn = inv.getArgument(0);
            try {
                return ApiFutures.immediateFuture(fn.updateCallback(transaction));
            } catch (Exception e) {
                return ApiFutures.immediateFailedFuture(e);
            }
        }).when(firestore).runTransaction(any());
    }

    @SuppressWarnings("unchecked")
    private void setupUserTransactionMock() {
        doAnswer(inv -> {
            Transaction.Function fn = inv.getArgument(0);
            try {
                return ApiFutures.immediateFuture(fn.updateCallback(transaction));
            } catch (Exception e) {
                return ApiFutures.immediateFailedFuture(e);
            }
        }).when(firestore).runTransaction(any());
    }
}
