package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, Long> {

    Optional<PromptTemplateEntity> findByTemplateUid(String templateUid);

    List<PromptTemplateEntity> findByEnabledTrueOrderByNameAsc();

    List<PromptTemplateEntity> findByCategoryAndEnabledTrue(String category);

    void deleteByTemplateUid(String templateUid);
}
