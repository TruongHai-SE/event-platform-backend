package vn.eventplatform.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.event.domain.enums.CategoryStatus;
import vn.eventplatform.event.domain.enums.RefundPolicy;
import vn.eventplatform.event.domain.enums.SeatingMode;
import vn.eventplatform.event.dto.CategoryDto;
import vn.eventplatform.event.dto.EventDto;
import vn.eventplatform.event.dto.InternalInventoryDto;
import vn.eventplatform.event.repository.CategoryRepository;
import vn.eventplatform.event.repository.CategoryRequestRepository;
import vn.eventplatform.event.repository.EventRepository;
import vn.eventplatform.event.repository.OutboxEventRepository;
import vn.eventplatform.event.repository.ShowTimeTicketInventoryRepository;
import vn.eventplatform.event.service.EventMediaService;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryRequestRepository categoryRequestRepository;

    @Autowired
    private ShowTimeTicketInventoryRepository inventoryRepository;

    @Autowired
    private vn.eventplatform.event.repository.SeatAvailabilityRepository seatAvailabilityRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EventMediaService eventMediaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${security.token.secret-key}")
    private String jwtSecret;

    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID organizerA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID organizerB = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID normalUser = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void cleanDatabase() {
        seatAvailabilityRepository.deleteAll();
        inventoryRepository.deleteAll();
        outboxEventRepository.deleteAll();
        eventRepository.deleteAll();
        categoryRequestRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("1. Auth & Privilege Escalation Sabotage: Unauthenticated, Forged JWT, Wrong Role (USER vs ORGANIZER vs ADMIN)")
    void test01_AuthAndPrivilegeEscalationSabotage() throws Exception {
        // 1. Unauthenticated request to protected endpoint -> 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));

        // 2. Forged/Tampered Bearer JWT -> 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.forged.signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 3. Valid Bearer JWT with ROLE_USER trying to create Event (requires ORGANIZER/ADMIN) -> 403 FORBIDDEN
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String userJwt = Jwts.builder()
                .subject(normalUser.toString())
                .claim("roles", List.of("ROLE_USER"))
                .signWith(key)
                .compact();

        UUID catId = createPublishedCategory("Music");
        EventDto.CreateEventRequest validReq = EventDto.CreateEventRequest.builder()
                .title("Test Event")
                .categoryId(catId)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isForbidden());

        // 4. ROLE_ORGANIZER trying to call Admin-only category creation -> 403 FORBIDDEN
        mockMvc.perform(post("/api/v1/categories")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Only Cat\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    @DisplayName("2. Tasks 1-3: Category CRUD, Status Toggle & Sabotage (Zero-Width, NFKC Duplicate, Delete Category in Use)")
    void test02_CategoryCrudAndUnicodeSabotage() throws Exception {
        // 1. Sabotage: Zero-width only category name -> 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/categories")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\\u200B   \\uFEFF\"}"))
                .andExpect(status().isBadRequest());

        // 2. Admin creates category "Âm Nhạc"
        UUID catId = createPublishedCategory("  Âm   Nhạc  ");

        // 3. Sabotage: Duplicate category using zero-width character + lowercase -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/categories")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"âm\\u200B nhạc\"}"))
                .andExpect(status().isConflict());

        // 4. Admin updates category name & description -> 200 OK
        mockMvc.perform(put("/api/v1/categories/" + catId)
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Âm Nhạc & Lễ Hội\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("am-nhac-le-hoi"));

        // 5. Public GET by slug works
        mockMvc.perform(get("/api/v1/categories/am-nhac-le-hoi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(catId.toString()));

        // 6. Create an event using this category, then attempt to DELETE category -> 409 CONFLICT
        createDraftEventForOrganizer(organizerA, catId, "Concert 1", SeatingMode.GENERAL_ADMISSION);
        mockMvc.perform(delete("/api/v1/categories/" + catId)
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isConflict());

        // 7. Create unused category and DELETE it -> 200 OK
        UUID unusedCatId = createPublishedCategory("Temporary Category");
        mockMvc.perform(delete("/api/v1/categories/" + unusedCatId)
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @DisplayName("3. Tasks 4-7: Category Proposal Approval, Rejection & Sabotage (Duplicate Existing/Pending, Double Review)")
    void test03_CategoryProposalLifecycleAndSabotage() throws Exception {
        createPublishedCategory("Thể Thao");

        // 1. Sabotage: Organizer proposes existing category -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/categories/requests")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedName\":\"  THỂ\\u200B THAO  \"}"))
                .andExpect(status().isConflict());

        // 2. Organizer A proposes "Nghệ Thuật Đương Đại" -> 201 CREATED
        MvcResult prop1Res = mockMvc.perform(post("/api/v1/categories/requests")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedName\":\"Nghệ Thuật Đương Đại\",\"description\":\"Art shows\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        UUID prop1Id = UUID.fromString(
                objectMapper.readTree(prop1Res.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        // 3. Sabotage: Organizer B proposes same pending category -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/categories/requests")
                        .header("X-User-Id", organizerB)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedName\":\"nghệ thuật đương đại\"}"))
                .andExpect(status().isConflict());

        // 4. Admin approves prop1 -> 200 OK + auto-creates category
        mockMvc.perform(post("/api/v1/categories/requests/" + prop1Id + "/approve")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.createdCategoryId").isNotEmpty());

        // 5. Sabotage: Admin tries to approve or reject an already APPROVED proposal -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/categories/requests/" + prop1Id + "/approve")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isConflict());

        // 6. Organizer proposes another category -> Admin rejects it with reason -> 200 OK
        MvcResult prop2Res = mockMvc.perform(post("/api/v1/categories/requests")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedName\":\"Danh Mục Không Phù Hợp\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID prop2Id = UUID.fromString(
                objectMapper.readTree(prop2Res.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        mockMvc.perform(post("/api/v1/categories/requests/" + prop2Id + "/reject")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"Không nằm trong định hướng sự kiện\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Không nằm trong định hướng sự kiện"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Tasks 8-13: Event Draft CRUD, Ownership Isolation, RefundPolicy Validation & Draft Deletion")
    void test04_EventCreationOwnershipAndRefundPolicySabotage() throws Exception {
        UUID activeCatId = createPublishedCategory("Hội Nghị");

        // Archive a category and try to create event with it -> 400 BAD_REQUEST
        UUID archivedCatId = createPublishedCategory("Archived Cat");
        mockMvc.perform(patch("/api/v1/categories/" + archivedCatId + "/status")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk());

        EventDto.CreateEventRequest archivedCatEvent = EventDto.CreateEventRequest.builder()
                .title("Event on Archived Cat")
                .categoryId(archivedCatId)
                .build();
        mockMvc.perform(post("/api/v1/events")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(archivedCatEvent)))
                .andExpect(status().isBadRequest());

        // Sabotage: ALLOW_REFUND with refundDeadlineHours = 0 -> 400 BAD_REQUEST
        EventDto.CreateEventRequest badRefundReq = EventDto.CreateEventRequest.builder()
                .title("Bad Refund Event")
                .categoryId(activeCatId)
                .refundPolicy(RefundPolicy.ALLOW_REFUND)
                .refundDeadlineHours(0)
                .build();
        mockMvc.perform(post("/api/v1/events")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRefundReq)))
                .andExpect(status().isBadRequest());

        // Valid Draft Event creation by Organizer A
        UUID eventId = createDraftEventForOrganizer(organizerA, activeCatId, "Tech Summit 2026", SeatingMode.GENERAL_ADMISSION);

        // Update RefundPolicy via PATCH /refund-policy -> 200 OK
        mockMvc.perform(patch("/api/v1/events/" + eventId + "/refund-policy")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundPolicy\":\"ALLOW_REFUND\",\"refundDeadlineHours\":24,\"refundFeePercent\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundPolicy").value("ALLOW_REFUND"))
                .andExpect(jsonPath("$.data.refundDeadlineHours").value(24));

        // Sabotage: Organizer B tries to view managed detail, update, or delete Organizer A's event -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .header("X-User-Id", organizerB)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/events/" + eventId)
                        .header("X-User-Id", organizerB)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isForbidden());

        // Organizer A deletes their own DRAFT event -> 200 OK
        mockMvc.perform(delete("/api/v1/events/" + eventId)
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events/" + eventId)
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("5. Task 14 & Guardrail 2.2: Media Folder Isolation, Magic Bytes Spoofing, SSRF Blocking & Root Folder Guard")
    void test05_MediaUploadFolderIsolationMagicBytesAndSsrfSabotage() throws Exception {
        UUID catId = createPublishedCategory("Media Test Cat");
        UUID eventId = createDraftEventForOrganizer(organizerA, catId, "Media Event", SeatingMode.GENERAL_ADMISSION);

        // 1. Valid PNG Magic Bytes -> stored in isolated folder
        byte[] validPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        MockMultipartFile bannerPng = new MockMultipartFile("file", "banner.png", "image/png", validPng);
        mockMvc.perform(multipart("/api/v1/events/" + eventId + "/media/banner")
                        .file(bannerPng)
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bannerUrl").value(org.hamcrest.Matchers.containsString(
                        "/uploads/mss301-event-platform/events/" + eventId + "/banners/"
                )));

        // 2. Valid JPEG Magic Bytes for thumbnail
        byte[] validJpg = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        };
        MockMultipartFile thumbJpg = new MockMultipartFile("file", "thumb.jpg", "image/jpeg", validJpg);
        mockMvc.perform(multipart("/api/v1/events/" + eventId + "/media/thumbnail")
                        .file(thumbJpg)
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "/uploads/mss301-event-platform/events/" + eventId + "/thumbnails/"
                )));

        // 3. Sabotage: Spoofed script file renamed to .png -> 400 BAD_REQUEST
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "hack.png", "image/png", "<script>fetch('http://evil.com')</script>".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/events/" + eventId + "/media/banner")
                        .file(spoofedFile)
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isBadRequest());

        // 4. Sabotage: SSRF attempts (HTTP scheme, localhost, 127.0.0.1, AWS metadata 169.254.169.254, RFC1918 192.168.1.1)
        for (String maliciousUrl : List.of(
                "http://res.cloudinary.com/demo/image/upload/sample.jpg",
                "https://localhost:8080/internal",
                "https://127.0.0.1/admin",
                "https://169.254.169.254/latest/meta-data/",
                "https://192.168.1.1/router.png",
                "https://10.0.0.1/internal.png"
        )) {
            mockMvc.perform(put("/api/v1/events/" + eventId + "/media/urls")
                            .header("X-User-Id", organizerA)
                            .header("X-Roles", "ROLE_ORGANIZER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bannerUrl\":\"" + maliciousUrl + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        // 5. Sabotage: Deleting asset outside project root folder -> blocked with AppException(FORBIDDEN)
        assertThatThrownBy(() -> eventMediaService.deleteMediaAssetSafely("another-project/events/banner_1"))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> eventMediaService.deleteMediaAssetSafely("mss301-event-platform/../other/banner"))
                .isInstanceOf(AppException.class);
    }

    @Test
    @Order(6)
    @DisplayName("6. Tasks 15-20: ShowTime Overlap Guard, TicketType Validation & Seating Configuration Sabotage")
    void test06_ShowTimeTicketTypeAndSeatingSabotage() throws Exception {
        UUID catId = createPublishedCategory("Seating Cat");
        UUID gaEventId = createDraftEventForOrganizer(organizerA, catId, "GA Event", SeatingMode.GENERAL_ADMISSION);
        UUID seatedEventId = createDraftEventForOrganizer(organizerA, catId, "Seated Event", SeatingMode.ASSIGNED_SEAT);

        // 1. Sabotage: Adding SeatSection to GENERAL_ADMISSION event -> 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/events/" + gaEventId + "/seat-sections")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SEC-A\",\"name\":\"Section A\",\"capacity\":10}"))
                .andExpect(status().isBadRequest());

        // 2. Sabotage: ShowTime in the past or endTime <= startTime -> 400 BAD_REQUEST
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + past + "\",\"endTime\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}"))
                .andExpect(status().isBadRequest());

        Instant stStart = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant stEnd = stStart.plus(3, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + stEnd + "\",\"endTime\":\"" + stStart + "\"}"))
                .andExpect(status().isBadRequest());

        // 3. Valid ShowTime creation -> 201 CREATED
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + stStart + "\",\"endTime\":\"" + stEnd + "\"}"))
                .andExpect(status().isCreated());

        // 4. Sabotage: Overlapping ShowTime -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + stStart.plus(30, ChronoUnit.MINUTES) + "\",\"endTime\":\"" + stEnd.plus(1, ChronoUnit.HOURS) + "\"}"))
                .andExpect(status().isConflict());

        // 5. Sabotage: TicketType with maxPerOrder > totalQuantity -> 400 BAD_REQUEST
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/ticket-types")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VIP\",\"price\":100000,\"totalQuantity\":5,\"maxPerOrder\":10}"))
                .andExpect(status().isBadRequest());

        // 6. Valid TicketType creation -> 201 CREATED
        MvcResult ttRes = mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/ticket-types")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"VIP\",\"price\":200000,\"totalQuantity\":20,\"maxPerOrder\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID vipTypeId = UUID.fromString(
                objectMapper.readTree(ttRes.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        // 7. Create SeatSection (capacity = 2) -> 201 CREATED
        MvcResult secRes = mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/seat-sections")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SEC-A\",\"name\":\"Khu A\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID sectionId = UUID.fromString(
                objectMapper.readTree(secRes.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        // 8. Sabotage: Duplicate section code -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/seat-sections")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"sec-a\",\"name\":\"Khu A Dup\",\"capacity\":5}"))
                .andExpect(status().isConflict());

        // 9. Sabotage: Bulk create 3 seats when section capacity is 2 -> 400 BAD_REQUEST
        EventDto.BulkCreateSeatsRequest tooManySeats = EventDto.BulkCreateSeatsRequest.builder()
                .seats(List.of(
                        EventDto.SeatItemRequest.builder().rowLabel("A").seatNumber("1").build(),
                        EventDto.SeatItemRequest.builder().rowLabel("A").seatNumber("2").build(),
                        EventDto.SeatItemRequest.builder().rowLabel("A").seatNumber("3").build()
                ))
                .build();
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/seat-sections/" + sectionId + "/seats")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooManySeats)))
                .andExpect(status().isBadRequest());

        // 10. Valid bulk create 2 seats + assign TicketType via PUT /assign-ticket-type -> 200 OK
        EventDto.BulkCreateSeatsRequest validSeats = EventDto.BulkCreateSeatsRequest.builder()
                .seats(List.of(
                        EventDto.SeatItemRequest.builder().rowLabel("A").seatNumber("1").build(),
                        EventDto.SeatItemRequest.builder().rowLabel("A").seatNumber("2").build()
                ))
                .build();
        mockMvc.perform(post("/api/v1/events/" + seatedEventId + "/seat-sections/" + sectionId + "/seats")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSeats)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/events/" + seatedEventId + "/seat-sections/" + sectionId + "/assign-ticket-type")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTypeId\":\"" + vipTypeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedTicketTypeId").value(vipTypeId.toString()))
                .andExpect(jsonPath("$.data.seats[0].assignedTicketTypeId").value(vipTypeId.toString()));
    }

    @Test
    @Order(7)
    @DisplayName("7. Tasks 21-34: State Machine Guards, Approval, Public Discovery Filters, Cancellation & Auto-Mark Ended")
    void test07_StateMachineApprovalCancellationAndMarkEnded() throws Exception {
        UUID catId = createPublishedCategory("Festival");
        UUID eventId = createDraftEventForOrganizer(organizerA, catId, "Lễ Hội Âm Nhạc Mùa Hè", SeatingMode.GENERAL_ADMISSION);

        // 1. Sabotage: Admin tries to approve a DRAFT event directly -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/events/" + eventId + "/approve")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isConflict());

        // 2. Add ShowTime & TicketType, Submit -> Reject -> Resubmit -> Approve
        Instant stStart = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant stEnd = stStart.plus(4, ChronoUnit.HOURS);
        MvcResult stRes = mockMvc.perform(post("/api/v1/events/" + eventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + stStart + "\",\"endTime\":\"" + stEnd + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID showTimeId = UUID.fromString(
                objectMapper.readTree(stRes.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        mockMvc.perform(post("/api/v1/events/" + eventId + "/ticket-types")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"GA Ticket\",\"price\":350000,\"totalQuantity\":100,\"maxPerOrder\":6}"))
                .andExpect(status().isCreated());

        // Public detail on DRAFT event must return 404 NOT_FOUND
        mockMvc.perform(get("/api/v1/events/public/" + eventId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/events/" + eventId + "/submit")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/events/" + eventId + "/approve")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 3. Public Search & Filter + Pagination Cap (?size=5000 -> capped at 100)
        mockMvc.perform(get("/api/v1/events/public")
                        .param("keyword", "Mùa Hè")
                        .param("categorySlug", "festival")
                        .param("city", "Ho Chi Minh")
                        .param("startFrom", stStart.minus(1, ChronoUnit.HOURS).toString())
                        .param("endTo", stStart.plus(1, ChronoUnit.HOURS).toString())
                        .param("minPrice", "300000")
                        .param("maxPrice", "400000")
                        .param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        // 4. Task 26: Simulate showtime in the past and trigger /api/v1/events/mark-ended -> transitions to ENDED
        jdbcTemplate.update(
                "UPDATE show_times SET start_time = ?, end_time = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(5, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                showTimeId
        );

        mockMvc.perform(post("/api/v1/events/mark-ended")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endedCount").value(1));

        mockMvc.perform(get("/api/v1/events/public/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Internal Inventory Security, Snapshot & High-Concurrency Race Condition (10 Threads vs 20 Tickets)")
    void test08_InternalInventorySecurityAndConcurrencyRaceCondition() throws Exception {
        UUID catId = createPublishedCategory("Concert Race");
        UUID eventId = createDraftEventForOrganizer(organizerA, catId, "Concurrency Concert", SeatingMode.GENERAL_ADMISSION);

        Instant stStart = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant stEnd = stStart.plus(2, ChronoUnit.HOURS);
        MvcResult stRes = mockMvc.perform(post("/api/v1/events/" + eventId + "/show-times")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"" + stStart + "\",\"endTime\":\"" + stEnd + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID showTimeId = UUID.fromString(
                objectMapper.readTree(stRes.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        // Total quantity = 20, maxPerOrder = 10
        MvcResult ttRes = mockMvc.perform(post("/api/v1/events/" + eventId + "/ticket-types")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flash Sale Ticket\",\"price\":150000,\"totalQuantity\":20,\"maxPerOrder\":10}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID ticketTypeId = UUID.fromString(
                objectMapper.readTree(ttRes.getResponse().getContentAsString()).path("data").path("id").asText()
        );

        mockMvc.perform(post("/api/v1/events/" + eventId + "/submit")
                        .header("X-User-Id", organizerA)
                        .header("X-Roles", "ROLE_ORGANIZER"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/" + eventId + "/approve")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());

        // 1. Sabotage: Wrong X-Internal-Secret -> 401 UNAUTHORIZED
        mockMvc.perform(get("/internal/v1/events/" + eventId)
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());

        // 2. Valid Internal Snapshot -> 200 OK
        mockMvc.perform(get("/internal/v1/events/" + eventId)
                        .header("X-Internal-Secret", "test_internal_secret_for_mock_verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 3. Sabotage: Requesting quantity (15) > maxPerOrder (10) -> 400 BAD_REQUEST
        InternalInventoryDto.CheckAndReserveInventoryRequest exceedMaxReq =
                InternalInventoryDto.CheckAndReserveInventoryRequest.builder()
                        .eventId(eventId)
                        .showTimeId(showTimeId)
                        .items(List.of(InternalInventoryDto.TicketItemReservationRequest.builder()
                                .ticketTypeId(ticketTypeId)
                                .quantity(15)
                                .build()))
                        .build();
        mockMvc.perform(post("/internal/v1/events/inventory/check-and-reserve")
                        .header("X-Internal-Secret", "test_internal_secret_for_mock_verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exceedMaxReq)))
                .andExpect(status().isBadRequest());

        // 4. Concurrency Race Condition: 10 threads simultaneously try to reserve 10 tickets each (total available = 20)
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        InternalInventoryDto.CheckAndReserveInventoryRequest raceReq =
                InternalInventoryDto.CheckAndReserveInventoryRequest.builder()
                        .eventId(eventId)
                        .showTimeId(showTimeId)
                        .items(List.of(InternalInventoryDto.TicketItemReservationRequest.builder()
                                .ticketTypeId(ticketTypeId)
                                .quantity(10)
                                .build()))
                        .build();
        String raceJson = objectMapper.writeValueAsString(raceReq);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                int httpStatus = mockMvc.perform(post("/internal/v1/events/inventory/check-and-reserve")
                                .header("X-Internal-Secret", "test_internal_secret_for_mock_verification")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(raceJson))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                if (httpStatus == 200) {
                    successCount.incrementAndGet();
                } else if (httpStatus == 409) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        readyLatch.await();
        startLatch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(conflictCount.get()).isEqualTo(8);

        var inv = inventoryRepository.findByShowTimeId(showTimeId).get(0);
        assertThat(inv.getReservedQuantity()).isEqualTo(20);
        assertThat(inv.getAvailableQuantity()).isEqualTo(0);
    }

    private UUID createPublishedCategory(String name) throws Exception {
        CategoryDto.CreateCategoryRequest req = CategoryDto.CreateCategoryRequest.builder()
                .name(name)
                .status(CategoryStatus.PUBLISHED)
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/categories")
                        .header("X-User-Id", adminId)
                        .header("X-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText()
        );
    }

    private UUID createDraftEventForOrganizer(UUID organizerId, UUID categoryId, String title, SeatingMode mode) throws Exception {
        EventDto.CreateEventRequest req = EventDto.CreateEventRequest.builder()
                .title(title)
                .categoryId(categoryId)
                .venueName("SECC")
                .city("Ho Chi Minh")
                .address("Quận 7")
                .seatingMode(mode)
                .refundPolicy(RefundPolicy.NO_REFUND)
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/events")
                        .header("X-User-Id", organizerId)
                        .header("X-Roles", "ROLE_ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText()
        );
    }
}
