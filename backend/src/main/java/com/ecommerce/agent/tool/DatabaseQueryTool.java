package com.ecommerce.agent.tool;

import com.ecommerce.agent.model.ConversationRecord;
import com.ecommerce.agent.model.v2.Customer;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.ecommerce.agent.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 数据库查询工具 — Agent 访问业务数据的统一入口
 *
 * 让 LLM 能查询:
 * - 对话记录 (按时间/会话/关键词)
 * - 客户数据 (按国家/状态/行业/关键词)
 * - 客户对话关联查询
 *
 * 这才是真正的 Tool —— 不是换 prompt，而是查真实数据库
 */
@Slf4j
@Component
public class DatabaseQueryTool implements Tool {

    private final ConversationRecordRepository msgRepo;
    private final CustomerRepository customerRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseQueryTool(ConversationRecordRepository msgRepo,
                              CustomerRepository customerRepo) {
        this.msgRepo = msgRepo;
        this.customerRepo = customerRepo;
    }

    @Override public String getName() { return "database_query"; }
    @Override public String getCategory() { return "INFO"; }
    @Override public long getTimeoutMs() { return 15000; }

    @Override
    public boolean isEnabled() {
        return msgRepo != null;
    }

    @Override
    public String getDescription() {
        return "数据库查询工具。查询对话记录和客户数据。支持按时间范围、国家、状态等条件过滤。" +
               "返回结构化JSON数据供后续分析。典型用法：汇总对话 → 总结客户反馈 → 生成报告。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        // 查询类型
        Map<String, Object> typeProp = new LinkedHashMap<>();
        typeProp.put("type", "string");
        typeProp.put("description", "查询类型: messages(对话记录) / customers(客户) / customer_messages(某客户对话) / sessions(会话列表)");
        typeProp.put("enum", List.of("messages", "customers", "customer_messages", "sessions"));
        props.put("query_type", typeProp);

        // 时间范围
        Map<String, Object> hoursProp = new LinkedHashMap<>();
        hoursProp.put("type", "number");
        hoursProp.put("description", "最近N小时。例如24=最近一天, 168=最近一周。0表示不限。");
        props.put("hours", hoursProp);

        Map<String, Object> dateFromProp = new LinkedHashMap<>();
        dateFromProp.put("type", "string");
        dateFromProp.put("description", "开始日期 ISO格式，如 2026-06-19T00:00:00");
        props.put("date_from", dateFromProp);

        Map<String, Object> dateToProp = new LinkedHashMap<>();
        dateToProp.put("type", "string");
        dateToProp.put("description", "结束日期 ISO格式");
        props.put("date_to", dateToProp);

        // 过滤条件
        Map<String, Object> keywordProp = new LinkedHashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "消息内容关键词 (仅 messages 类型)");
        props.put("keyword", keywordProp);

        Map<String, Object> countryProp = new LinkedHashMap<>();
        countryProp.put("type", "string");
        countryProp.put("description", "客户国家代码，如 US、DE、UK (仅 customers 类型)");
        props.put("country", countryProp);

        Map<String, Object> statusProp = new LinkedHashMap<>();
        statusProp.put("type", "string");
        statusProp.put("description", "客户状态: NEW/CONTACTED/NEGOTIATING/WON/LOST");
        props.put("status", statusProp);

