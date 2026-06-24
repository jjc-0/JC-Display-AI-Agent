package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件生成工具 — 为外贸客户自动生成开发信、报价邮件、跟进邮件
 *
 * 支持类型:
 * - 开发信 (cold outreach)
 * - 报价邮件 (quote email)
 * - 跟进邮件 (follow-up)
 * - 样品确认 (sample confirmation)
 */
@Slf4j
@Component
public class EmailGenerationTool implements Tool {

    @Override
    public String getName() {
        return "generate_email";
    }

    @Override
    public String getDescription() {
        return "生成外贸邮件模板。支持冷开发信(cold)、报价邮件(quote)、跟进邮件(followup)、样品确认(sample)。根据客户信息和目的自动选择合适的语气和内容结构。";
    }

    @Override
    public String getCategory() {
        return "GENERATION";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> typeProp = new LinkedHashMap<>();
        typeProp.put("type", "string");
        typeProp.put("description", "邮件类型: cold(开发信), quote(报价), followup(跟进), sample(样品确认)");
        props.put("type", typeProp);

        Map<String, Object> customerProp = new LinkedHashMap<>();
        customerProp.put("type", "string");
        customerProp.put("description", "客户公司名");
        props.put("customer_name", customerProp);

        Map<String, Object> contactProp = new LinkedHashMap<>();
        contactProp.put("type", "string");
        contactProp.put("description", "联系人姓名 (可选)");
        props.put("contact_name", contactProp);

        Map<String, Object> productProp = new LinkedHashMap<>();
        productProp.put("type", "string");
        productProp.put("description", "产品名称");
        props.put("product", productProp);

        Map<String, Object> extraProp = new LinkedHashMap<>();
        extraProp.put("type", "string");
        extraProp.put("description", "额外信息 (如报价金额、交期、特殊要求等)");
        props.put("extra_info", extraProp);

        schema.put("properties", props);
        schema.put("required", List.of("type", "customer_name", "product"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String type = (String) params.getOrDefault("type", "cold");
            String customerName = (String) params.getOrDefault("customer_name", "Dear Sir/Madam");
            String contactName = (String) params.getOrDefault("contact_name", "");
            String product = (String) params.getOrDefault("product", "display stands");

            String greeting = contactName != null && !contactName.isBlank()
                    ? "Dear " + contactName : "Dear " + customerName + " Team";

            return switch (type.toLowerCase()) {
                case "cold" -> generateColdEmail(greeting, product, customerName);
                case "quote" -> generateQuoteEmail(greeting, product, customerName, params);
                case "followup" -> generateFollowUp(greeting, product, customerName);
                case "sample" -> generateSampleEmail(greeting, product, customerName);
                default -> generateColdEmail(greeting, product, customerName);
            };
        });
    }

    private String generateColdEmail(String greeting, String product, String companyName) {
        return String.format("""
                📧 冷开发信模板
                
                Subject: High-Quality %s Solutions | JC Display Factory Direct
                
                %s,
                
                I hope this message finds you well. I'm reaching out from JC Display (Shenzhen), a professional %s manufacturer with over 17 years of experience serving global brands.
                
                We specialize in producing custom cardboard display stands, POP displays, and retail fixtures for markets worldwide — including the US, UK, Germany, Japan, and Australia.
                
                **Why JC Display?**
                🏭 Own factory — direct pricing, no middleman markup
                🎨 Free 3D design & mockup within 2 working days
                📦 Flat-pack shipping to reduce freight costs by up to 60%%
                ✅ ISO-certified quality with FSC materials available
                
                I would love to learn more about %s's display needs and see how we can support your upcoming projects.
                
                Would you be available for a brief call or email exchange next week?
                
                Best regards,
                JC Display Team
                Shenzhen JC Display Ltd
                Web: www.displaystandpop.com
                
                ---
                💡 提示: 发送前请用 analyze_lead 工具评估客户质量, 以调整邮件策略。
                """, product, greeting, product, companyName);
    }

    private String generateQuoteEmail(String greeting, String product, String companyName,
                                       Map<String, Object> params) {
        String extra = (String) params.getOrDefault("extra_info", "");
        return String.format("""
                📧 报价邮件模板
                
                Subject: Quotation for %s | JC Display — Factory Direct Pricing
                
                %s,
                
                Thank you for your inquiry regarding %s. Please find our detailed quotation below.
                
                **Product: %s**
                
                | Item | Details |
                |------|---------|
                | Material | 300g art paper + B-flute corrugated cardboard |
                | Printing | 4C CMYK offset with gloss/matte lamination |
                | MOQ | 100 pcs per design (negotiable for trials) |
                | Price Range | USD 8-25/pc (varies by size & complexity) |
                | Lead Time | 10-15 working days after design confirmation |
                | Payment | T/T 30%% deposit, 70%% before shipment |
                | Shipping | FOB Shenzhen (CIF/DDP available on request) |
                
                %s
                
                **Next Steps:**
                1. Share your required dimensions & quantity for a precise quote
                2. We'll provide a 3D design within 2 working days
                3. Free sample can be arranged within 3 working days
                
                Looking forward to your feedback!
                
                Best regards,
                JC Display Team
                
                ---
                💡 提示: 若客户48小时未回复, 可用 generate_email type=followup 生成跟进邮件。
                """, product, greeting, product, product,
                extra.isBlank() ? "" : "Additional Info: " + extra);
    }

    private String generateFollowUp(String greeting, String product, String companyName) {
        return String.format("""
                📧 跟进邮件模板
                
                Subject: Following Up — %s Solutions for %s
                
                %s,
                
                I hope you're having a productive week. I wanted to follow up on my previous message regarding %s.
                
                I understand you're likely busy — just wanted to check if you had a chance to review our proposal. We're very interested in exploring how JC Display can support %s's display needs.
                
                We're currently offering:
                - Free 3D design & mockup service for new clients
                - Competitive pricing for Q3/Q4 production slots
                - Flexible MOQ for trial orders
                
                Would this week be a good time for a quick discussion? I'm happy to prepare a custom quote based on your specific requirements.
                
                Looking forward to hearing from you.
                
                Best regards,
                JC Display Team
                
                ---
                💡 提示: 三次跟进无回复建议转为长期培育, 使用 update_customer_status 工具更新状态。
                """, product, companyName, greeting, product, companyName);
    }

    private String generateSampleEmail(String greeting, String product, String companyName) {
        return String.format("""
                📧 样品确认邮件模板
                
                Subject: Sample Confirmation — %s | Request Details
                
                %s,
                
                Great news — we're ready to start your %s sample production!
                
                To ensure we deliver exactly what you need, please confirm the following:
                
                1. **Dimensions**: Required size (L x W x H in mm/inch)
                2. **Design**: Color scheme, logo placement, any reference images
                3. **Quantity**: Number of sample units needed
                4. **Shipping Address**: Complete delivery address with contact person & phone
                5. **Timeline**: Preferred delivery date
                
                **Sample Timeline:**
                - Design confirmation: 1-2 working days
                - Sample production: 2-3 working days
                - Shipping: 3-7 working days (via DHL/FedEx/UPS)
                
                Sample cost will be credited against your bulk order.
                
                Looking forward to bringing your display vision to life!
                
                Best regards,
                JC Display Team
                
                ---
                💡 提示: 样品确认后请用 create_crm_record 工具记录到客户档案。
                """, product, greeting, product);
    }
}
