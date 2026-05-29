package net.shamansoft.cookbook.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.admin.UpdateTierResponse;
import net.shamansoft.cookbook.entitlement.UserTier;
import net.shamansoft.cookbook.exception.UserNotFoundException;
import net.shamansoft.cookbook.repository.UserProfileRepository;
import net.shamansoft.cookbook.repository.firestore.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserProfileRepository userProfileRepository;
    private final FirebaseAuth firebaseAuth;

    public UpdateTierResponse updateUserTier(String adminUserId, String targetUserId, UserTier newTier) {
        Optional<UserProfile> profileOpt;
        try {
            profileOpt = userProfileRepository.findByUserId(targetUserId).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching user profile", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to fetch user profile for userId: " + targetUserId, e);
        }

        UserProfile profile = profileOpt.orElseThrow(
                () -> new UserNotFoundException("User not found: " + targetUserId));

        UserTier oldTier = profile.tier() != null ? profile.tier() : UserTier.FREE;

        userProfileRepository.updateTier(targetUserId, newTier);

        try {
            firebaseAuth.setCustomUserClaims(targetUserId, Map.of("tier", newTier.name()));
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Failed to update Firebase custom claims for userId: " + targetUserId, e);
        }

        log.info("admin={} updated tier for user={} from={} to={}", adminUserId, targetUserId, oldTier, newTier);

        return new UpdateTierResponse(targetUserId, oldTier, newTier);
    }
}
