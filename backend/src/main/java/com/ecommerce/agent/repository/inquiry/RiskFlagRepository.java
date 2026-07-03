package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.RiskFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskFlagRepository extends JpaRepository<RiskFlag, Long> {
    List<RiskFlag> findByCaseIdOrderByLevelDesc(Long caseId);
    long countByCaseId(Long caseId);
    long countByCaseIdAndLevel(Long caseId, String level);
    void deleteByCaseId(Long caseId);
}
