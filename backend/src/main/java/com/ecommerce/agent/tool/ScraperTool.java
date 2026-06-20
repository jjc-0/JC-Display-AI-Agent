package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ScraperTool implements Tool {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ECommerceAgent/1.0)";

    @Override
    public String getName() {
        return "scraper";
    }

    @Override
    public String getDescription() {
        return "网页内容抓取工具，用于抓取商品页面信息、价格、评价等。输入URL，返回页面结构化信息。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> urlProp = new LinkedHashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "要抓取的网页URL");
        props.put("url", urlProp);

        Map<String, Object> selectorProp = new LinkedHashMap<>();
        selectorProp.put("type", "string");
        selectorProp.put("description", "可选，CSS选择器用于提取特定内容");
        props.put("selector", selectorProp);

        schema.put("properties", props);
        schema.put("required", List.of("url"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = (String) params.get("url");
                String selector = (String) params.get("selector");

                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(10000)
                        .get();

                StringBuilder sb = new StringBuilder("📄 网页抓取结果:\n\n");
                sb.append("URL: ").append(url).append("\n");
                sb.append("标题: ").append(doc.title()).append("\n\n");

                if (selector != null && !selector.isBlank()) {
                    Elements elements = doc.select(selector);
                    sb.append("选择器 '").append(selector).append("' 匹配结果 (").append(elements.size()).append("条):\n");
                    for (int i = 0; i < Math.min(elements.size(), 10); i++) {
                        sb.append(i + 1).append(". ").append(elements.get(i).text().trim()).append("\n");
                    }
                } else {
                    String metaDesc = getMetaContent(doc, "description");
                    String metaKeywords = getMetaContent(doc, "keywords");

                    sb.append("页面描述: ").append(metaDesc != null ? metaDesc : "无").append("\n");
                    sb.append("关键词: ").append(metaKeywords != null ? metaKeywords : "无").append("\n\n");

                    sb.append("页面文本摘要 (前500字符):\n");
                    String bodyText = doc.body().text();
                    sb.append(bodyText.length() > 500 ? bodyText.substring(0, 500) + "..." : bodyText);
                    sb.append("\n\n");

                    sb.append("页面链接:\n");
                    Elements links = doc.select("a[href]");
                    for (int i = 0; i < Math.min(links.size(), 10); i++) {
                        Element link = links.get(i);
                        sb.append("- ").append(link.text().trim())
                                .append(" → ").append(link.absUrl("href")).append("\n");
                    }
                }

                return sb.toString();
            } catch (Exception e) {
                log.error("Scraper tool error", e);
                return "网页抓取失败: " + e.getMessage();
            }
        });
    }

    private String getMetaContent(Document doc, String name) {
        Element meta = doc.selectFirst("meta[name=" + name + "]");
        if (meta == null) {
            meta = doc.selectFirst("meta[property=og:" + name + "]");
        }
        return meta != null ? meta.attr("content") : null;
    }
}
