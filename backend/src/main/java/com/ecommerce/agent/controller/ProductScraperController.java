package com.ecommerce.agent.controller;

import com.ecommerce.agent.rag.KnowledgeBaseLoader;
import com.ecommerce.agent.rag.ProductScraper;
import com.ecommerce.agent.rag.ProductScraper.ScrapedProduct;
import com.ecommerce.agent.rag.ProductScraper.ScrapeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
public class ProductScraperController {

    private final ProductScraper productScraper;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    /**
     * 从指定产品列表 URL 逐条爬取并实时写入 MySQL（防丢失）
     */
    @PostMapping("/list")
    public ResponseEntity<Map<String, Object>> scrapeList(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "http://www.displaystandpop.com/productlist.html");
        if (url.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "url 不能为空"));
        }
        log.info("从列表页开始爬取: {}", url);

        // 异步执行
        new Thread(() -> {
            try {
                productScraper.scrapeProductListUrl(url);
            } catch (Exception e) {
                log.error("列表页爬取失败", e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "爬取已启动，请通过 GET /api/scraper/status 查看进度",
                "url", url
        ));
    }

    /**
     * 仅补全缺失图片（不重复遍历品类/分页）
     */
    @PostMapping("/fill-images")
    public ResponseEntity<Map<String, Object>> fillImages() {
        log.info("触发图片补全（仅补缺失，不遍历）...");

        new Thread(() -> {
            try {
                int count = productScraper.fillMissingImages();
                log.info("图片补全完成: 修复 {} 个产品", count);
            } catch (Exception e) {
                log.error("图片补全异常", e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图片补全已启动（异步），请通过 GET /api/scraper/status 查看进度"
        ));
    }

    /**
     * 触发全量产品爬取
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runScraper() {
        log.info("触发全量产品爬取（异步）...");

        // 异步执行，避免 HTTP 超时中断
        new Thread(() -> {
            try {
                ScrapeResult result = productScraper.scrapeAll();
                log.info("全量爬取完成: {} 个产品", result.getTotalProducts());
            } catch (Exception e) {
                log.error("全量爬取异常", e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "爬取已启动（异步），请通过 GET /api/scraper/status 查看进度"
        ));
    }

    /**
     * 对单个 URL 进行抓取测试
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testScrape(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "");
        if (url.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "url 不能为空"));
        }

        ScrapedProduct product = productScraper.scrapeProductPage(url);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", product != null);
        if (product != null) {
            response.put("name", product.getName());
            response.put("url", product.getUrl());
            response.put("price", product.getPrice());
            response.put("sku", product.getSku());
            response.put("imageUrl", product.getImageUrl());
            response.put("description", product.getDescription());
        } else {
            response.put("message", "无法抓取该页面，请检查URL是否正确");
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 调试端点：分析指定URL的HTML结构，帮助调整CSS选择器
     */
    @PostMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "http://www.displaystandpop.com/productlist.html");
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            response.put("url", url);
            response.put("finalUrl", doc.location());
            response.put("title", doc.title());

            // 页面关键 CSS class 统计
            Map<String, Integer> classCounts = new LinkedHashMap<>();
            Elements all = doc.select("[class]");
            for (Element el : all) {
                for (String cls : el.className().split("\\s+")) {
                    if (!cls.isBlank()) {
                        classCounts.merge(cls.toLowerCase(), 1, Integer::sum);
                    }
                }
            }
            // 只保留出现次数 >=2 的
            classCounts.entrySet().removeIf(e -> e.getValue() < 2);
            response.put("cssClasses", classCounts);

            // 页面所有链接（前50个）
            List<Map<String, String>> links = new ArrayList<>();
            Elements aTags = doc.select("a[href]");
            for (int i = 0; i < Math.min(aTags.size(), 50); i++) {
                Element a = aTags.get(i);
                Map<String, String> linkInfo = new LinkedHashMap<>();
                linkInfo.put("text", a.text().trim());
                linkInfo.put("href", a.absUrl("href"));
                linkInfo.put("class", a.className());
                links.add(linkInfo);
            }
            response.put("totalLinks", aTags.size());
            response.put("links", links);

            // 图片列表
            List<String> images = new ArrayList<>();
            Elements imgs = doc.select("img[src]");
            for (int i = 0; i < Math.min(imgs.size(), 20); i++) {
                images.add(imgs.get(i).absUrl("src"));
            }
            response.put("totalImages", imgs.size());
            response.put("images", images);

            // body 前800字符
            String bodyText = doc.body().text();
            response.put("bodyPreview", bodyText.length() > 800
                    ? bodyText.substring(0, 800) + "..." : bodyText);

        } catch (IOException e) {
            response.put("error", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 获取已抓取的产品列表
     */
    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        List<ScrapedProduct> all = productScraper.getCachedProducts();
        int total = all.size();

        List<Map<String, Object>> items = new ArrayList<>();
        int end = Math.min(offset + limit, all.size());
        for (int i = offset; i < end; i++) {
            ScrapedProduct p = all.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", p.getName());
            item.put("url", p.getUrl());
            item.put("price", p.getPrice());
            item.put("sku", p.getSku());
            item.put("imageUrl", p.getImageUrl());
            item.put("category", p.getCategory());
            item.put("description", p.getDescription() != null
                    ? p.getDescription().substring(0, Math.min(200, p.getDescription().length()))
                    : null);
            items.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("items", items);
        return ResponseEntity.ok(response);
    }

    /**
     * 重新加载知识库（将已抓取的产品也纳入 RAG 向量存储）
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        log.info("重新加载知识库（含产品数据）...");
        long start = System.currentTimeMillis();

        // 强制重新加载
        knowledgeBaseLoader.forceReload();

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "知识库重新加载完成");
        response.put("durationMs", elapsed);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取抓取状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        List<ScrapedProduct> products = productScraper.getCachedProducts();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("productsCached", products.size());
        response.put("lastUpdated", products.isEmpty() || products.get(products.size() - 1).getScrapedAt() == null ? "N/A"
                : products.get(products.size() - 1).getScrapedAt().toString());
        response.put("progress", productScraper.getScrapeProgress());
        response.put("total", productScraper.getScrapeTotal());
        response.put("status", productScraper.getScrapeStatus());
        return ResponseEntity.ok(response);
    }
}
