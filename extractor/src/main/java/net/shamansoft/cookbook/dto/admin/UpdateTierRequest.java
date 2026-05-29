package net.shamansoft.cookbook.dto.admin;

import jakarta.validation.constraints.NotNull;
import net.shamansoft.cookbook.entitlement.UserTier;

public record UpdateTierRequest(@NotNull(message = "Tier is required") UserTier tier) {
}
