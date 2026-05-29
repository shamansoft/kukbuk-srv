package net.shamansoft.cookbook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.shamansoft.cookbook.dto.admin.UpdateTierRequest;
import net.shamansoft.cookbook.dto.admin.UpdateTierResponse;
import net.shamansoft.cookbook.service.AdminUserService;
import net.shamansoft.cookbook.security.RequireAdmin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminUserService adminUserService;

    @PutMapping("/users/{userId}/tier")
    @RequireAdmin
    public ResponseEntity<UpdateTierResponse> updateUserTier(
            @PathVariable String userId,
            @RequestBody @Valid UpdateTierRequest request,
            @RequestAttribute("userId") String adminUserId) {

        log.info("Admin {} updating tier for user {}", adminUserId, userId);
        UpdateTierResponse response = adminUserService.updateUserTier(adminUserId, userId, request.tier());
        return ResponseEntity.ok(response);
    }
}
