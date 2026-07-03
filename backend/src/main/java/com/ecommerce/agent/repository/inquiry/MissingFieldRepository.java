package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.MissingField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissingFieldRepository extends JpaRepository<MissingField, Long> {
    List<MissingField> findByCaseIdOrderByPriorityDesc(Long caseId);
    long countByCaseId(Long caseId);
    long countByCaseIdAndPriority(Long caseId, String priority);
    void deleteByCaseId(Long caseId);
}
