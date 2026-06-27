package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.ConversationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRecordRepository extends JpaRepository<ConversationRecord, Long> {

    List<ConversationRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ConversationRecord> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<ConversationRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ConversationRecord> findByUsernameOrderByCreatedAtDesc(String username);

    @Query("SELECT r FROM ConversationRecord r WHERE r.sessionId = :sessionId ORDER BY r.createdAt ASC")
    List<ConversationRecord> findHistoryForLLM(@Param("sessionId") String sessionId);

    void deleteBySessionId(String sessionId);

    long countBySessionId(String sessionId);

    @Query("SELECT r.sessionId, COUNT(r) as cnt, MAX(r.createdAt) as lastTime " +
           "FROM ConversationRecord r GROUP BY r.sessionId ORDER BY lastTime DESC")
    List<Object[]> findSessionSummaries();

    @Query("SELECT r FROM ConversationRecord r WHERE r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt DESC")
    List<ConversationRecord> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r FROM ConversationRecord r WHERE r.userId = :userId AND r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt DESC")
    List<ConversationRecord> findByUserIdAndDateRange(@Param("userId") String userId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);
}
