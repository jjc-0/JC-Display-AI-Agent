package com.ecommerce.agent.tool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * v2 工具接口 — 支持分类、超时、条件启用
 */
public interface Tool {

    /** 工具唯一名称 (如 search_customer, generate_email) */
    String getName();

    /** 工具描述 (给 LLM 理解何时调用) */
    String getDescription();

    /** 参数 JSON Schema */
    Map<String, Object> getParametersSchema();

    /** 执行工具 */
    CompletableFuture<String> execute(Map<String, Object> params);

    /**
     * 工具分类:
     * - INFO:       信息类 (搜索、爬取)
     * - ANALYSIS:   分析类 (评分、分类)
     * - GENERATION: 生成类 (文案、邮件、图片)
     * - BUSINESS:   业务类 (CRM、状态更新)
     * - MULTIMODAL: 多模态 (识图、生图)
     */
    default String getCategory() {
        return "INFO";
    }

    /** 是否启用 (默认 true) */
    default boolean isEnabled() {
        return true;
    }

    /** 执行超时(毫秒) */
    default long getTimeoutMs() {
        return 30000;
    }

    /** 是否需要在执行前确认 (高危操作用) */
    default boolean requiresConfirmation() {
        return false;
    }
}
