package com.smartpark.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartpark.entity.OwnerVerification;
import com.smartpark.entity.OwnerVerification.ApprovalStatus;
import com.smartpark.entity.User;
import com.smartpark.repository.OwnerVerificationRepository;
import com.smartpark.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnerVerificationService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(OwnerVerificationService.class);
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OwnerVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmartParkMailDeliveryService mailDeliveryService;
    private final Path storageDirectory;
    private final String mailFrom;
    private final boolean exposeVerificationCodes;

    public OwnerVerificationService(
            OwnerVerificationRepository verificationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SmartParkMailDeliveryService mailDeliveryService,
            @Value("${smartpark.kyc.storage-directory}") String storageDirectory,
            @Value("${smartpark.mail.from:noreply@smartpark.local}") String mailFrom,
            @Value("${smartpark.auth.expose-verification-codes:false}")
                boolean exposeVerificationCodes) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailDeliveryService = mailDeliveryService;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.mailFrom = mailFrom;
        this.exposeVerificationCodes = exposeVerificationCodes;
    }

    @Transactional
    public OwnerVerification initializeOwner(User owner) {
        return verificationRepository.save(OwnerVerification.pending(owner));
    }

    @Transactional
    public OwnerVerification initializeEmailVerifiedOwner(User owner) {
        OwnerVerification verification = OwnerVerification.pending(owner);
        verification.verifyEmail();
        return verificationRepository.save(verification);
    }

    @Transactional
    public VerificationView sendOtp(Long ownerId) {
        OwnerVerification verification = getVerification(ownerId);
        ensurePending(verification);
        LocalDateTime now = LocalDateTime.now();
        if (verification.getOtpSentAt() != null
                && verification.getOtpSentAt().plusSeconds(60).isAfter(now)) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Please wait one minute before requesting another verification code"
            );
        }

        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        verification.prepareOtp(passwordEncoder.encode(otp), now);
        verificationRepository.save(verification);

        if (!exposeVerificationCodes) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(verification.getOwner().getEmail());
            message.setSubject("Your SmartPark owner verification code");
            message.setText(
                "Your SmartPark verification code is " + otp
                    + ". It expires in 10 minutes. If you did not request this, ignore this email."
            );
            try {
                mailDeliveryService.send(message);
            } catch (MailException exception) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification email could not be sent. Check the email service configuration and try again.",
                    exception
                );
            }
        }
        return view(verification, exposeVerificationCodes ? otp : null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VerificationView verifyOtp(Long ownerId, String otp) {
        OwnerVerification verification = getVerification(ownerId);
        ensurePending(verification);
        LocalDateTime now = LocalDateTime.now();
        if (verification.getOtpHash() == null
                || verification.getOtpExpiresAt() == null
                || !verification.getOtpExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Verification code is missing or expired. Request a new code."
            );
        }
        if (verification.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect verification attempts. Request a new code."
            );
        }
        if (otp == null || !otp.matches("\\d{6}")
                || !passwordEncoder.matches(otp, verification.getOtpHash())) {
            verification.recordFailedOtpAttempt();
            verificationRepository.save(verification);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The verification code is incorrect"
            );
        }
        verification.verifyEmail();
        return view(verificationRepository.save(verification));
    }

    @Transactional
    public VerificationView uploadDocument(Long ownerId, MultipartFile document) {
        OwnerVerification verification = getVerification(ownerId);
        ensurePending(verification);
        if (!verification.isEmailVerified()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Verify your email before uploading your government document"
            );
        }
        if (document == null || document.isEmpty()
                || document.getSize() > MAX_DOCUMENT_SIZE) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Choose a document no larger than 10 MB"
            );
        }

        String fileName = safeFileName(document.getOriginalFilename());
        String contentType = validateDocument(document, fileName);
        String storageKey = UUID.randomUUID().toString();
        Path storedFile = storageDirectory.resolve(storageKey).normalize();
        if (!storedFile.getParent().equals(storageDirectory)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid document name"
            );
        }

        try {
            Files.createDirectories(storageDirectory);
            Files.write(
                storedFile,
                document.getBytes(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Government document could not be stored securely",
                exception
            );
        }

        try {
            verification.setDocument(
                storageKey, fileName, contentType, document.getSize(), LocalDateTime.now()
            );
            return view(verificationRepository.save(verification));
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(storedFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public VerificationView getOwnerVerification(Long ownerId) {
        return view(getVerification(ownerId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingApprovals() {
        return verificationRepository
            .findAllByApprovalStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING)
            .stream()
            .filter(OwnerVerification::isEmailVerified)
            .map(this::approvalRequest)
            .toList();
    }

    public void deleteDocumentFile(Long ownerId, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path document = storageDirectory.resolve(storageKey).normalize();
        if (!storageDirectory.equals(document.getParent())) {
            throw new IllegalArgumentException("Invalid owner document storage key");
        }
        try {
            Files.deleteIfExists(document);
        } catch (IOException exception) {
            LOGGER.error("Unable to remove KYC document for deleted owner {}", ownerId, exception);
        }
    }

    @Transactional
    public ApprovalRequest approveManually(Long ownerId, Long adminId) {
        OwnerVerification verification = getVerification(ownerId);
        ensurePending(verification);
        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Administrator account was not found"
            ));
        if (!verification.isEmailVerified()
                || verification.getDocumentStorageKey() == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The owner must verify their email and upload a government document before approval"
            );
        }
        verification.approveManually(admin, LocalDateTime.now());
        OwnerVerification approved = verificationRepository.save(verification);
        boolean notificationSent = sendDecisionEmail(approved.getOwner(), true, null);
        return approvalRequest(approved, notificationSent);
    }

    @Transactional
    public ApprovalRequest rejectManually(Long ownerId, Long adminId, String reason) {
        OwnerVerification verification = getVerification(ownerId);
        ensurePending(verification);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Provide a rejection reason of at most 1000 characters"
            );
        }
        User admin = userRepository.findById(adminId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Administrator account was not found"
            ));
        verification.rejectManually(admin, reason.trim(), LocalDateTime.now());
        OwnerVerification rejected = verificationRepository.save(verification);
        boolean notificationSent = sendDecisionEmail(
            rejected.getOwner(), false, reason.trim()
        );
        return approvalRequest(rejected, notificationSent);
    }

    private boolean sendDecisionEmail(User owner, boolean approved, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(owner.getEmail());
        message.setSubject(approved
            ? "Your SmartPark owner application was approved"
            : "Your SmartPark owner application was not approved");
        message.setText(approved
            ? "Your SmartPark parking-owner application has been approved. You can now create and manage your parking listing."
            : "Your SmartPark parking-owner application was not approved.\n\nReason: "
                + reason + "\n\nYou may contact SmartPark support if you believe this decision was made in error.");
        try {
            mailDeliveryService.send(message);
            return true;
        } catch (MailException exception) {
            LOGGER.warn(
                "Owner decision was saved, but its notification email could not be sent (owner id={})",
                owner.getId(),
                exception
            );
            return false;
        }
    }

    @Transactional(readOnly = true)
    public DocumentDownload getDocumentForAdmin(Long ownerId) {
        OwnerVerification verification = getVerification(ownerId);
        if (verification.getDocumentStorageKey() == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No government document has been uploaded"
            );
        }
        Path document = storageDirectory.resolve(
            verification.getDocumentStorageKey()
        ).normalize();
        if (!document.getParent().equals(storageDirectory)) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Stored document reference is invalid"
            );
        }
        try {
            return new DocumentDownload(
                Files.readAllBytes(document),
                verification.getDocumentFileName(),
                verification.getDocumentContentType()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Government document could not be read",
                exception
            );
        }
    }

    private OwnerVerification getVerification(Long ownerId) {
        return verificationRepository.findByOwner_Id(ownerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Owner verification request was not found"
            ));
    }

    private static void ensurePending(OwnerVerification verification) {
        if (verification.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "This owner verification request has already been reviewed"
            );
        }
    }

    private VerificationView view(OwnerVerification verification) {
        return view(verification, null);
    }

    private VerificationView view(OwnerVerification verification, String developmentOtp) {
        return new VerificationView(
            verification.getOwner().getId(),
            verification.getOwner().getFullName(),
            verification.getOwner().getEmail(),
            verification.getOwner().getPhone(),
            verification.isEmailVerified(),
            verification.getDocumentFileName() != null,
            verification.getDocumentFileName(),
            verification.getDocumentUploadedAt(),
            verification.getApprovalStatus().name(),
            verification.getApprovalMethod(),
            verification.getCreatedAt(),
            verification.getRejectionReason(),
            developmentOtp
        );
    }

    private ApprovalRequest approvalRequest(OwnerVerification verification) {
        return approvalRequest(verification, null);
    }

    private ApprovalRequest approvalRequest(
            OwnerVerification verification, Boolean notificationSent) {
        return new ApprovalRequest(
            view(verification),
            verification.getDocumentStorageKey() != null,
            verification.getReviewedBy() == null
                ? null : verification.getReviewedBy().getFullName(),
            verification.getReviewedAt(),
            notificationSent
        );
    }

    private static String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A document file name is required"
            );
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The document file name is invalid"
            );
        }
        return name;
    }

    private static String validateDocument(MultipartFile document, String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String contentType = document.getContentType();
        String normalizedType = contentType == null
            ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean validPdf = lowerName.endsWith(".pdf")
            && "application/pdf".equals(normalizedType)
            && hasPrefix(document, new byte[] {'%', 'P', 'D', 'F', '-'});
        boolean validPng = lowerName.endsWith(".png")
            && "image/png".equals(normalizedType)
            && hasPrefix(document, new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
            });
        boolean validJpeg = (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg"))
            && "image/jpeg".equals(normalizedType)
            && hasPrefix(document, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        if (!validPdf && !validPng && !validJpeg) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Upload a valid PDF, PNG, JPG, or JPEG government document"
            );
        }
        return normalizedType;
    }

    private static boolean hasPrefix(MultipartFile file, byte[] prefix) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if (bytes[i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Government document could not be read", exception
            );
        }
    }

    public record VerificationView(
        Long ownerUserId,
        String ownerName,
        String email,
        String phone,
        boolean emailVerified,
        boolean documentUploaded,
        String documentFileName,
        LocalDateTime documentUploadedAt,
        String approvalStatus,
        String approvalMethod,
        LocalDateTime requestedAt,
        String rejectionReason,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String developmentOtp
    ) {
    }

    public record ApprovalRequest(
        VerificationView owner,
        boolean documentAvailable,
        String reviewedBy,
        LocalDateTime reviewedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean notificationSent
    ) {
    }

    public record DocumentDownload(byte[] content, String fileName, String contentType) {
    }
}
