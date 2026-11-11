package com.smartpark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import com.smartpark.entity.User;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.JwtService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.http.MediaType;

@SpringBootTest(properties = "smartpark.jwt.secret=test-secret-key-for-jwt-signing-32-bytes-minimum")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class ApiSecurityTest {

    private static final Path KYC_TEST_DIRECTORY = createKycTestDirectory();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private JavaMailSender mailSender;

    @org.springframework.test.context.DynamicPropertySource
    static void configureKycStorage(
            org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("smartpark.kyc.storage-directory", () -> KYC_TEST_DIRECTORY.toString());
    }

    @AfterAll
    static void removeKycTestFiles() throws IOException {
        try (var paths = Files.walk(KYC_TEST_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void protectedProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void portalRoutesArePublicAndForwardToTheClient() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .forwardedUrl("/index.html"));
    }

    @Test
    void adminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLoginReturnsCredentialError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"missing-account@example.test","password":"Secure1"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().string(org.hamcrest.Matchers.containsString(
                    "Invalid email or password"
                )));
    }

    @Test
    void customerAccountIsCreatedOnlyAfterEmailOtpVerification() throws Exception {
        String email = "customer-otp@example.test";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Verified Customer","email":"%s",
                     "phone":"+15555550199","password":"Secure1"}
                    """.formatted(email)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.developmentOtp").doesNotExist());
        org.junit.jupiter.api.Assertions.assertFalse(
            userRepository.existsByEmailIgnoreCase(email)
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
            ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        java.util.regex.Matcher otpMatcher =
            java.util.regex.Pattern.compile("\\b(\\d{6})\\b")
                .matcher(messageCaptor.getValue().getText());
        org.junit.jupiter.api.Assertions.assertTrue(otpMatcher.find());
        String otp = otpMatcher.group(1);
        String incorrectOtp = "000000".equals(otp) ? "000001" : "000000";

        mockMvc.perform(post("/api/auth/register/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","otp":"%s"}
                    """.formatted(email, incorrectOtp)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","otp":"%s"}
                    """.formatted(email, otp)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
            userRepository.existsByEmailIgnoreCase(email)
        );
    }

    @Test
    void ownerApprovalQueueIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/owner-approvals"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/owner-approvals")
                .with(SecurityMockMvcRequestPostProcessors.user("owner").roles("PARKING_OWNER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerEmailAndDocumentSubmissionRemainsPendingUntilAdminReview() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"facilityName":"Test Facility","address":"Test address",
                     "totalCapacity":25,"operatingStart":"00:00:00",
                     "operatingEnd":"23:59:00","ownerUserId":null,
                     "ownerRegistrationEnabled":true,"cancellationEnabled":true,
                     "cancellationCutoffMinutes":0,"refundPercentage":100,
                     "vehicleTypes":["Two-wheeler","Four-wheeler"]}
                    """))
            .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/auth/register/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"KYC Owner","email":"kyc-owner@example.test",
                     "phone":"+15555550199","password":"Secure1"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");

        mockMvc.perform(post("/api/owner/verification/otp")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(jsonPath("$.otpHash").doesNotExist())
            .andExpect(jsonPath("$.developmentOtp").doesNotExist());

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
            ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        String emailText = messageCaptor.getValue().getText();
        java.util.regex.Matcher otpMatcher =
            java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(emailText);
        org.junit.jupiter.api.Assertions.assertTrue(otpMatcher.find());
        String otp = otpMatcher.group(1);

        mockMvc.perform(post("/api/owner/verification/otp/verify")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + otp + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        MockMultipartFile document = new MockMultipartFile(
            "document", "identity.pdf", "application/pdf",
            "%PDF-1.4\nverified test document".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/owner/verification/document")
                .file(document)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentUploaded").value(true))
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        User admin = saveTestAdmin("kyc-review-admin@example.test");
        long ownerId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
        String adminToken = jwtService.issueToken(admin);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException(
            "SMTP unavailable"
        )).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/approve", ownerId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owner.approvalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.owner.approvalMethod").value("MANUAL"))
            .andExpect(jsonPath("$.notificationSent").value(false));

        mockMvc.perform(get("/api/owner/verification")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void adminCanRejectOwnerApplicationWithReason() throws Exception {
        String ownerJson = registerOwner("rejected-owner@example.test");
        long ownerId = ((Number) com.jayway.jsonpath.JsonPath.read(ownerJson, "$.id")).longValue();
        User admin = saveTestAdmin("kyc-reject-admin@example.test");
        String adminToken = jwtService.issueToken(admin);

        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/reject", ownerId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Please submit a clearer identity document."}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owner.approvalStatus").value("REJECTED"))
            .andExpect(jsonPath("$.owner.approvalMethod").value("MANUAL"))
            .andExpect(jsonPath("$.owner.rejectionReason")
                .value("Please submit a clearer identity document."));

        mockMvc.perform(get("/api/owner/verification")
                .header("Authorization", "Bearer "
                    + com.jayway.jsonpath.JsonPath.read(ownerJson, "$.accessToken")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason")
                .value("Please submit a clearer identity document."));
    }

    @Test
    void adminCannotApproveOwnerBeforeEmailAndDocumentSubmission() throws Exception {
        String ownerJson = registerOwner("incomplete-owner@example.test");
        long ownerId = ((Number) com.jayway.jsonpath.JsonPath.read(ownerJson, "$.id")).longValue();
        User admin = saveTestAdmin("incomplete-owner-admin@example.test");

        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/approve", ownerId)
                .header("Authorization", "Bearer " + jwtService.issueToken(admin)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                "The owner must verify their email and upload a government document before approval"
            ));

        mockMvc.perform(get("/api/owner/verification")
                .header("Authorization", "Bearer "
                    + com.jayway.jsonpath.JsonPath.read(ownerJson, "$.accessToken")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("PENDING"));
    }

    @Test
    void approvedOwnersCanCreateFacilitiesAndOnlyManageTheirOwnSpaces() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"facilityName":"Test Facility","address":"Test address",
                     "totalCapacity":25,"operatingStart":"00:00:00",
                     "operatingEnd":"23:59:00","ownerUserId":null,
                     "ownerRegistrationEnabled":true,"cancellationEnabled":true,
                     "cancellationCutoffMinutes":0,"refundPercentage":100,
                     "vehicleTypes":["Two-wheeler","Four-wheeler"]}
                    """))
            .andExpect(status().isOk());

        String firstOwnerJson = registerOwner("facility-owner-a@example.test");
        String firstOwnerToken = com.jayway.jsonpath.JsonPath.read(firstOwnerJson, "$.accessToken");
        long firstOwnerId = ((Number) com.jayway.jsonpath.JsonPath.read(firstOwnerJson, "$.id")).longValue();
        completeOwnerVerification(firstOwnerToken);
        mockMvc.perform(post("/api/owner/facility")
                .header("Authorization", "Bearer " + firstOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(facilityRequest("Owner A Facility")))
            .andExpect(status().isForbidden());

        User admin = saveTestAdmin("facility-admin@example.test");
        String adminToken = jwtService.issueToken(admin);
        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/approve", firstOwnerId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/owner/facility")
                .header("Authorization", "Bearer " + firstOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(facilityRequest("Owner A Facility", "Latur, Maharashtra")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Owner A Facility"));
        String firstSlotJson = mockMvc.perform(post("/api/owner/parking-slots")
                .header("Authorization", "Bearer " + firstOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotRequest("OWNER-A-1")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long firstSlotId = ((Number) com.jayway.jsonpath.JsonPath.read(firstSlotJson, "$.id")).longValue();
        String availabilityStart = java.time.LocalDateTime.now().plusHours(1)
            .withSecond(0).withNano(0).toString();
        String availabilityEnd = java.time.LocalDateTime.parse(availabilityStart)
            .plusHours(1).toString();
        mockMvc.perform(get("/api/bookings/availability")
                .param("startTime", availabilityStart)
                .param("endTime", availabilityEnd))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/availability")
                .param("startTime", availabilityStart)
                .param("endTime", availabilityEnd)
                .param("locationQuery", "Latur"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].facilityName").value("Owner A Facility"))
            .andExpect(jsonPath("$[0].facilityAddress").value("Latur, Maharashtra"));
        mockMvc.perform(get("/api/bookings/availability")
                .param("startTime", availabilityStart)
                .param("endTime", availabilityEnd)
                .param("latitude", "18.4088")
                .param("longitude", "76.5604")
                .param("radiusKm", "20")
                .param("locationQuery", "Latur"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].facilityName").value("Owner A Facility"));

        String secondOwnerJson = registerOwner("facility-owner-b@example.test");
        String secondOwnerToken = com.jayway.jsonpath.JsonPath.read(secondOwnerJson, "$.accessToken");
        long secondOwnerId = ((Number) com.jayway.jsonpath.JsonPath.read(secondOwnerJson, "$.id")).longValue();
        completeOwnerVerification(secondOwnerToken);
        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/approve", secondOwnerId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/owner/facility")
                .header("Authorization", "Bearer " + secondOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(facilityRequest("Owner B Facility")))
            .andExpect(status().isCreated());
        String secondSlotJson = mockMvc.perform(post("/api/owner/parking-slots")
                .header("Authorization", "Bearer " + secondOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotRequest("OWNER-B-1")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long secondSlotId = ((Number) com.jayway.jsonpath.JsonPath.read(secondSlotJson, "$.id")).longValue();

        mockMvc.perform(get("/api/owner/overview")
                .header("Authorization", "Bearer " + firstOwnerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facility.name").value("Owner A Facility"))
            .andExpect(jsonPath("$.slots[0].slotNumber").value("OWNER-A-1"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/owner/parking-slots/{slotId}", secondSlotId)
                .header("Authorization", "Bearer " + firstOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotRequest("OWNER-B-CHANGED")))
            .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/owner/parking-slots/{slotId}", firstSlotId)
                .header("Authorization", "Bearer " + firstOwnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotRequest("OWNER-A-UPDATED")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotNumber").value("OWNER-A-UPDATED"));
    }

    @Test
    void userCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanLoadDatabaseDrivenDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void parkingSlotDiscoveryRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/parking-slots"))
            .andExpect(status().isOk());
    }

    @Test
    void ownerRegistrationAndFacilityAssignmentAreRoleScoped() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"facilityName":"Test Facility","address":"Test address",
                     "totalCapacity":25,"operatingStart":"00:00:00",
                     "operatingEnd":"23:59:00","ownerUserId":null,
                     "ownerRegistrationEnabled":true,"cancellationEnabled":true,
                     "cancellationCutoffMinutes":0,"refundPercentage":100,
                     "vehicleTypes":["Two-wheeler","Four-wheeler"]}
                    """))
            .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/auth/register/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Test Owner","email":"owner-flow@example.test",
                     "phone":"+15555550123","password":"Secure1"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("PARKING_OWNER"))
            .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
        Number ownerIdValue = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        long ownerId = ownerIdValue.longValue();
        completeOwnerVerification(token);

        mockMvc.perform(get("/api/owner/overview")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        User admin = new User();
        admin.setFullName("Test Administrator");
        admin.setEmail("test-admin-owner-flow@example.test");
        admin.setPasswordHash("not-used-by-this-test");
        admin.setRole("ADMIN");
        admin = userRepository.saveAndFlush(admin);
        String adminToken = jwtService.issueToken(admin);

        mockMvc.perform(get("/api/admin/owner-approvals")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.owner.ownerUserId == %d)]".formatted(ownerId)).exists());
        mockMvc.perform(post("/api/admin/owner-approvals/{ownerId}/approve", ownerId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owner.approvalMethod").value("MANUAL"));

        mockMvc.perform(put("/api/admin/settings")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"facilityName":"Test Facility","address":"Test address",
                     "totalCapacity":25,"operatingStart":"00:00:00",
                     "operatingEnd":"23:59:00","ownerUserId":%d,
                     "ownerRegistrationEnabled":true,"cancellationEnabled":true,
                     "cancellationCutoffMinutes":0,"refundPercentage":100,
                     "vehicleTypes":["Two-wheeler","Four-wheeler"]}
                    """.formatted(ownerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currencyCode").value("INR"));

        mockMvc.perform(get("/api/owner/overview")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facility.name").value("Test Facility"));
    }

    @Test
    void ownerRegistrationCanBeDisabledByAdminPolicy() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"facilityName":"Test Facility","address":"Test address",
                     "totalCapacity":25,"operatingStart":"00:00:00",
                     "operatingEnd":"23:59:00","ownerUserId":null,
                     "ownerRegistrationEnabled":false,"cancellationEnabled":true,
                     "cancellationCutoffMinutes":0,"refundPercentage":100,
                     "vehicleTypes":["Car"]}
                    """))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Disabled Owner","email":"disabled-owner@example.test",
                     "phone":"+15555550124","password":"Secure1"}
                    """))
            .andExpect(status().isForbidden());
    }

    private static Path createKycTestDirectory() {
        try {
            return Files.createTempDirectory("smartpark-kyc-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private String registerOwner(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fullName":"Facility Owner","email":"%s",
                     "phone":"+15555550199","password":"Secure1"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private void completeOwnerVerification(String token) throws Exception {
        mockMvc.perform(post("/api/owner/verification/otp")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
            ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.atLeastOnce())
            .send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getAllValues()
            .get(messageCaptor.getAllValues().size() - 1);
        java.util.regex.Matcher otpMatcher =
            java.util.regex.Pattern.compile("\\b(\\d{6})\\b")
                .matcher(sentMessage.getText());
        org.junit.jupiter.api.Assertions.assertTrue(otpMatcher.find());

        mockMvc.perform(post("/api/owner/verification/otp/verify")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + otpMatcher.group(1) + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));

        MockMultipartFile document = new MockMultipartFile(
            "document", "identity.pdf", "application/pdf",
            "%PDF-1.4\nverified test document".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/owner/verification/document")
                .file(document)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentUploaded").value(true));
    }

    private User saveTestAdmin(String email) {
        User admin = new User();
        admin.setFullName("Test Administrator");
        admin.setEmail(email);
        admin.setPasswordHash("not-used-by-this-test");
        admin.setRole("ADMIN");
        return userRepository.saveAndFlush(admin);
    }

    private static String facilityRequest(String name) {
        return facilityRequest(name, "123 Owner Road");
    }

    private static String facilityRequest(String name, String address) {
        return """
            {"name":"%s","address":"%s","totalCapacity":2,
             "operatingStart":"06:00:00","operatingEnd":"22:00:00"}
            """.formatted(name, address);
    }

    private static String slotRequest(String slotNumber) {
        return """
            {"slotNumber":"%s","floorNumber":0,"location":"Level 0","zone":"A",
             "vehicleType":"Four-wheeler","hourlyPrice":10,"dailyPrice":100,
             "monthlyPrice":1000,"status":"AVAILABLE"}
            """.formatted(slotNumber);
    }
}
