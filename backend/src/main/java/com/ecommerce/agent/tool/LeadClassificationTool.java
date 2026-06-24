package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 询盘/客户分析工具 — 分类客户意向、评估质量
 *
 * 分析维度:
 * - 客户意图 (购买/询价/合作/投诉)
 * - 紧急程度 (低/中/高)
 * - 客户质量 (A/B/C级)
 * - 购买阶段 (认知/考虑/决策)
 */
@Slf4j
@Component
public class LeadClassificationTool implements Tool {

    @Override
    public String getName() {
        return "analyze_lead";
    }

    @Override
    public String getDescription() {
        return "分析客户询盘或客户信息，评估客户质量、意图分类、购买阶段。输入客户信息或询盘文本，返回结构化分析结果。";
    }

    @Override
    public String getCategory() {
        return "ANALYSIS";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> contentProp = new LinkedHashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要分析的询盘文本或客户信息");
        props.put("content", contentProp);

        Map<String, Object> customerNameProp = new LinkedHashMap<>();
        customerNameProp.put("type", "string");
        customerNameProp.put("description", "客户名称/公司名 (可选)");
        props.put("customer_name", customerNameProp);

        Map<String, Object> countryProp = new LinkedHashMap<>();
        countryProp.put("type", "string");
        countryProp.put("description", "客户所在国家 (可选, 用于市场适配分析)");
        props.put("country", countryProp);

        schema.put("properties", props);
        schema.put("required", List.of("content"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String content = (String) params.getOrDefault("content", "");
            String customerName = (String) params.getOrDefault("customer_name", "匿名");
            String country = (String) params.getOrDefault("country", "未知");

            // 基于启发式规则做初步分析 (实际应由 LLM 驱动)
            int score = computeScore(content);
            String intent = classifyIntent(content);
            String stage = classifyStage(content, score);
            String tier = score >= 80 ? "A" : score >= 60 ? "B" : "C";
            String urgency = content.length() > 300 || containsKeywords(content,
                    "urgent", "尽快", "ASAP", "immediately") ? "高" : score > 60 ? "中" : "低";

            return String.format("""
                    📊 客户分析报告
                    
                    客户: %s | 国家: %s
                    
                    ## 评分: %d/100
                    - 客户等级: %s级
                    - 意图分类: %s
                    - 购买阶段: %s
                    - 紧急程度: %s
                    
                    ## 建议行动
                    %s
                    
                    ## 关键信号
                    %s
                    
                    💡 提示: 使用 generate_email 工具为此客户生成开发信。
                    """,
                    customerName, country,
                    score, tier, intent, stage, urgency,
                    generateAction(tier, stage),
                    extractSignals(content)
            );
        });
    }

    private int computeScore(String content) {
        int score = 30;
        if (content.length() > 100) score += 10;
        if (content.length() > 300) score += 10;
        if (containsKeywords(content, "price", "报价", "cost", "价格", "MOQ", "起订")) score += 15;
        if (containsKeywords(content, "order", "订单", "purchase", "采购", "批量")) score += 20;
        if (containsKeywords(content, "sample", "样品", "test", "合作", "partner")) score += 10;
        if (containsKeywords(content, "factory", "工厂", "manufacturer", "OEM", "custom")) score += 10;
        if (content.toLowerCase().contains("thank") || content.contains("谢谢")) score += 5;
        return Math.min(score, 100);
    }

    private String classifyIntent(String content) {
        if (containsKeywords(content, "order", "purchase", "buy", "采购", "批量", "集装箱"))
            return "批量采购意向";
        if (containsKeywords(content, "price", "quote", "报价", "价格", "FOB"))
            return "价格询价";
        if (containsKeywords(content, "sample", "样品", "test order", "trial"))
            return "样品请求";
        if (containsKeywords(content, "cooperat", "合作", "partner", "distributor", "代理"))
            return "合作意向";
        if (containsKeywords(content, "complaint", "投诉", "problem", "问题", "broken"))
            return "售后问题";
        return "产品咨询";
    }

    private String classifyStage(String content, int score) {
        if (score >= 80) return "决策阶段";
        if (score >= 60) return "比较询价";
        return "初步意向";
    }

    private String generateAction(String tier, String stage) {
        return switch (tier) {
            case "A" -> "⚠️ 高优先级: 24小时内回复, 提供详细报价+3D设计, 安排视频会议";
            case "B" -> "📌 中优先级: 48小时内回复, 发产品目录, 了解具体需求";
            default -> "📎 常规跟进: 发送标准产品介绍, 加入长期培育列表";
        };
    }

    private String extractSignals(String content) {
        StringBuilder sb = new StringBuilder();
        if (containsKeywords(content, "USA", "UK", "Germany", "日本", "美国", "德国", "英国"))
            sb.append("- ✅ 目标市场需求明确\n");
        if (containsKeywords(content, "长期", "long term", "yearly", "annual"))
            sb.append("- ✅ 有长期合作意愿\n");
        if (containsKeywords(content, "urgent", "尽快", "ASAP", "急"))
            sb.append("- ⚡ 客户有紧迫需求\n");
        if (content.length() < 50)
            sb.append("- ⚠️ 询盘信息太少, 建议追问更多细节\n");
        if (sb.isEmpty()) sb.append("- 中等信号强度, 建议进一步沟通了解需求\n");
        return sb.toString();
    }

    private boolean containsKeywords(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
