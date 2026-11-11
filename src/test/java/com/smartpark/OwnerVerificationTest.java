package com.smartpark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartpark.entity.OwnerVerification;
import com.smartpark.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OwnerVerificationTest {

    @Test
    void emailAndDocumentVerificationStillRequiresAdministratorReview() {
        OwnerVerification verification = OwnerVerification.pending(owner());

        verification.verifyEmail();
        assertEquals(OwnerVerification.ApprovalStatus.PENDING, verification.getApprovalStatus());

        verification.setDocument(
            "a-document-key", "identity.pdf", "application/pdf", 128,
            LocalDateTime.now()
        );

        assertEquals(OwnerVerification.ApprovalStatus.PENDING, verification.getApprovalStatus());
        assertNull(verification.getApprovalMethod());
        assertTrue(verification.isEmailVerified());
    }

    @Test
    void manualApprovalDoesNotClaimAutomaticVerification() {
        User owner = owner();
        OwnerVerification verification = OwnerVerification.pending(owner);
        User admin = owner();
        admin.setFullName("Admin");

        verification.approveManually(admin, LocalDateTime.now());

        assertEquals(OwnerVerification.ApprovalStatus.APPROVED, verification.getApprovalStatus());
        assertEquals("MANUAL", verification.getApprovalMethod());
        assertFalse(verification.isEmailVerified());
        assertEquals("Admin", verification.getReviewedBy().getFullName());
    }

    @Test
    void rejectionRecordsTheAdministratorAndReason() {
        User owner = owner();
        OwnerVerification verification = OwnerVerification.pending(owner);
        User admin = owner();
        admin.setFullName("Admin");

        verification.rejectManually(admin, "Document is unreadable", LocalDateTime.now());

        assertEquals(OwnerVerification.ApprovalStatus.REJECTED, verification.getApprovalStatus());
        assertEquals("MANUAL", verification.getApprovalMethod());
        assertEquals("Document is unreadable", verification.getRejectionReason());
        assertEquals("Admin", verification.getReviewedBy().getFullName());
    }

    private static User owner() {
        User user = new User();
        user.setFullName("Test Owner");
        user.setEmail("test-owner@example.test");
        user.setPasswordHash("not-used");
        user.setRole("PARKING_OWNER");
        return user;
    }
}
