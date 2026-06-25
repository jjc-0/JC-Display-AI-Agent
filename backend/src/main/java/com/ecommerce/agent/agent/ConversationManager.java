package com.ecommerce.agent.agent;

import com.ecommerce.agent.model.ConversationMessage;
import com.ecommerce.agent.model.ConversationRecord;
import com.ecommerce.agent.model.ConversationSession;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.ecommerce.agent.repository.ConversationSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConversationManager {

    private final Map<String, Deque<ConversationMessage>> sessions = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 20;

    private final ConversationRecordRepository recordRepository;
    private final ConversationSessionRepository sessionRepository;

    public ConversationManager(ConversationRecordRepository recordRepository,
                               ConversationSessionRepository sessionRepository) {
        this.recordRepository = recordRepository;
        this.sessionRepository = sessionRepository;
    }

    private boolean isDbAvailable() {
        return recordRepository != null && sessionRepository != null;
    }

    public String createSession() {
        return createSession(null, null);
    }

    public String createSession(String title, String operationType) {
        return createSession(UUID.randomUUID().toString(), title, operationType);
    }

    /** 使用指定 sessionId 创建会话（用于微信等外部渠道绑定固定会话） */
    public String createSession(String sessionId, String title, String operationType) {
        // 如果 DB 中已存在（服务重启后），不覆盖，直接恢复到内存
        sessions.put(sessionId, new ArrayDeque<>());

        if (isDbAvailable()) {
            try {
                boolean exists = sessionRepository.findBySessionId(sessionId).isPresent();
                if (!exists) {
                    ConversationSession session = ConversationSession.builder()
                            .sessionId(sessionId)
                            .title(title)
                            .operationType(operationType)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .messageCount(0)
                            .build();
                    sessionRepository.save(session);
                } else {
                    // 恢复：更新 title + updatedAt
                    sessionRepository.findBySessionId(sessionId).ifPresent(s -> {
                        if (title != null) s.setTitle(title);
                        s.setUpdatedAt(LocalDateTime.now());
                        sessionRepository.save(s);
                    });
                }
            } catch (Exception e) {
                log.warn("DB session save failed: {}", e.getMessage());
            }
        }

        log.debug("Session created: {} type={}", sessionId, operationType);
        return sessionId;
    }

    public void addMessage(String sessionId, String role, String content) {
        addMessageInternal(sessionId, role, content, null, null, null);
    }

    public void addMessage(String sessionId, String role, String content, String modelUsed, long processingTimeMs) {
        addMessageInternal(sessionId, role, content, modelUsed, processingTimeMs, null);
    }

    private void addMessageInternal(String sessionId, String role, String content,
                                     String modelUsed, Long processingTimeMs, String operationType) {
        Deque<ConversationMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        ConversationMessage msg = ConversationMessage.builder()
                .role(role)
                .content(content)
                .timestamp(Instant.now().toEpochMilli())
                .build();
        history.addLast(msg);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }

        if (isDbAvailable()) {
            try {
                ConversationRecord record = ConversationRecord.builder()
                        .sessionId(sessionId)
                        .role(role)
                        .content(content)
                        .modelUsed(modelUsed)
                        .processingTimeMs(processingTimeMs)
                        .operationType(operationType)
                        .createdAt(LocalDateTime.now())
                        .build();
                recordRepository.save(record);

                sessionRepository.findBySessionId(sessionId).ifPresent(s -> {
                    s.setMessageCount(s.getMessageCount() != null ? s.getMessageCount() + 1 : 1);
                    s.setUpdatedAt(LocalDateTime.now());
                    sessionRepository.save(s);
                });
            } catch (Exception e) {
                log.warn("DB message save failed: {}", e.getMessage());
            }
        }
    }

    public void addToolMessage(String sessionId, String role, String content,
                                String toolName, String toolResult) {
        Deque<ConversationMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        ConversationMessage msg = ConversationMessage.builder()
                .role(role)
                .content(content)
                .toolName(toolName)
                .toolResult(toolResult)
                .timestamp(Instant.now().toEpochMilli())
                .build();
        history.addLast(msg);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }

        if (isDbAvailable()) {
            try {
                ConversationRecord record = ConversationRecord.builder()
                        .sessionId(sessionId)
                        .role(role)
                        .content(content)
                        .toolName(toolName)
                        .toolResult(toolResult)
                        .createdAt(LocalDateTime.now())
                        .build();
                recordRepository.save(record);

                sessionRepository.findBySessionId(sessionId).ifPresent(s -> {
                    s.setMessageCount(s.getMessageCount() != null ? s.getMessageCount() + 1 : 1);
                    s.setUpdatedAt(LocalDateTime.now());
                    sessionRepository.save(s);
                });
            } catch (Exception e) {
                log.warn("DB tool message save failed: {}", e.getMessage());
            }
        }
    }

    public List<ConversationMessage> getHistory(String sessionId) {
        Deque<ConversationMessage> history = sessions.get(sessionId);
        if ((history == null || history.isEmpty()) && isDbAvailable()) {
            try {
                List<ConversationRecord> records = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                if (!records.isEmpty()) {
                    // 从 DB 恢复 → 同时填充内存 deque（避免 addMessage 覆盖）
                    Deque<ConversationMessage> restored = new ArrayDeque<>();
                    for (ConversationRecord r : records) {
                        ConversationMessage msg = ConversationMessage.builder()
                                .role(r.getRole())
                                .content(r.getContent())
                                .toolName(r.getToolName())
                                .toolResult(r.getToolResult())
                                .timestamp(r.getCreatedAt() != null
                                        ? r.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        : Instant.now().toEpochMilli())
                                .build();
                        restored.addLast(msg);
                    }
                    sessions.put(sessionId, restored);
                    return new ArrayList<>(restored);
                }
            } catch (Exception e) {
                log.warn("DB history read failed: {}", e.getMessage());
            }
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    public List<Map<String, String>> getHistoryForLLM(String sessionId) {
        List<ConversationMessage> history = getHistory(sessionId);
        List<Map<String, String>> result = new ArrayList<>();
        for (ConversationMessage msg : history) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole() != null ? msg.getRole() : "user");
            if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }
            if (msg.getToolResult() != null) {
                m.put("content", "工具 " + msg.getToolName() + " 返回: " + msg.getToolResult());
            }
            result.add(m);
        }
        return result;
    }

    @Transactional
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        if (isDbAvailable()) {
            try {
                recordRepository.deleteBySessionId(sessionId);
                sessionRepository.deleteBySessionId(sessionId);
            } catch (Exception e) {
                log.warn("DB session clear failed: {}", e.getMessage());
            }
        }
        log.debug("Session cleared: {}", sessionId);
    }

    public boolean sessionExists(String sessionId) {
        if (sessions.containsKey(sessionId)) return true;
        // 内存中没有，查 DB（服务重启后恢复）
        if (isDbAvailable()) {
            try {
                return sessionRepository.findBySessionId(sessionId).isPresent();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public String getContextSummary(String sessionId) {
        List<ConversationMessage> history = getHistory(sessionId);
        if (history.isEmpty()) {
            return "New session, no context.";
        }
        StringBuilder sb = new StringBuilder("Session Context:\n");
        for (ConversationMessage msg : history) {
            sb.append("- [").append(msg.getRole()).append("] ");
            String content = msg.getContent();
            if (content != null && content.length() > 100) {
                content = content.substring(0, 100) + "...";
            }
            sb.append(content).append("\n");
        }
        sb.append("Total ").append(history.size()).append(" messages");
        return sb.toString();
    }

    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        if (isDbAvailable()) {
            try {
                sessionRepository.findBySessionId(sessionId).ifPresent(s -> {
                    s.setTitle(title);
                    sessionRepository.save(s);
                });
            } catch (Exception e) {
                log.warn("DB title update failed: {}", e.getMessage());
            }
        }
    }

    public List<Map<String, Object>> getSessionList() {
        if (!isDbAvailable()) return Collections.emptyList();
        try {
            List<ConversationSession> sessions = sessionRepository.findAllByOrderByUpdatedAtDesc();
            return mapSessionList(sessions);
        } catch (Exception e) {
            log.warn("Session list query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getSessionList(String operationType) {
        if (!isDbAvailable() || operationType == null) return getSessionList();
        try {
            List<ConversationSession> sessions = sessionRepository.findByOperationTypeOrderByUpdatedAtDesc(operationType);
            return mapSessionList(sessions);
        } catch (Exception e) {
            return getSessionList();
        }
    }

    private List<Map<String, Object>> mapSessionList(List<ConversationSession> sessions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConversationSession s : sessions) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("sessionId", s.getSessionId());
            info.put("title", s.getTitle() != null ? s.getTitle() : "Untitled");
            info.put("operationType", s.getOperationType());
            info.put("messageCount", s.getMessageCount() != null ? s.getMessageCount() : 0);
            info.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
            info.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : "");
            result.add(info);
        }
        log.debug("会话列表查询: {} 条", result.size());
        return result;
    }

    public List<ConversationRecord> getDBHistory(String sessionId) {
        if (!isDbAvailable()) return Collections.emptyList();
        try {
            return recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        } catch (Exception e) {
            log.warn("DB history query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
