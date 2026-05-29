package net.shamansoft.cookbook.dto.admin;

import net.shamansoft.cookbook.entitlement.UserTier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateTierRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_validTierFree_returnsFreeRequest() throws Exception {
        String json = "{\"tier\": \"FREE\"}";
        UpdateTierRequest request = objectMapper.readValue(json, UpdateTierRequest.class);
        assertThat(request.tier()).isEqualTo(UserTier.FREE);
    }

    @Test
    void deserialize_validTierPro_returnsProRequest() throws Exception {
        String json = "{\"tier\": \"PRO\"}";
        UpdateTierRequest request = objectMapper.readValue(json, UpdateTierRequest.class);
        assertThat(request.tier()).isEqualTo(UserTier.PRO);
    }

    @Test
    void deserialize_validTierEnterprise_returnsEnterpriseRequest() throws Exception {
        String json = "{\"tier\": \"ENTERPRISE\"}";
        UpdateTierRequest request = objectMapper.readValue(json, UpdateTierRequest.class);
        assertThat(request.tier()).isEqualTo(UserTier.ENTERPRISE);
    }

    @Test
    void deserialize_invalidTier_throwsException() {
        String json = "{\"tier\": \"INVALID_TIER\"}";
        assertThatThrownBy(() -> objectMapper.readValue(json, UpdateTierRequest.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void deserialize_nullTier_returnRequestWithNullTier() throws Exception {
        String json = "{\"tier\": null}";
        UpdateTierRequest request = objectMapper.readValue(json, UpdateTierRequest.class);
        assertThat(request.tier()).isNull();
    }

    @Test
    void updateTierResponse_hasCorrectFields() {
        UpdateTierResponse response = new UpdateTierResponse("user-123", UserTier.FREE, UserTier.PRO);
        assertThat(response.userId()).isEqualTo("user-123");
        assertThat(response.oldTier()).isEqualTo(UserTier.FREE);
        assertThat(response.newTier()).isEqualTo(UserTier.PRO);
    }
}
