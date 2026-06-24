package com.ecommerce.agent.tool;

import com.ecommerce.agent.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 客户搜索工具 — 搜索潜在外贸客户
 * 支持按关键词、国家、行业搜索已有的客户数据库
 */
@Slf4j
@Component
public class CustomerSearchTool implements Tool {

    private final CustomerRepository customerRepo;

    public CustomerSearchTool(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    @Override
    public String getName() {
        return "search_customer";
    }

    @Override
    public String getDescription() {
        return "搜索外贸客户数据库。按关键词(公司名/联系人/官网)、国家、行业条件查找匹配的客户。用于快速定位已有客户或发现相关潜在客户。";
    }

    @Override
    public String getCategory() {
        return "INFO";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> kwProp = new LinkedHashMap<>();
        kwProp.put("type", "string");
        kwProp.put("description", "搜索关键词 (公司名/联系人/官网域名)");
        props.put("keyword", kwProp);

        Map<String, Object> countryProp = new LinkedHashMap<>();
        countryProp.put("type", "string");
        countryProp.put("description", "目标国家代码 (US, UK, DE, JP等)");
        props.put("country", countryProp);

        Map<String, Object> industryProp = new LinkedHashMap<>();
        industryProp.put("type", "string");
        industryProp.put("description", "行业 (retail, fmcg, electronics等)");
        props.put("industry", industryProp);

        Map<String, Object> statusProp = new LinkedHashMap<>();
        statusProp.put("type", "string");
        statusProp.put("description", "客户状态筛选 (NEW, CONTACTED, NEGOTIATING, WON, LOST)");
        props.put("status", statusProp);

        schema.put("properties", props);
        schema.put("required", List.of("keyword"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String keyword = (String) params.getOrDefault("keyword", "");
            String country = (String) params.get("country");
            String industry = (String) params.get("industry");
            String status = (String) params.get("status");

            List<com.ecommerce.agent.model.v2.Customer> results;

            if (keyword != null && !keyword.isBlank()) {
                results = customerRepo.search(keyword);
            } else if (country != null) {
                results = customerRepo.findByCountryOrderByNameAsc(country);
            } else if (industry != null && status != null) {
                results = customerRepo.findByIndustryAndStatus(industry, status);
            } else if (status != null) {
                results = customerRepo.findByStatusOrderByUpdatedAtDesc(status);
            } else {
                results = List.of();
            }

            // 后置过滤
            if (country != null && !country.isBlank()) {
                results = results.stream()
                        .filter(c -> country.equalsIgnoreCase(c.getCountry()))
                        .toList();
            }
            if (industry != null && !industry.isBlank()) {
                results = results.stream()
                        .filter(c -> industry.equalsIgnoreCase(c.getIndustry()))
                        .toList();
            }

            if (results.isEmpty()) {
                return "未找到匹配的客户。建议: 1) 试用其他关键词 2) 调用 crawl_website 工具从网络发现新客户 3) 检查搜索条件是否过严";
            }

            StringBuilder sb = new StringBuilder("🔍 客户搜索结果: " + results.size() + " 条\n\n");
            int max = Math.min(results.size(), 15);
            for (int i = 0; i < max; i++) {
                var c = results.get(i);
                sb.append(i + 1).append(". ").append(c.getName()).append("\n");
                sb.append("   国家: ").append(nullToStr(c.getCountry())).append(" | 行业: ").append(nullToStr(c.getIndustry())).append("\n");
                sb.append("   状态: ").append(c.getStatus()).append(" | 官网: ").append(nullToStr(c.getWebsite())).append("\n");
                sb.append("   联系人: ").append(nullToStr(c.getContactName())).append(" | 邮箱: ").append(nullToStr(c.getContactEmail())).append("\n");
                sb.append("   产品偏好: ").append(nullToStr(c.getProductPreferences())).append("\n\n");
            }
            if (results.size() > max) {
                sb.append("...还有 ").append(results.size() - max).append(" 条结果未显示。请缩小搜索范围。\n");
            }
            return sb.toString();
        });
    }

    private String nullToStr(String s) { return s != null ? s : "未知"; }
}
