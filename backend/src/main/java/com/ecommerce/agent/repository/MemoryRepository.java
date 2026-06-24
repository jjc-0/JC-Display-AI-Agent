package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.v2.MemoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MemoryRepository extends JpaRepository<MemoryEntry, Long> {

    /** 按用户和类型查询 */
    List<MemoryEntry> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type);

    /** 按用户查询所有记忆 */
    List<MemoryEntry> findByUserIdAndArchivedFalseOrderByImportanceDesc(String userId);

    /** 按客户查询 */
    List<MemoryEntry> findByCustomerIdAndArchivedFalseOrderByCreatedAtDesc(String customerId);

    /** 按标签查询 */
    @Query("SELECT m FROM MemoryEntry m WHERE m.userId = :userId AND m.tags LIKE %:tag% AND m.archived = false ORDER BY m.createdAt DESC")
    List<MemoryEntry> findByUserIdAndTag(@Param("userId") String userId, @Param("tag") String tag);

    /** 搜索记忆内容 (模糊匹配) */
    @Query("SELECT m FROM MemoryEntry m WHERE m.userId = :userId AND (m.summary LIKE %:keyword% OR m.content LIKE %:keyword%) AND m.archived = false ORDER BY m.importance DESC, m.createdAt DESC")
    Page<MemoryEntry> search(@Param("userId") String userId, @Param("keyword") String keyword, Pageable pageable);

    /** 清理过期记忆 */
    @Query("DELETE FROM MemoryEntry m WHERE m.expiresAt IS NOT NULL AND m.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);

    /** 按重要性获取Top N */
    List<MemoryEntry> findTop10ByUserIdAndArchivedFalseOrderByImportanceDescAccessCountDesc(String userId);

    /** 统计各类型数量 */
    @Query("SELECT m.type, COUNT(m) FROM MemoryEntry m WHERE m.userId = :userId AND m.archived = false GROUP BY m.type")
    List<Object[]> countByType(@Param("userId") String userId);
}
