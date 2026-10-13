package vn.eventplatform.promotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.enums.DiscountType;
import vn.eventplatform.promotion.repository.PromotionRepository;
import vn.eventplatform.promotion.repository.PromotionUsageRepository;
import vn.eventplatform.promotion.repository.UserPromotionRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalVoucherControllerIntegrationTest {
    private static final String SECRET = "test_internal_secret_for_mock_verification";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionUsageRepository usageRepository;
    @Autowired private UserPromotionRepository userPromotionRepository;

    @BeforeEach
    void cleanDatabase() {
        usageRepository.deleteAll();
        userPromotionRepository.deleteAll();
        promotionRepository.deleteAll();
    }

    @Test
    void validatesAppliesAndReleasesVoucherThroughInternalApi() throws Exception {
        Promotion promotion = promotionRepository.save(Promotion.builder()
                .code("API" + UUID.randomUUID().toString().substring(0, 8))
                .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
                .maxDiscountAmount(new BigDecimal("15")).usedCount(0).isActive(true).isCompensation(false).build());
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/vouchers/validate").header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId, "code", promotion.getCode(), "orderAmount", 200))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.discountAmount").value(15));

        mockMvc.perform(post("/internal/v1/vouchers/apply").header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderId", orderId, "userId", userId, "code", promotion.getCode(), "orderAmount", 200))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.payableAmount").value(185));

        mockMvc.perform(post("/internal/v1/vouchers/release").header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderId", orderId, "reason", "REVOKED"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsExternalRequestWithWrongInternalSecret() throws Exception {
        mockMvc.perform(post("/internal/v1/vouchers/validate").header("X-Internal-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", UUID.randomUUID(), "code", "NOPE", "orderAmount", 10))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminCanPerformVoucherCrudAndIssueVoucherToWallet() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        String token = token(adminId, "ROLE_ADMIN");
        Map<String, Object> request = voucherRequest("ADMIN" + UUID.randomUUID().toString().substring(0, 6));

        String response = mockMvc.perform(post("/api/v1/admin/vouchers").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value(request.get("code"))).andReturn()
                .getResponse().getContentAsString();
        UUID promotionId = UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());

        mockMvc.perform(get("/api/v1/admin/vouchers").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/admin/vouchers/{id}", promotionId).header("Authorization", token))
                .andExpect(status().isOk());

        request.put("discountValue", 25);
        mockMvc.perform(put("/api/v1/admin/vouchers/{id}", promotionId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.discountValue").value(25));

        mockMvc.perform(post("/api/v1/admin/vouchers/{id}/issue", promotionId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("userId", recipientId, "source", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.promotionId").value(promotionId.toString()));
        mockMvc.perform(get("/api/v1/vouchers/wallet").header("Authorization", token(recipientId, "ROLE_ATTENDEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/v1/admin/vouchers/{id}", promotionId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void organizerCanOnlyManageOwnVoucher() throws Exception {
        UUID organizerId = UUID.randomUUID();
        String organizerToken = token(organizerId, "ROLE_ORGANIZER");
        Map<String, Object> request = voucherRequest("ORG" + UUID.randomUUID().toString().substring(0, 6));

        String response = mockMvc.perform(post("/api/v1/organizer/vouchers").header("Authorization", organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID promotionId = UUID.fromString(objectMapper.readTree(response).at("/data/id").asText());
        assertThat(promotionRepository.findById(promotionId).orElseThrow().getOrganizerId()).isEqualTo(organizerId);

        mockMvc.perform(get("/api/v1/organizer/vouchers").header("Authorization", organizerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(put("/api/v1/organizer/vouchers/{id}", promotionId).header("Authorization", organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/organizer/vouchers/{id}", promotionId).header("Authorization", organizerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizer/vouchers/{id}", promotionId)
                        .header("Authorization", token(UUID.randomUUID(), "ROLE_ORGANIZER")))
                .andExpect(status().isForbidden());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String token(UUID userId, String role) {
        return "Bearer " + Jwts.builder().subject(userId.toString()).claim("roles", List.of(role))
                .issuedAt(java.util.Date.from(Instant.now())).expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("test_dummy_jwt_secret_key_for_unit_tests_must_be_256_bits_long_abcdef123456".getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Map<String, Object> voucherRequest(String code) {
        return new java.util.HashMap<>(Map.of("code", code, "discountType", "FIXED_AMOUNT", "discountValue", 20,
                "totalUsageLimit", 10, "maxUsagePerUser", 2, "isActive", true));
    }
}
