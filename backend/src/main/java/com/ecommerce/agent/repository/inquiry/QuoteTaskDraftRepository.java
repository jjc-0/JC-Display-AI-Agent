package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.QuoteTaskDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuoteTaskDraftRepository extends JpaRepository<QuoteTaskDraft, Long> {
    Optional<QuoteTaskDraft> findByCaseId(Long caseId);
    void deleteByCaseId(Long caseId);
}
