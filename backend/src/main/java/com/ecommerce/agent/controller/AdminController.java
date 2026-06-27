package com.ecommerce.agent.controller;

import com.ecommerce.agent.model.User;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.ecommerce.agent.repository.ConversationSessionRepository;
import com.ecommerce.agent.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ConversationSessionRepository sessionRepository;
    private final ConversationRecordRepository recordRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository,
                           ConversationSessionRepository sessionRepository,
                           ConversationRecordRepository recordRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(this::toAdminUser)
                .toList();
        return ResponseEntity.ok(Map.of(
                "users", users,
                "stats", adminStats()
        ));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (body.containsKey("role")) {
            String role = clean(body.get("role"), 20).toLowerCase(Locale.ROOT);
            if (!role.equals("admin") && !role.equals("user")) {
                return ResponseEntity.badRequest().body(Map.of("message", "角色只能是 admin 或 user"));
            }
            if ("admin".equalsIgnoreCase(user.getRole()) && "user".equals(role) && userRepository.countByRoleIgnoreCase("admin") <= 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "至少需要保留一个管理员账号"));
            }
            user.setRole(role);
        }
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            if (!enabled && "admin".equalsIgnoreCase(user.getRole()) && userRepository.countByRoleIgnoreCase("admin") <= 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "不能禁用最后一个管理员账号"));
            }
            user.setEnabled(enabled);
        }
        if (body.containsKey("displayName")) {
            user.setDisplayName(clean(body.get("displayName"), 80));
        }
        if (body.containsKey("password")) {
            String password = clean(body.get("password"), 120);
            if (!password.isBlank()) {
                if (password.length() < 6) {
                    return ResponseEntity.badRequest().body(Map.of("message", "密码至少需要 6 位"));
                }
                user.setPassword(passwordEncoder.encode(password));
            }
        }

        userRepository.save(user);
        return ResponseEntity.ok(toAdminUser(user));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        long adminCount = userRepository.countByRoleIgnoreCase("admin");
        if ("admin".equalsIgnoreCase(user.getRole()) && adminCount <= 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "至少需要保留一个管理员账号"));
        }
        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(adminStats());
    }

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> conversations(@RequestParam(required = false) String username) {
        List<Map<String, Object>> sessions = sessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(session -> username == null || username.isBlank() || Objects.equals(username, session.getUsername()))
                .map(session -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sessionId", session.getSessionId());
                    item.put("title", valueOrDefault(session.getTitle(), "未命名会话"));
                    item.put("operationType", valueOrDefault(session.getOperationType(), "chat"));
                    item.put("messageCount", session.getMessageCount() != null ? session.getMessageCount() : 0);
                    item.put("userId", valueOrDefault(session.getUserId(), ""));
                    item.put("username", valueOrDefault(session.getUsername(), "未归属"));
                    item.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
                    item.put("updatedAt", session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null);
                    return item;
                })
                .toList();

        List<Map<String, Object>> records = recordRepository.findAll().stream()
                .filter(record -> username == null || username.isBlank() || Objects.equals(username, record.getUsername()))
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .limit(300)
                .map(record -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", record.getId());
                    item.put("sessionId", record.getSessionId());
                    item.put("role", record.getRole());
                    item.put("operationType", valueOrDefault(record.getOperationType(), "chat"));
                    item.put("userId", valueOrDefault(record.getUserId(), ""));
                    item.put("username", valueOrDefault(record.getUsername(), "未归属"));
                    item.put("content", preview(record.getContent(), 600));
                    item.put("toolName", valueOrDefault(record.getToolName(), ""));
                    item.put("toolResult", preview(record.getToolResult(), 500));
                    item.put("modelUsed", valueOrDefault(record.getModelUsed(), ""));
                    item.put("processingTimeMs", record.getProcessingTimeMs());
                    item.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
                    return item;
                })
                .toList();

        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("username", user.getUsername());
                    item.put("displayName", valueOrDefault(user.getDisplayName(), user.getUsername()));
                    item.put("role", user.getRole());
                    item.put("enabled", user.isEnabled());
                    return item;
                })
                .toList();

        Map<String, Long> sessionsByUser = sessions.stream()
                .collect(Collectors.groupingBy(item -> valueOrDefault((String) item.get("username"), "未归属"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> recordsByUser = records.stream()
                .collect(Collectors.groupingBy(item -> valueOrDefault((String) item.get("username"), "未归属"), LinkedHashMap::new, Collectors.counting()));

        return ResponseEntity.ok(Map.of(
                "users", users,
                "sessions", sessions,
                "records", records,
                "stats", Map.of(
                        "totalSessions", sessions.size(),
                        "totalRecords", records.size(),
                        "toolCalls", records.stream().filter(item -> !valueOrDefault((String) item.get("toolName"), "").isBlank()).count(),
                        "sessionsByUser", sessionsByUser,
                        "recordsByUser", recordsByUser
                )
        ));
    }

    private Map<String, Object> adminStats() {
        long users = userRepository.count();
        long admins = userRepository.countByRoleIgnoreCase("admin");
        long enabledUsers = userRepository.countByEnabledTrue();
        long sessions = sessionRepository.count();
        long records = recordRepository.count();
        long todayRecords = recordRepository.findAll().stream()
                .filter(record -> record.getCreatedAt() != null
                        && record.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", users);
        stats.put("adminUsers", admins);
        stats.put("enabledUsers", enabledUsers);
        stats.put("disabledUsers", Math.max(0, users - enabledUsers));
        stats.put("totalSessions", sessions);
        stats.put("totalRecords", records);
        stats.put("todayRecords", todayRecords);
        return stats;
    }

    private Map<String, Object> toAdminUser(User user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.getId());
        item.put("username", user.getUsername());
        item.put("role", user.getRole());
        item.put("displayName", valueOrDefault(user.getDisplayName(), user.getUsername()));
        item.put("email", valueOrDefault(user.getEmail(), ""));
        item.put("qqEmail", valueOrDefault(user.getQqEmail(), ""));
        item.put("companyName", valueOrDefault(user.getCompanyName(), ""));
        item.put("department", valueOrDefault(user.getDepartment(), ""));
        item.put("jobTitle", valueOrDefault(user.getJobTitle(), ""));
        item.put("phone", valueOrDefault(user.getPhone(), ""));
        item.put("enabled", user.isEnabled());
        item.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        item.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        item.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        return item;
    }

    private String clean(Object value, int maxLength) {
        if (value == null) return "";
        String s = value.toString().trim();
        if (s.length() > maxLength) return s.substring(0, maxLength);
        return s;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String preview(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.length() <= maxLength) return clean;
        return clean.substring(0, maxLength) + "...";
    }
}
