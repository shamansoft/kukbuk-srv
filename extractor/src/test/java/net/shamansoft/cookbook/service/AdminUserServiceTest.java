package net.shamansoft.cookbook.service;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import net.shamansoft.cookbook.dto.admin.UpdateTierResponse;
import net.shamansoft.cookbook.entitlement.UserTier;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService Tests")
class AdminUserServiceTest {

    private static final String ADMIN_USER_ID = "admin-123";
    private static final String TARGET_USER_ID = "user-456";

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private FirebaseAuth firebaseAuth;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userProfileRepository, firebaseAuth);
    }

    @Test
    @DisplayName("Should update tier in Firestore and Firebase claims on success")
    void shouldUpdateTierInFirestoreAndFirebaseClaims() throws Exception {
        UserProfile profile = UserProfile.builder()
                .userId(TARGET_USER_ID)
                .tier(UserTier.FREE)
                .build();
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getCustomClaims()).thenReturn(Map.of());

        when(userProfileRepository.findByUserId(TARGET_USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(firebaseAuth.getUser(TARGET_USER_ID)).thenReturn(userRecord);

        UpdateTierResponse response = adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO);

        assertThat(response.userId()).isEqualTo(TARGET_USER_ID);
        assertThat(response.oldTier()).isEqualTo(UserTier.FREE);
        assertThat(response.newTier()).isEqualTo(UserTier.PRO);

        verify(userProfileRepository).updateTier(TARGET_USER_ID, UserTier.PRO);
        verify(firebaseAuth).setCustomUserClaims(eq(TARGET_USER_ID), eq(Map.of("tier", "PRO")));
    }

    @Test
    @DisplayName("Should preserve existing Firebase claims when updating tier")
    void shouldPreserveExistingClaimsWhenUpdatingTier() throws Exception {
        UserProfile profile = UserProfile.builder()
                .userId(TARGET_USER_ID)
                .tier(UserTier.FREE)
                .build();
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getCustomClaims()).thenReturn(Map.of("admin", true));

        when(userProfileRepository.findByUserId(TARGET_USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(firebaseAuth.getUser(TARGET_USER_ID)).thenReturn(userRecord);

        adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO);

        verify(firebaseAuth).setCustomUserClaims(eq(TARGET_USER_ID), eq(Map.of("admin", true, "tier", "PRO")));
    }

    @Test
    @DisplayName("Should default oldTier to FREE when profile tier is null")
    void shouldDefaultOldTierToFreeWhenNull() throws Exception {
        UserProfile profile = UserProfile.builder()
                .userId(TARGET_USER_ID)
                .tier(null)
                .build();
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getCustomClaims()).thenReturn(Map.of());

        when(userProfileRepository.findByUserId(TARGET_USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(firebaseAuth.getUser(TARGET_USER_ID)).thenReturn(userRecord);

        UpdateTierResponse response = adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.ENTERPRISE);

        assertThat(response.oldTier()).isEqualTo(UserTier.FREE);
        assertThat(response.newTier()).isEqualTo(UserTier.ENTERPRISE);
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user does not exist")
    void shouldThrowUserNotFoundWhenUserDoesNotExist() {
        when(userProfileRepository.findByUserId(TARGET_USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertThatThrownBy(() -> adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(TARGET_USER_ID);
    }

    @Test
    @DisplayName("Should throw RuntimeException when Firebase claim update fails")
    void shouldThrowRuntimeExceptionWhenFirebaseClaimUpdateFails() throws Exception {
        UserProfile profile = UserProfile.builder()
                .userId(TARGET_USER_ID)
                .tier(UserTier.FREE)
                .build();
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getCustomClaims()).thenReturn(Map.of());

        when(userProfileRepository.findByUserId(TARGET_USER_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(profile)));
        when(firebaseAuth.getUser(TARGET_USER_ID)).thenReturn(userRecord);
        FirebaseAuthException firebaseEx = new FirebaseAuthException(
                ErrorCode.INTERNAL, "claim update failed", null, null, AuthErrorCode.USER_NOT_FOUND);
        doThrow(firebaseEx)
                .when(firebaseAuth).setCustomUserClaims(eq(TARGET_USER_ID), anyMap());

        assertThatThrownBy(() -> adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to update Firebase custom claims");
    }

    @Test
    @DisplayName("Should throw RuntimeException and restore interrupt flag on InterruptedException")
    void shouldRestoreInterruptOnInterruptedException() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<UserProfile>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(future);

        assertThatThrownBy(() -> adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Interrupted");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // clear for subsequent tests
    }

    @Test
    @DisplayName("Should throw RuntimeException on ExecutionException from profile fetch")
    void shouldThrowRuntimeExceptionOnExecutionException() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<UserProfile>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new ExecutionException("db error", new RuntimeException("Firestore failure")));
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(future);

        assertThatThrownBy(() -> adminUserService.updateUserTier(ADMIN_USER_ID, TARGET_USER_ID, UserTier.PRO))
                .isInstanceOf(RuntimeException.class);
    }
}
