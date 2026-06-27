package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {

    Optional<ConversationSession> findBySessionId(String sessionId);

    List<ConversationSession> findAllByOrderByUpdatedAtDesc();

    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<ConversationSession> findByUsernameOrderByUpdatedAtDesc(String username);

    List<ConversationSession> findByOperationTypeOrderByUpdatedAtDesc(String operationType);

    List<ConversationSession> findByUserIdAndOperationTypeOrderByUpdatedAtDesc(String userId, String operationType);

    @Query("SELECT s.operationType, COUNT(s) FROM ConversationSession s GROUP BY s.operationType ORDER BY COUNT(s) DESC")
    List<Object[]> countByOperationType();

    long count();

    void deleteBySessionId(String sessionId);
}
