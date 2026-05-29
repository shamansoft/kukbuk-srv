package net.shamansoft.cookbook.dto.admin;

import net.shamansoft.cookbook.entitlement.UserTier;

public record UpdateTierResponse(String userId, UserTier oldTier, UserTier newTier) {
}
