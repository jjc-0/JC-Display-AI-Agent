package com.ecommerce.agent.tool;

import com.ecommerce.agent.model.v2.Customer;
import com.ecommerce.agent.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * CRM 集成工具 — 客户状态管理、记录更新
 *
 * 操作:
 * - 创建/更新客户记录
 * - 更新客户状态 (NEW → CONTACTED → NEGOTIATING → WON → LOST)
 * - 设置跟进计划
 * - 记录沟通日志
 */
@Slf4j
@Component
public class CRMIntegrationTool implements Tool {

    private final CustomerRepository customerRepo;

    public CRMIntegrationTool(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    @Override
    public String getName() {
        return "update_customer_status";
    }

    @Override
    public String getDescription() {
        return "管理CRM客户记录。更新客户状态(NEW→CONTACTED→NEGOTIATING→WON→LOST)、设置下一次跟进时间、记录AI分析备注。输入客户ID和新状态即可。";
    }

    @Override
    public String getCategory() {
        return "BUSINESS";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "integer");
        idProp.put("description", "客户ID (从 search_customer 结果中获取)");
        props.put("customer_id", idProp);

        Map<String, Object> statusProp = new LinkedHashMap<>();
        statusProp.put("type", "string");
        statusProp.put("description", "新状态: NEW, CONTACTED, NEGOTIATING, WON, LOST");
        props.put("status", statusProp);

        Map<String, Object> notesProp = new LinkedHashMap<>();
        notesProp.put("type", "string");
        notesProp.put("description", "AI分析备注 (可选)");
        props.put("ai_notes", notesProp);

        Map<String, Object> followUpProp = new LinkedHashMap<>();
        followUpProp.put("type", "string");
        followUpProp.put("description", "下次跟进时间 (ISO格式, 如2026-06-27T10:00:00)");
        props.put("next_follow_up", followUpProp);

        schema.put("properties", props);
        schema.put("required", List.of("customer_id", "status"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Long customerId = Long.valueOf(params.get("customer_id").toString());
                String status = (String) params.getOrDefault("status", "CONTACTED");
                String aiNotes = (String) params.get("ai_notes");
                String followUpStr = (String) params.get("next_follow_up");

                Optional<Customer> opt = customerRepo.findById(customerId);
                if (opt.isEmpty()) {
                    return "❌ 客户不存在: ID=" + customerId;
                }

                Customer customer = opt.get();
                String oldStatus = customer.getStatus();

                // 更新状态
                customer.setStatus(status);
                customer.setLastContactAt(LocalDateTime.now());

                if (aiNotes != null && !aiNotes.isBlank()) {
                    customer.setAiNotes(
                            (customer.getAiNotes() != null ? customer.getAiNotes() + "\n" : "")
                                    + "[" + LocalDateTime.now() + "] " + aiNotes);
                }

                if (followUpStr != null && !followUpStr.isBlank()) {
                    try {
                        customer.setNextFollowUpAt(LocalDateTime.parse(followUpStr));
                    } catch (Exception e) {
                        // 默认3天后跟进
                        customer.setNextFollowUpAt(LocalDateTime.now().plusDays(3));
                    }
                }

                customerRepo.save(customer);

                return String.format("""
                        ✅ CRM 更新成功
                        
                        客户: %s (ID: %d)
                        状态变更: %s → %s
                        上次联系: %s
                        下次跟进: %s
                        %s
                        """,
                        customer.getName(), customerId,
                        oldStatus, status,
                        LocalDateTime.now().toString(),
                        customer.getNextFollowUpAt() != null ? customer.getNextFollowUpAt().toString() : "未设置",
                        aiNotes != null ? "备注: " + aiNotes : ""
                );

            } catch (Exception e) {
                log.error("CRM更新失败", e);
                return "❌ CRM 更新失败: " + e.getMessage();
            }
        });
    }
}
