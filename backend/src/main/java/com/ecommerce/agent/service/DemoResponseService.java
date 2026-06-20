package com.ecommerce.agent.service;

import com.ecommerce.agent.util.CountryLanguageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DemoResponseService {

    public String generateCopywritingDemo(String productName, String sellingPoints, String platform, String targetCountry) {
        String country = CountryLanguageUtil.resolveCountryName(targetCountry);
        String lang = CountryLanguageUtil.resolveLanguage(targetCountry);
        String productType = sellingPoints != null && !sellingPoints.isBlank() ? sellingPoints : "cardboard display stand";

        if ("alibaba".equalsIgnoreCase(platform) || "bulk".equalsIgnoreCase(platform)) {
            return String.format("""
                    🏭 Alibaba B2B 产品详情 (演示模式)

                    ## Product Title
                    Custom Wholesale %s | High Quality %s for Supermarket Retail Display | Direct Factory Price

                    ## Key Attributes
                    • ✅ Material: 300G art paper + B flute corrugated cardboard
                    • ✅ Printing: 4C CMYK offset printing with high glossy finish
                    • ✅ MOQ: 100 pcs per design (negotiable)
                    • ✅ Custom Design: Free 3D design & mockup before production
                    • ✅ Certification: Factory audited, quality compliant

                  ## Product Description
                  Shenzhen JC Display Ltd is a professional POP display manufacturer with industry experience. Our %s is designed for %s market retailers and brands who need eye-catching in-store displays.

                  **Why Choose JC Display:**
                  - 🏭 Direct factory price with competitive advantage
                  - 📦 Flat pack delivery for shipping cost optimization
                  - 🎨 Free custom design service (size, color, printing)
                  - ⏱ Efficient production lead time
                  - 🌍 Export to global markets (US, UK, EU, AU, JP)

                    **Specifications:**
                    | Item | Details |
                    |------|---------|
                    | Material | 300g coated paper + B/C/E flute corrugated |
                    | Printing | 4C offset printing |
                    | Surface | High gloss / matte lamination |
                    | Packing | Flat pack, 10 pcs/carton |
                    | Custom | Size, shape, color, printing fully customizable |

                    ## Trade Shows We Attend
                    Canton Fair, Global Sources, Ambiente Frankfurt, EuroShop

                    ---
                    📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI 生成文案。
                    编辑 application-secrets.yml 中的 DEEPSEEK_API_KEY 即可。
                    """,
                    productName, productType, country,
                    productType, country);
        }

        if ("email".equalsIgnoreCase(platform) || "inquiry".equalsIgnoreCase(platform)) {
            return String.format("""
                    ✉️ 询盘回复邮件 (演示模式)

                    Subject: Re: Inquiry about %s | JC Display Factory Direct Quote

                    Dear [Customer Name],

                    Thank you for your interest in our %s. We are JC Display (Shenzhen), a professional cardboard POP display manufacturer with 17+ years of experience.

                    **Quick Quote:**
                    - Product: %s
                    - Material: 300G art paper + B flute corrugated
                    - MOQ: 100 pcs per design
                    - Unit Price: USD 8-25/pc (depends on size & complexity)
                    - Production Time: 10-12 working days
                    - Shipping: FOB Shenzhen / EXW / DDP available

                    **Services We Provide:**
                    1. ✅ Free 3D design & mockup within 2 days
                    2. ✅ Free sample within 3 working days
                    3. ✅ OEM/ODM fully supported
                    4. ✅ Flat pack for cost-effective shipping

                    Please let me know:
                    - Required dimensions?
                    - Estimated order quantity?
                    - Any special printing requirements?

                    Looking forward to working with you!

                    Best regards,
                    JC Display Team
                    Shenzhen JC Display Ltd
                    Web: www.displaystandpop.com
                    Email: sales@displaystandpop.com

                    ---
                    📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI 生成邮件。
                    """,
                    productName, productName, productName);
        }

        return String.format("""
                🏭 POP 展示架产品文案 (演示模式)

                ## Product Title
                Custom %s | Wholesale Cardboard POS Display Stand | JC Display Factory

                ## Bullet Points
                • ✅ DIRECT FACTORY - 17 years experience, ISO certified, save 30%% cost
                • ✅ FULLY CUSTOMIZABLE - Size, color, printing, shape all per your design
                • ✅ HIGH QUALITY PRINTING - 4C offset print + gloss/UV lamination available
                • ✅ FLAT PACK SHIPPING - Reduces freight cost by 60%%
                • ✅ FREE DESIGN SERVICE - 3D rendering within 2 working days

                ## Product Description
                JC Display presents our premium %s, ideal for %s supermarkets, retail stores, and trade shows. Made from durable corrugated cardboard with professional offset printing, this display delivers maximum visual impact and product visibility.

                ## FAQ
                Q: What's the MOQ?
                A: 100 pcs per design. Sample order available.

                Q: Can you do custom design?
                A: Yes! Free 3D mockup before production.

                Q: How is it shipped?
                A: Flat packed to minimize freight cost. FOB Shenzhen port.

                ---
                📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI 生成文案。
                编辑 application-secrets.yml 中的 DEEPSEEK_API_KEY 即可。
                """,
                productName, productType, country);
    }

    public String generateTranslationDemo(String text, String source, String target) {
        return String.format("""
                🌐 翻译结果 (演示模式)

                原文 (%s): %s

                译文 (%s): [%s translation of the above text would appear here]

                ## 本地化说明
                - 已将尺寸单位转换为目标市场标准 (mm↔inch)
                - 已适配目标市场的展示架行业表达习惯
                - 保留了专业技术术语的准确性

                ---
                📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI 翻译。
                编辑 application-secrets.yml 中的 DEEPSEEK_API_KEY 即可。
                """, source, text, target, target);
    }

    public String generateAnalysisDemo(String productName, String targetCountry) {
        String country = CountryLanguageUtil.resolveCountryName(targetCountry);
        return String.format("""
                📊 展示架市场分析报告 (演示模式)

                ## 产品: %s | 目标市场: %s

                ## 1. 市场需求分析
                - %s零售市场规模庞大，超市/便利店/药妆店对POP展示架需求持续增长
                - 环保趋势下，纸质展示架逐步替代塑料/PVC展架
                - FMCG (快消品) 品牌商的季度促销活动驱动稳定需求

                ## 2. 竞争格局
                - 主要出口竞争国: 中国(60%%)、土耳其(10%%)、波兰(8%%)
                - 中国供应商优势: 价格竞争力、交货速度快、定制灵活
                - JC Display差异化: 17年行业经验、免费设计、ISO认证

                ## 3. 合规与准入
                - %s市场要求符合FSC环保认证、SGS材质检测
                - 瓦楞纸板需符合ISTA运输包装认证
                - 油墨需符合ROHS/EN71环保标准

                ## 4. 物流与关税建议
                - 推荐FOB深圳或FCA
                - 平摊包装(Flat Pack)可节省60%%运费
                - 建议提前3个月备货应对Q4旺季

                ## 5. 综合建议
                - ✅ 推荐进入: 市场需求强，纸质展示架趋势利好
                - 定价策略: 建议$8-25/pc (根据尺寸和复杂度)
                - 展会推荐: EuroShop、GlobalShop、POPAI展

                ---
                📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI 市场分析。
                编辑 application-secrets.yml 中的 DEEPSEEK_API_KEY 即可。
                """, productName, country, country, country);
    }

    public String generateSEODemo(String url, String keywords, String targetCountry) {
        String country = CountryLanguageUtil.resolveCountryName(targetCountry);
        String kw = keywords != null && !keywords.isBlank() ? keywords : "display stand, cardboard display, POP display";
        return String.format("""
                🔧 SEO优化报告 (演示模式)

                ## 页面: %s | 目标市场: %s

                ## 1. On-Page SEO评估
                - ❌ Title Tag长度优化 (建议40-60字符)
                - ✅ Meta Description已完整
                - ❌ H1标签未包含主关键词
                - ✅ 图片Alt标签已完整

                ## 2. 关键词优化
                - 核心关键词: %s
                - 长尾关键词建议: POP display stand wholesale, custom cardboard display, retail display solutions
                - 竞争密度: 中等
                - 月搜索量: 2,400-8,100 (US市场)

                ## 3. 技术SEO
                - ✅ SSL证书已配置
                - ❌ 页面加载速度: 3.2s (建议<2.5s)
                - ✅ Sitemap已提交
                - ✅ Robots.txt正确配置

                ## 4. 优化建议
                1. 优化Title Tag包含品牌词
                2. 添加Schema Markup (Product + Organization)
                3. 压缩图片以提升页面速度
                4. 增加内部链接至相关产品

                ---
                📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI SEO分析。
                """, url, country, kw);
    }

    public String generateCompetitorDemo(String competitorUrl, String targetCountry) {
        String country = CountryLanguageUtil.resolveCountryName(targetCountry);
        return String.format("""
                🔳 竞品分析报告 (演示模式)

                ## 竞品: %s | 目标市场: %s

                ## 1. 公司概况
                - 类型: POP展示架制造商/贸易公司
                - 规模: 中型企业
                - 主要市场: %s

                ## 2. 产品竞争力
                | 维度 | JC Display | 竞品 |
                |--------|-----------|------|
                | 价格 | ★★★★★ | ★★★ |
                | 质量 | ★★★★ | ★★★★ |
                | 定制 | ★★★★★ | ★★ |
                | 交货 | ★★★★ | ★★★★★ |

                ## 3. 差异化优势
                - JC Display优势: 17年行业经验、免费设计、平摊包装节省运费
                - 竞品优势: 快速小批量交货

                ## 4. 竞争策略建议
                1. 突出"17年经验 + 免费设计"品牌差异化
                2. 针对竞品价格标注"批发价格更优"
                3. 制作竞品对比表用于销售话术

                ---
                📌 提示: 配置 DeepSeek API Key 后可获得真正的 AI竞品分析。
                """, competitorUrl, country, country);
    }

}
