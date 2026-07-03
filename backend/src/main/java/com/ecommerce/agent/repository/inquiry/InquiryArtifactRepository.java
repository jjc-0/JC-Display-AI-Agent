package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.InquiryArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryArtifactRepository extends JpaRepository<InquiryArtifact, Long> {
    List<InquiryArtifact> findByCaseIdOrderByCreatedAtDesc(Long caseId);
    long countByCaseIdAndParseStatus(Long caseId, String parseStatus);
    void deleteByCaseId(Long caseId);
}
