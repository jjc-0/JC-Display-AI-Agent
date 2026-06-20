package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByEnabledTrueOrderByTitleAsc();

    List<KnowledgeDocument> findByCategoryAndEnabledTrue(String category);

    List<KnowledgeDocument> findByTitleContainingIgnoreCase(String keyword);

    List<KnowledgeDocument> findBySourceTypeOrderByUpdatedAtDesc(String sourceType);

    long countBySourceType(String sourceType);

    @Query("SELECT kd FROM KnowledgeDocument kd WHERE " +
           "kd.title LIKE %:keyword% OR kd.content LIKE %:keyword% OR kd.category LIKE %:keyword% " +
           "ORDER BY kd.updatedAt DESC")
    Page<KnowledgeDocument> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Page<KnowledgeDocument> findBySourceType(String sourceType, Pageable pageable);
}
