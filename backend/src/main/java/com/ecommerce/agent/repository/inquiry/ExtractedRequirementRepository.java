package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.ExtractedRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtractedRequirementRepository extends JpaRepository<ExtractedRequirement, Long> {
    List<ExtractedRequirement> findByCaseIdOrderByFieldKeyAsc(Long caseId);
    long countByCaseId(Long caseId);
    void deleteByCaseId(Long caseId);
}
