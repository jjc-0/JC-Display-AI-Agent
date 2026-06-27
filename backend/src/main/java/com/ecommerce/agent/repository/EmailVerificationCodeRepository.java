package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByTargetEmailAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String targetEmail,
            String purpose,
            LocalDateTime now
    );
}
