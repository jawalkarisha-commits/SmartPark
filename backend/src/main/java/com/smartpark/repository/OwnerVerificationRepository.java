package com.smartpark.repository;

import com.smartpark.entity.OwnerVerification;
import com.smartpark.entity.OwnerVerification.ApprovalStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerVerificationRepository
        extends JpaRepository<OwnerVerification, Long> {

    Optional<OwnerVerification> findByOwner_Id(Long ownerId);

    List<OwnerVerification> findAllByApprovalStatusOrderByCreatedAtAsc(
        ApprovalStatus approvalStatus
    );
}