        Map<String, Object> customerIdProp = new LinkedHashMap<>();
        customerIdProp.put("type", "number");
        customerIdProp.put("description", "客户ID (仅 customer_messages 类型)");
        props.put("customer_id", customerIdProp);

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "number");
        limitProp.put("description", "返回记录数上限，默认50");
        props.put("limit", limitProp);

        schema.put("properties", props);
        schema.put("required", List.of("query_type"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String queryType = (String) params.getOrDefault("query_type", "messages");
                int limit = Math.min(((Number) params.getOrDefault("limit", 50)).intValue(), 200);

                switch (queryType) {
                    case "messages":
                        return queryMessages(params, limit);
                    case "customers":
                        return queryCustomers(params, limit);
                    case "customer_messages":
                        return queryCustomerMessages(params, limit);
                    case "sessions":
                        return querySessions(limit);
                    default:
                        return "{\"error\": \"未知查询类型: " + queryType + "\"}";
                }
            } catch (Exception e) {
                log.error("数据库查询失败", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String queryMessages(Map<String, Object> params, int limit) {
        LocalDateTime start = resolveStartTime(params);
        LocalDateTime end = resolveEndTime(params);
        String keyword = (String) params.get("keyword");

        List<ConversationRecord> records;
        if (start != null && end != null) {
            records = msgRepo.findByDateRange(start, end);
        } else {
            records = msgRepo.findAll();
        }

        // 内存过滤
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            records = records.stream()
                    .filter(r -> r.getContent() != null && r.getContent().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }

        // 去重 session
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (ConversationRecord r : records) {
            if (grouped.size() >= limit) break;
            grouped.computeIfAbsent(r.getSessionId(), k -> new ArrayList<>())
                    .add(toMap(r));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("session_id", entry.getKey());
            session.put("message_count", entry.getValue().size());
            // 只返回每条消息的摘要
            List<Map<String, Object>> msgs = entry.getValue().stream()
                    .limit(30)
                    .map(m -> {
                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("role", m.get("role"));
                        summary.put("time", m.get("time"));
                        String content = (String) m.get("content");
                        summary.put("content", content != null && content.length() > 200
                                ? content.substring(0, 200) + "..." : content);
                        summary.put("tool", m.get("tool"));
                        return summary;
                    })
                    .collect(Collectors.toList());
            session.put("messages", msgs);
            result.add(session);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total_sessions", result.size());
        response.put("time_range", (start != null ? start.toString() : "all") + " ~ " + (end != null ? end.toString() : "now"));
        response.put("sessions", result);

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private String queryCustomers(Map<String, Object> params, int limit) {
        String country = (String) params.get("country");
        String status = (String) params.get("status");

        List<Customer> customers;
        if (country != null && status != null) {
            customers = customerRepo.findByIndustryAndStatus(country, status);
        } else if (country != null) {
            customers = customerRepo.findByCountryOrderByNameAsc(country);
        } else if (status != null) {
            customers = customerRepo.findByStatusOrderByUpdatedAtDesc(status);
        } else {
            customers = customerRepo.findAll();
        }

        List<Map<String, Object>> result = customers.stream()
                .limit(limit)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("country", c.getCountry());
                    m.put("status", c.getStatus());
                    m.put("industry", c.getIndustry());
                    m.put("contact", c.getContactName());
                    m.put("email", c.getContactEmail());
                    m.put("source", c.getSource());
                    m.put("tier", c.getTier());
                    if (c.getLastContactAt() != null)
                        m.put("last_contact", c.getLastContactAt().toString());
                    if (c.getNextFollowUpAt() != null)
                        m.put("next_followup", c.getNextFollowUpAt().toString());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.size());
        response.put("customers", result);

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    private String queryCustomerMessages(Map<String, Object> params, int limit) {
        Number cid = (Number) params.get("customer_id");
        if (cid == null) return "{\"error\":\"缺少 customer_id 参数\"}";

        // 通过客户名关联 session (简化: 搜索所有消息中含客户名的)
        Optional<Customer> customer = customerRepo.findById(cid.longValue());
        if (customer.isEmpty()) return "{\"error\":\"客户不存在: " + cid + "\"}";

        Customer c = customer.get();
        List<ConversationRecord> all = msgRepo.findByDateRange(
                c.getCreatedAt(), LocalDateTime.now());
        List<Map<String, Object>> msgs = all.stream()
                .filter(r -> r.getContent() != null &&
                        (r.getContent().contains(c.getName()) ||
                         c.getContactEmail() != null && r.getContent().contains(c.getContactEmail())))
                .limit(limit)
                .map(this::toMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer_name", c.getName());
        response.put("customer_country", c.getCountry());
        response.put("message_count", msgs.size());
        response.put("messages", msgs);

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    private String querySessions(int limit) {
        List<ConversationRecord> all = msgRepo.findAll();
        Map<String, Map<String, Object>> sessions = new LinkedHashMap<>();

        for (ConversationRecord r : all) {
            if (sessions.size() >= limit && !sessions.containsKey(r.getSessionId())) break;
            sessions.computeIfAbsent(r.getSessionId(), k -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("session_id", k);
                s.put("message_count", 0);
                s.put("first_time", r.getCreatedAt().toString());
                return s;
            });
            Map<String, Object> s = sessions.get(r.getSessionId());
            s.put("message_count", ((Integer) s.get("message_count")) + 1);
            if (r.getCreatedAt() != null) s.put("last_time", r.getCreatedAt().toString());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", sessions.size());
        response.put("sessions", sessions.values());

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    // ═══════════════════════════════════════════════════════════════

    private LocalDateTime resolveStartTime(Map<String, Object> params) {
        String dateFrom = (String) params.get("date_from");
        if (dateFrom != null && !dateFrom.isBlank()) {
            return LocalDateTime.parse(dateFrom, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        Number hours = (Number) params.get("hours");
        if (hours != null && hours.intValue() > 0) {
            return LocalDateTime.now().minusHours(hours.longValue());
        }
        // 默认最近 24 小时
        return LocalDateTime.now().minusHours(24);
    }

    private LocalDateTime resolveEndTime(Map<String, Object> params) {
        String dateTo = (String) params.get("date_to");
        if (dateTo != null && !dateTo.isBlank()) {
            return LocalDateTime.parse(dateTo, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return LocalDateTime.now();
    }

    private Map<String, Object> toMap(ConversationRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("session_id", r.getSessionId());
        m.put("role", r.getRole());
        m.put("content", r.getContent());
        m.put("tool", r.getToolName());
        m.put("time", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return m;
    }
}
