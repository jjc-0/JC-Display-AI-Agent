package com.ecommerce.agent.rag;

import com.ecommerce.agent.model.Product;
import com.ecommerce.agent.repository.ProductRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 公司官网产品爬虫工具
 * 从 displaystandpop.com 抓取所有产品信息，输出为 RAG 知识库文档
 *
 * 爬取策略：
 * 1. 访问首页，发现所有链接
 * 2. 识别列表页（productlist.html / category 等），提取产品卡片
 * 3. 逐个抓取产品详情页
 */
@Slf4j
@Component
public class ProductScraper {

    private final ProductRepository productRepo;

    private static final String BASE_URL = "http://www.displaystandpop.com";
    // Alibaba 店铺备用 URL
    private static final String ALIBABA_STORE_PREFIX = "https://jcdisplay.en.alibaba.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int REQUEST_DELAY_MS = 1500;
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_PAGES = 50; // 最大翻页数
    private static final int MAX_PRODUCTS = 10000;

    // 文件保存的基础目录
    private static final String SAVE_DIR = "src/main/resources/knowledge/products";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final List<ScrapedProduct> products = Collections.synchronizedList(new ArrayList<>());

    public ProductScraper(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    // 已知的列表页 URL 模式
    private static final String[] LIST_PAGE_PATTERNS = {
            "productlist", "product-list", "products.html", "all-products",
            "product/category", "category.html", "catalog",
            "productgrouplist",                          // Alibaba 品类页
    };

    // 产品详情页 URL 特征
    private static final String[] PRODUCT_DETAIL_PATTERNS = {
            "product-detail", "product_detail", "productdetail",
            "product-item", "product_info",
            "showproduct", "product-view",
            "alibaba.com/product-detail/",
            "/product-detail/",
            ".html" // 先用宽松匹配，在列表页上下文中再过滤
    };

    // Alibaba 已知类目 URL 模式（作为列表页兜底）
    private static final String[] ALIBABA_CATEGORY_URLS = {
            "https://jcdisplay.en.alibaba.com/productgrouplist",
            "https://jcdisplay.en.alibaba.com/productlist",
            "https://www.alibaba.com/product-detail/"  // 作为单产品页
    };

    // 从浏览器 Console 中提取的所有品类页 URL（JS 动态加载，无法从 HTML 发现）
    private static final String[] KNOWN_CATEGORY_URLS = {
            "/productgrouplist-213106225/Packaging.html",
            "/productgrouplist-220643886/Cardboard_Display_Stands.html",
            "/productgrouplist-220643887/Cardboard_Hook_Displays.html",
            "/productgrouplist-220640933/Cardboard_Pallet_Displays.html",
            "/productgrouplist-221605480/Counter_Display_Boxes.html",
            "/productgrouplist-221211842/Cardboard_Dump_Bins.html",
            "/productgrouplist-221608499/Brochure_leaflet_holders.html",
            "/productgrouplist-221605431/Cardboard_Totem_Displays.html",
            "/productgrouplist-220642975/Advertising_Banners.html",
            "/productgrouplist-221601453/Cardboard_Trolley_Boxes.html",
            "/productgrouplist-807048216/Cardboard_Cat_Scratcher.html",
            "/productgrouplist-213562583/Paper_Puzzles.html",
            "/productgrouplist-214122412/Acrylic_Displays.html",
            // 下面 4 个因 Console 日志截断需要从浏览器补充
            "/productgrouplist-221608497/Gift_Box_Packaging.html",
            "/productgrouplist-221608498/Paper_Bags.html",
            "/productgrouplist-220640932/Corrugated_Boxes.html",
            "/productgrouplist-221605479/Cosmetic_Packaging.html",
    };

    // 产品列表面常见的单个产品容器选择器
    private static final String[] PRODUCT_CARD_SELECTORS = {
            ".icbu-product-card",                          // Alibaba ICBU 卡片（已验证可用）
            ".product-item", ".product-card",
            ".product-grid .product", ".product-list .product",
            ".product-wrap", ".product-box",
            "[class*='product-card']",
    };

    // 产品卡片内：名称选择器
    private static final String[] CARD_NAME_SELECTORS = {
            ".title", ".title-link", ".title-con",         // Alibaba ICBU
            ".product-name", ".product-title", "h3", "h4",
            ".name", "[itemprop='name']", ".group-link",
    };

    // 产品卡片内：链接选择器（指向详情页）
    private static final String[] CARD_LINK_SELECTORS = {
            ".title-link", ".group-link", ".title a",        // Alibaba ICBU
            "a.product-name", "a.product-title", "a[href*='product']",
            "a[href*='detail']", "a[href*='item']",
            ".product-img a", ".product-image a", ".image a",
            "h3 a", "h4 a", ".name a",
    };

    // 产品卡片内：图片选择器
    private static final String[] CARD_IMAGE_SELECTORS = {
            ".product-image img", ".img-box img",           // Alibaba ICBU
            ".product-img img", ".thumbnail img", ".image img",
            "img[src$=.jpg]", "img[src$=.png]",
            ".pic-box img", "img.lazy-load",
    };

    // 产品详情页字段选择器
    private static final String[] PRODUCT_NAME_SELECTORS = {
            "h1", ".product-name", ".product-title", "[itemprop='name']",
            "#content h1", ".heading-title", ".product-info h1", ".detail-title",
            ".product-title-container h1", ".ma-title", // Alibaba
            ".module_productDetail_title h1",             // Alibaba
    };
    private static final String[] PRODUCT_DESC_SELECTORS = {
            "#tab-description", ".product-description", "[itemprop='description']",
            "#tab-specification", ".tab-content", ".description",
            ".product-info .description", "#content .description",
            ".product-detail .description", ".detail-content",
            ".richtext", ".detail-desc",                   // Alibaba
            ".module_productDetail_description",            // Alibaba
    };
    private static final String[] PRODUCT_PRICE_SELECTORS = {
            ".price", ".product-price", "[itemprop='price']",
            ".price-new", ".special-price", ".product-info .price",
            ".ma-price", ".module_productDetail_price",    // Alibaba
    };
    private static final String[] PRODUCT_IMAGE_SELECTORS = {
            "meta[property='og:image']",                        // Open Graph（Alibaba 必有）
            ".product-image img", ".thumbnail img", "[itemprop='image']",
            ".image img", ".main-image img", ".product-img img",
            ".detail-img img", ".pic img",
            ".main-image-view img", ".ma-img img",              // Alibaba
            ".module_productDetail_image img",                   // Alibaba
            "#J_ImgBooth",                                      // Alibaba 主图
            ".MagicZoom img", ".big-image img",                 // 通用
            "img.mainPic", "img#mainImg",
    };
    private static final String[] PRODUCT_SKU_SELECTORS = {
            ".product-model", ".model", "[itemprop='sku']",
            ".sku", ".product-sku", ".model-number",
            ".module_productDetail_attributes",             // Alibaba
    };

    // URL 黑名单：这些页面绝对不是产品页
    private static final String[] URL_BLACKLIST = {
            "message.alibaba.com", "contact.htm", "company.htm",
            "about.htm", "overview.htm", "login", "signin", "register",
            "alibaba.com/help", "trustpass", "gold-supplier",
    };

    // 页面标题/名称黑名单
    private static final String[] NAME_BLACKLIST = {
            "alibaba manufacturer directory", "company overview",
            "contact information", "about us", "home page",
            "suppliers, manufacturers", "exporters & importers",
            "gold supplier", "verified supplier", "login",
            "sign in", "register", "privacy policy",
    };

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScrapedProduct {
        private String name;
        private String url;
        private String imageUrl;
        private String price;
        private String sku;
        private String description;
        @JsonIgnore
        private String category;
        private LocalDateTime scrapedAt;

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(name != null ? name : "未知产品").append("\n\n");
            if (sku != null && !sku.isBlank()) {
                sb.append("- **型号/SKU**: ").append(sku).append("\n");
            }
            if (price != null && !price.isBlank()) {
                sb.append("- **价格**: ").append(price).append("\n");
            }
            if (category != null && !category.isBlank()) {
                sb.append("- **分类**: ").append(category).append("\n");
            }
            if (url != null && !url.isBlank()) {
                sb.append("- **产品链接**: ").append(url).append("\n");
            }
            if (imageUrl != null && !imageUrl.isBlank()) {
                sb.append("- **产品图片**: ").append(imageUrl).append("\n");
            }
            if (description != null && !description.isBlank()) {
                sb.append("\n**产品描述**:\n").append(description).append("\n");
            }
            sb.append("\n---\n");
            return sb.toString();
        }
    }

    @Data
    @Builder
    public static class ScrapeResult {
        private int totalProducts;
        private int categoriesFound;
        private long durationMs;
        private List<String> productNames;
        private String debugInfo;
        private String message;
    }

    /**
     * 快速分页：直接构造 &page=N URL，不进行慢速模式测试（适用于 Alibaba 等）
     */
    private Set<String> quickPagination(String baseUrl, org.jsoup.nodes.Document firstDoc) {
        Set<String> urls = new LinkedHashSet<>();

        // 从页面提取总产品数来估算页数
        int estimatedPages = 50; // 默认
        try {
            String bodyText = firstDoc.body().text();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("of\\s+([\\d,]+)\\s+results", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(bodyText);
            if (m.find()) {
                int total = Integer.parseInt(m.group(1).replace(",", ""));
                // 每页约 20 个产品
                estimatedPages = (int) Math.ceil(total / 20.0);
                log.info("快速分页: 总共约 {} 个产品, 估算 {} 页", total, estimatedPages);
            }
        } catch (Exception e) {
            log.debug("无法从页面提取总产品数, 使用默认分页数");
        }

        // 检测第一页的产品卡片数以确定每页数量
        int cardsOnFirstPage = 20;
        for (String sel : PRODUCT_CARD_SELECTORS) {
            int size = firstDoc.select(sel).size();
            if (size >= 1) {
                cardsOnFirstPage = Math.max(cardsOnFirstPage, size);
            }
        }
        if (cardsOnFirstPage > 20) estimatedPages = Math.max(estimatedPages, cardsOnFirstPage);

        // 限制最大页数
        estimatedPages = Math.min(estimatedPages, 100);

        // 智能选择 ?page= 或 &page=（根据 URL 是否已有查询参数）
        String clean = baseUrl.replaceAll("[?&]page=\\d+", "");
        String connector = clean.contains("?") ? "&" : "?";
        for (int p = 2; p <= estimatedPages; p++) {
            urls.add(clean + connector + "page=" + p);
        }

        log.info("快速分页: 生成 {} 个分页 URL ({} 页)", urls.size(), estimatedPages);
        return urls;
    }

    /**
     * 执行全量爬取
     */
    public ScrapeResult scrapeAll() {
        long start = System.currentTimeMillis();
        visitedUrls.clear();
        products.clear();
        List<String> debugLog = Collections.synchronizedList(new ArrayList<>());

        log.info("开始爬取官网 {} ...", BASE_URL);

        try {
            // Step 1: 爬取首页
            org.jsoup.nodes.Document homePage = fetchPage(BASE_URL);
            if (homePage == null) {
                return failResult(start, "无法访问首页 " + BASE_URL);
            }
            visitedUrls.add(BASE_URL);
            visitedUrls.add(BASE_URL + "/");

            // Step 2: 从首页找到所有可能的入口链接
            Set<String> listPages = new LinkedHashSet<>();
            Set<String> directProductUrls = new LinkedHashSet<>();
            classifyUrls(homePage, listPages, directProductUrls);

            debugLog.add("发现列表页: " + listPages.size() + "个 - " + listPages);
            debugLog.add("首页直接产品链接: " + directProductUrls.size() + "个 - " + directProductUrls);
            log.info("发现 {} 个列表页, {} 个直接产品链接", listPages.size(), directProductUrls.size());

            // 如果没发现任何链接，尝试已知 URL
            if (listPages.isEmpty() && directProductUrls.isEmpty()) {
                for (String u : new String[]{
                        BASE_URL + "/productlist.html",
                        BASE_URL + "/products.html",
                        BASE_URL + "/product.html",
                }) listPages.add(u);
                debugLog.add("未发现链接，尝试已知URL");
            }

            // 始终追加已知品类页 URL（JS 动态加载，HTML 中无链接）
            for (String path : KNOWN_CATEGORY_URLS) {
                listPages.add(BASE_URL + path);
            }
            debugLog.add("追加密品页: " + KNOWN_CATEGORY_URLS.length + "个，共 " + listPages.size() + "个列表页");

            // Step 3: 用队列遍历所有列表页（支持动态发现新分页/品类）
            LinkedList<String> pageQueue = new LinkedList<>(listPages);
            while (!pageQueue.isEmpty()) {
                if (products.size() >= MAX_PRODUCTS) {
                    debugLog.add("达到上限 " + MAX_PRODUCTS + "，停止遍历");
                    break;
                }
                String listUrl = pageQueue.pollFirst();
                if (visitedUrls.contains(listUrl)) continue;
                try {
                    Thread.sleep(REQUEST_DELAY_MS);
                    org.jsoup.nodes.Document doc = fetchPage(listUrl);
                    if (doc == null) continue;
                    visitedUrls.add(listUrl);

                    // 判断是列表页还是详情页
                    boolean isListPage = isProductListPage(doc, listUrl);
                    debugLog.add("页面 " + listUrl + " -> " + (isListPage ? "列表页" : "非列表页"));

                    if (isListPage) {
                        // 从列表页发现品类页（productgrouplist链接）
                        Set<String> categoryPages = discoverCategoryUrls(doc);
                        for (String catUrl : categoryPages) {
                            if (!visitedUrls.contains(catUrl)) {
                                pageQueue.add(catUrl);
                            }
                        }
                        debugLog.add("发现品类链接: " + categoryPages.size() + "个 - " + categoryPages);

                        // 策略A: 从列表页提取产品卡片信息（快速，适合静态页面）
                        List<ScrapedProduct> cardProducts = extractProductsFromListPage(doc, listUrl);
                        if (!cardProducts.isEmpty()) {
                            for (ScrapedProduct p : cardProducts) {
                                if (products.size() >= MAX_PRODUCTS) break;
                                products.add(p);
                                saveOneToMySql(p);  // 实时写入 MySQL
                            }
                            scrapeProgress = products.size();
                            scrapeStatus = "scraping " + scrapeProgress + " products";
                            debugLog.add("从列表页提取 " + cardProducts.size() + " 个产品");
                            log.info("从列表页 {} 提取 {} 个产品", listUrl, cardProducts.size());
                        }

                        // 策略B: 同时从列表页发现产品详情链接，逐个抓取
                        Set<String> detailUrls = discoverProductUrls(doc);
                        debugLog.add("列表页中发现 " + detailUrls.size() + " 个详情链接");

                        for (String detailUrl : detailUrls) {
                            if (products.size() >= MAX_PRODUCTS) break;
                            try {
                                Thread.sleep(REQUEST_DELAY_MS);
                                ScrapedProduct product = scrapeProductPage(detailUrl);
                                if (product != null && !isDuplicate(product)) {
                                    products.add(product);
                                    saveOneToMySql(product);  // 实时写入 MySQL
                                    scrapeProgress = products.size();
                                    scrapeStatus = "scraping " + scrapeProgress + " products";
                                    log.info("[{}/{}] 详情页抓取: {}", products.size(), MAX_PRODUCTS, product.getName());
                                }
                            } catch (Exception e) {
                                log.warn("详情页抓取失败: {} - {}", detailUrl, e.getMessage());
                            }
                        }

                        // 处理分页（快速模式：直接构造 &page=N，跳过慢速模式测试）
                        Set<String> paginationUrls = discoverPaginationUrls(doc, listUrl);
                        if (paginationUrls.isEmpty()) {
                            // 快速构造：不测试，直接生成 URL
                            paginationUrls = quickPagination(listUrl, doc);
                        }

                        for (String pageUrl : paginationUrls) {
                            if (products.size() >= MAX_PRODUCTS) break;
                            try {
                                Thread.sleep(REQUEST_DELAY_MS);
                                org.jsoup.nodes.Document pagedDoc = fetchPage(pageUrl);
                                if (pagedDoc == null) continue;
                                visitedUrls.add(pageUrl);
                                List<ScrapedProduct> pagedProducts = extractProductsFromListPage(pagedDoc, pageUrl);
                                for (ScrapedProduct p : pagedProducts) {
                                    if (products.size() >= MAX_PRODUCTS) break;
                                    products.add(p);
                                    saveOneToMySql(p);  // 实时写入 MySQL
                                }
                                scrapeProgress = products.size();
                                scrapeStatus = "scraping " + scrapeProgress + " products";
                            } catch (Exception e) {
                                log.warn("分页爬取失败: {} - {}", pageUrl, e.getMessage());
                            }
                        }
                    } else {
                        // 不是列表页，当作单产品页处理
                        ScrapedProduct product = scrapeProductPage(listUrl);
                        if (product != null && !isDuplicate(product)) {
                            products.add(product);
                            saveOneToMySql(product);
                            scrapeProgress = products.size();
                            scrapeStatus = "scraping " + scrapeProgress + " products";
                        }
                    }
                } catch (Exception e) {
                    log.warn("处理页面失败: {} - {}", listUrl, e.getMessage());
                }
            }

            // Step 4: 抓取首页直接发现的产品链接
            for (String url : directProductUrls) {
                if (products.size() >= MAX_PRODUCTS) break;
                try {
                    Thread.sleep(REQUEST_DELAY_MS);
                    ScrapedProduct product = scrapeProductPage(url);
                    if (product != null && !isDuplicate(product)) {
                        products.add(product);
                        saveOneToMySql(product);
                        scrapeProgress = products.size();
                        scrapeStatus = "scraping " + scrapeProgress + " products";
                    }
                } catch (Exception e) {
                    log.warn("产品页抓取失败: {} - {}", url, e.getMessage());
                }
            }

            // Step 5: 持久化
            saveProductsToFile();

            // Step 6: 补全缺失图片 — 对 MySQL 中无图片的产品重新抓取详情页
            scrapeStatus = "filling missing images";
            log.info("开始补全缺失图片...");
            int filledCount = fillMissingImages();
            log.info("图片补全完成: 修复 {} 个产品", filledCount);

            long duration = System.currentTimeMillis() - start;
            log.info("爬取完成: 共 {} 个产品, 耗时 {}ms", products.size(), duration);

            return ScrapeResult.builder()
                    .totalProducts(products.size())
                    .categoriesFound(listPages.size())
                    .durationMs(duration)
                    .productNames(products.stream().map(ScrapedProduct::getName).collect(Collectors.toList()))
                    .debugInfo(String.join("\n", debugLog))
                    .message("成功抓取 " + products.size() + " 个产品")
                    .build();

        } catch (Exception e) {
            log.error("爬取过程异常", e);
            return failResult(start, "爬取出错: " + e.getMessage());
        }
    }

    // 进度追踪
    private volatile int scrapeProgress = 0;
    private volatile int scrapeTotal = 0;
    private volatile String scrapeStatus = "idle";

    /**
     * 从指定产品列表 URL 爬取所有产品（支持分页，逐条实时写入 MySQL，防丢失）
     */
    public ScrapeResult scrapeProductListUrl(String listUrl) {
        long start = System.currentTimeMillis();
        scrapeProgress = 0;
        scrapeTotal = 0;
        scrapeStatus = "starting";
        int savedCount = 0;
        List<String> debugLog = Collections.synchronizedList(new ArrayList<>());

        log.info("开始从列表页爬取: {}", listUrl);

        try {
            // Step 1: 发现所有分页 URL
            scrapeStatus = "discovering pages";
            org.jsoup.nodes.Document firstDoc = fetchPage(listUrl);
            if (firstDoc == null) {
                return failResult(start, "无法访问列表页: " + listUrl);
            }
            Set<String> pagination = discoverPaginationUrls(firstDoc, listUrl);
            if (pagination.isEmpty()) {
                // 兜底：快速构造分页 URL（跳过慢速模式测试）
                pagination = quickPagination(listUrl, firstDoc);
            }

            List<String> listPages = new ArrayList<>();
            listPages.add(listUrl);
            listPages.addAll(pagination);
            debugLog.add("发现 " + listPages.size() + " 个列表分页");
            log.info("发现 {} 个列表分页", listPages.size());

            // Step 2: 从所有列表页提取产品 URL
            scrapeStatus = "collecting product URLs";
            LinkedHashSet<String> allProductUrls = new LinkedHashSet<>();
            for (int pi = 0; pi < listPages.size(); pi++) {
                String pageUrl = listPages.get(pi);
                if (pi > 0) Thread.sleep(REQUEST_DELAY_MS);
                org.jsoup.nodes.Document doc = (pi == 0) ? firstDoc : fetchPage(pageUrl);
                if (doc == null) continue;

                Set<String> urls = discoverProductUrls(doc);
                List<ScrapedProduct> cardProducts = extractProductsFromListPage(doc, pageUrl);
                for (ScrapedProduct p : cardProducts) {
                    if (p.getUrl() != null && !p.getUrl().isBlank() && !p.getUrl().equals(pageUrl)) {
                        urls.add(p.getUrl());
                    }
                }
                allProductUrls.addAll(urls);
                log.info("分页 [{}/{}]: {} 个产品链接", pi + 1, listPages.size(), urls.size());
            }

            debugLog.add("共发现 " + allProductUrls.size() + " 个不重复产品链接");
            log.info("共发现 {} 个不重复产品链接", allProductUrls.size());

            if (allProductUrls.isEmpty()) {
                scrapeStatus = "done (no products found)";
                return failResult(start, "未在任何列表页中找到产品详情链接");
            }

            // Step 3: 逐个抓取产品详情并立即写入 MySQL
            scrapeTotal = allProductUrls.size();
            scrapeStatus = "scraping 0/" + scrapeTotal;
            List<String> urlList = new ArrayList<>(allProductUrls);
            int index = 0;
            for (String url : urlList) {
                index++;
                scrapeProgress = index;
                scrapeStatus = "scraping " + index + "/" + scrapeTotal;
                try {
                    if (index > 1) Thread.sleep(REQUEST_DELAY_MS);
                    ScrapedProduct product = scrapeProductPage(url);
                    if (product != null) {
                        saveOneToMySql(product);
                        savedCount++;
                        log.info("[{}/{}] 已保存: {}",
                                index, scrapeTotal,
                                product.getName().substring(0, Math.min(60, product.getName().length())));
                    }
                } catch (Exception e) {
                    log.warn("[{}/{}] 失败: {} - {}", index, scrapeTotal, url, e.getMessage());
                }
            }

            // 保存到 JSON 备份
            saveProductsToFile();

            scrapeStatus = "done";
            long duration = System.currentTimeMillis() - start;
            log.info("列表页爬取完成: {} 个产品链接, 保存 {} 条, 耗时 {}ms", scrapeTotal, savedCount, duration);

            return ScrapeResult.builder()
                    .totalProducts(savedCount)
                    .categoriesFound(listPages.size())
                    .durationMs(duration)
                    .productNames(List.of())
                    .debugInfo(String.join("\n", debugLog))
                    .message("从 " + listUrl + " 抓取完成，共 " + savedCount + "/" + scrapeTotal + " 个产品已保存到 MySQL")
                    .build();

        } catch (Exception e) {
            scrapeStatus = "error: " + e.getMessage();
            log.error("列表页爬取异常", e);
            return failResult(start, "爬取出错: " + e.getMessage());
        }
    }

    /**
     * 逐条写入 MySQL（实时持久化），URL 去重 + 过滤非商品页
     */
    private void saveOneToMySql(ScrapedProduct sp) {
        // 过滤非产品 URL
        if (sp.getUrl() == null || !sp.getUrl().contains("/product-detail/")) {
            log.debug("跳过非产品URL: {}", sp.getUrl());
            return;
        }
        // 名称黑名单检查
        if (isBlacklistedName(sp.getName())) {
            log.debug("跳过黑名单名称: {}", sp.getName());
            return;
        }
        try {
            // URL 作为唯一去重键
            Optional<com.ecommerce.agent.model.Product> existingOpt = productRepo.findByUrl(sp.getUrl());
            if (existingOpt.isPresent()) {
                com.ecommerce.agent.model.Product existingProduct = existingOpt.get();
                boolean updated = false;
                if ((existingProduct.getImageUrl() == null || existingProduct.getImageUrl().isBlank())
                        && sp.getImageUrl() != null && !sp.getImageUrl().isBlank()
                        && !isBadgeImage(sp.getImageUrl())) {
                    existingProduct.setImageUrl(sp.getImageUrl());
                    updated = true;
                }
                if ((existingProduct.getDescription() == null || existingProduct.getDescription().isBlank())
                        && sp.getDescription() != null && !sp.getDescription().isBlank()) {
                    existingProduct.setDescription(sp.getDescription());
                    updated = true;
                }
                if ((existingProduct.getPrice() == null || existingProduct.getPrice().isBlank())
                        && sp.getPrice() != null && !sp.getPrice().isBlank()) {
                    existingProduct.setPrice(sp.getPrice());
                    updated = true;
                }
                if (updated) {
                    productRepo.save(existingProduct);
                    log.debug("产品已更新（补全字段）: {}", sp.getName());
                } else {
                    log.debug("产品已存在，跳过: {}", sp.getName());
                }
                return;
            }
            productRepo.save(com.ecommerce.agent.model.Product.builder()
                    .name(sp.getName())
                    .url(sp.getUrl())
                    .imageUrl(isBadgeImage(sp.getImageUrl()) ? null : sp.getImageUrl())
                    .price(sp.getPrice())
                    .sku(sp.getSku())
                    .description(sp.getDescription())
                    .category(sp.getCategory())
                    .enabled(true)
                    .build());
        } catch (Exception e) {
            log.warn("保存产品到 MySQL 失败: {} - {}", sp.getName(), e.getMessage());
        }
    }

    public int getScrapeProgress() { return scrapeProgress; }
    public int getScrapeTotal() { return scrapeTotal; }
    public String getScrapeStatus() { return scrapeStatus; }

    // 公开方法，给 Controller 测试用
    public org.jsoup.nodes.Document fetchWithHttpClientPublic(String url) {
        return fetchWithHttpClient(url);
    }

    public String extractImageUrlPublic(org.jsoup.nodes.Document doc) {
        return extractImageUrl(doc);
    }

    /**
     * 爬取单个产品详情页
     */
    public ScrapedProduct scrapeProductPage(String productUrl) {
        if (visitedUrls.contains(productUrl)) return null;
        visitedUrls.add(productUrl);

        // URL 黑名单检查
        if (isBlacklistedUrl(productUrl)) {
            log.debug("URL黑名单拦截: {}", productUrl);
            return null;
        }

        org.jsoup.nodes.Document doc = fetchPage(productUrl);
        if (doc == null) return null;

        String name = extractText(doc, PRODUCT_NAME_SELECTORS);
        if (name == null || name.isBlank()) {
            name = doc.title().trim();
        }

        // 标题/名称黑名单检查
        if (isBlacklistedName(name)) {
            log.debug("名称黑名单拦截: {} -> {}", productUrl, name);
            return null;
        }

        // 跳过明显不是产品页的
        if (name != null && (name.contains("All products") || name.contains("Product List"))) {
            log.debug("跳过非产品页: {} -> {}", productUrl, name);
            return null;
        }

        String description = extractText(doc, PRODUCT_DESC_SELECTORS);
        String price = extractText(doc, PRODUCT_PRICE_SELECTORS);
        String sku = extractText(doc, PRODUCT_SKU_SELECTORS);
        String imageUrl = extractImageUrl(doc);

        if (description != null && description.length() > 2000) {
            description = description.substring(0, 2000) + "...";
        }

        return ScrapedProduct.builder()
                .name(name.trim())
                .url(productUrl)
                .imageUrl(imageUrl)
                .price(price != null ? price.trim() : null)
                .sku(sku != null ? sku.trim() : null)
                .description(description != null ? description.trim() : null)
                .scrapedAt(LocalDateTime.now())
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 判断是否为产品列表页
     */
    private boolean isProductListPage(org.jsoup.nodes.Document doc, String url) {
        // URL 特征
        String lowerUrl = url.toLowerCase();
        for (String pattern : LIST_PAGE_PATTERNS) {
            if (lowerUrl.contains(pattern)) return true;
        }

        // 页面内容特征：是否有多个产品链接
        int productLinkCount = 0;
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.absUrl("href").toLowerCase();
            for (String pattern : PRODUCT_DETAIL_PATTERNS) {
                if (href.contains(pattern)) {
                    productLinkCount++;
                    break;
                }
            }
        }
        if (productLinkCount >= 3) return true;

        // 检查是否有产品卡片容器
        for (String selector : PRODUCT_CARD_SELECTORS) {
            if (doc.select(selector).size() >= 2) return true;
        }

        return false;
    }

    /**
     * 从列表页直接提取产品信息（不跳转详情页）
     */
    private List<ScrapedProduct> extractProductsFromListPage(org.jsoup.nodes.Document doc, String pageUrl) {
        List<ScrapedProduct> result = new ArrayList<>();

        // 尝试各种产品卡片选择器
        Elements cards = null;
        for (String selector : PRODUCT_CARD_SELECTORS) {
            cards = doc.select(selector);
            if (cards.size() >= 1) break;
        }

        if (cards == null || cards.isEmpty()) {
            log.debug("列表页 {} 未找到产品卡片容器", pageUrl);
            return result;
        }

        log.debug("列表页 {} 找到 {} 个产品卡片", pageUrl, cards.size());

        for (Element card : cards) {
            try {
                // 提取名称
                String name = null;
                for (String sel : CARD_NAME_SELECTORS) {
                    Element el = card.selectFirst(sel);
                    if (el != null) {
                        String text = el.text().trim();
                        if (!text.isBlank() && text.length() > 1) {
                            name = text;
                            break;
                        }
                    }
                }
                if (name == null || name.isBlank()) continue;

                // 名称黑名单检查
                if (isBlacklistedName(name)) continue;

                // 判断是否像产品名（过滤导航链接等无关内容）
                if (name.length() < 3 || name.length() > 200) continue;
                if (name.equalsIgnoreCase("home") || name.equalsIgnoreCase("products")
                        || name.equalsIgnoreCase("contact") || name.equalsIgnoreCase("about")
                        || name.equalsIgnoreCase("next") || name.equalsIgnoreCase("prev")) {
                    continue;
                }

                // 提取产品详情链接
                String detailLink = null;
                for (String sel : CARD_LINK_SELECTORS) {
                    Element a = card.selectFirst(sel);
                    if (a == null) continue;
                    String href = a.absUrl("href");
                    if (!href.isBlank() && !href.equals(pageUrl)) {
                        detailLink = resolveUrl(href);
                        break;
                    }
                }

                // 提取图片
                String imageUrl = null;
                for (String sel : CARD_IMAGE_SELECTORS) {
                    Element img = card.selectFirst(sel);
                    if (img != null) {
                        imageUrl = img.absUrl("src");
                        if (imageUrl.isBlank()) imageUrl = img.absUrl("data-src");
                        if (imageUrl.isBlank()) imageUrl = img.absUrl("srcset");
                        if (imageUrl.isBlank()) imageUrl = img.attr("data-ks-lazyload");
                        if (imageUrl.isBlank()) imageUrl = img.attr("data-original");
                        if (!imageUrl.isBlank()) break;
                    }
                }

                // 过滤认证徽章假图（会被误抓为产品图）
                if (imageUrl != null && imageUrl.contains("H118ee5207f09449ab9912b17e2b59bebw")) {
                    imageUrl = null;
                }
                // 降级方案：尝试从 a.product-image img 或 .img-box img 获取真实产品图
                if (imageUrl == null || imageUrl.isBlank()) {
                    Element productImg = card.selectFirst("a.product-image img, .img-box img");
                    if (productImg != null) {
                        imageUrl = productImg.absUrl("src");
                        if (imageUrl.isBlank()) imageUrl = productImg.attr("src");
                        if (!imageUrl.isBlank()) imageUrl = resolveUrl(imageUrl);
                    }
                }

                // 提取价格
                String price = null;
                for (String sel : PRODUCT_PRICE_SELECTORS) {
                    Element el = card.selectFirst(sel);
                    if (el != null && !el.text().isBlank()) {
                        price = el.text().trim();
                        break;
                    }
                }

                ScrapedProduct product = ScrapedProduct.builder()
                        .name(name)
                        .url(detailLink != null ? detailLink : pageUrl)
                        .imageUrl(imageUrl)
                        .price(price)
                        .scrapedAt(LocalDateTime.now())
                        .build();

                result.add(product);

            } catch (Exception e) {
                log.trace("提取产品卡片失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * 分类页面链接：区分列表页和产品详情页
     */
    private void classifyUrls(org.jsoup.nodes.Document doc, Set<String> listPages, Set<String> productUrls) {
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.absUrl("href");
            if (href.isBlank() || !href.startsWith("http")) continue;
            if (visitedUrls.contains(href)) continue;
            if (href.contains("#") || href.contains("tel:") || href.contains("mailto:")) continue;
            if (href.contains("facebook") || href.contains("twitter") || href.contains("linkedin")
                    || href.contains("youtube") || href.contains("instagram")) continue;
            if (isBlacklistedUrl(href)) continue;

            String lower = href.toLowerCase();
            boolean isList = false;
            for (String pattern : LIST_PAGE_PATTERNS) {
                if (lower.contains(pattern)) {
                    listPages.add(resolveUrl(href));
                    isList = true;
                    break;
                }
            }
            if (isList) continue;

            // 判断是否为产品详情链接
            for (String pattern : PRODUCT_DETAIL_PATTERNS) {
                if (lower.contains(pattern) &&
                        !lower.contains("productlist") && !lower.contains("product-list")
                        && !lower.contains("productgrouplist")
                        && !lower.contains("category") && !lower.contains("catalog")) {
                    productUrls.add(resolveUrl(href));
                    break;
                }
            }
        }

        // 如果没有任何发现，加所有可能的页面入候选
        if (listPages.isEmpty()) {
            for (Element link : links) {
                String href = link.absUrl("href");
                String lower = href.toLowerCase();
                if (lower.endsWith(".html") && href.contains("displaystandpop")) {
                    listPages.add(resolveUrl(href));
                }
            }
        }
    }

    /**
     * 从列表页发现 Alibaba 品类页链接
     */
    private Set<String> discoverCategoryUrls(org.jsoup.nodes.Document doc) {
        Set<String> urls = new LinkedHashSet<>();
        Elements links = doc.select("a[href*='productgrouplist']");

        for (Element link : links) {
            String href = link.absUrl("href");
            if (!href.isBlank() && !visitedUrls.contains(href)
                    && !href.equals(BASE_URL) && !href.equals(BASE_URL + "/")) {
                urls.add(resolveUrl(href));
            }
        }
        return urls;
    }

    /**
     * 构造分页 URL（用于 JS 渲染的分页组件，尝试多种常见 URL 模式）
     */
    private Set<String> constructPaginationUrls(String baseUrl, int maxPages) {
        Set<String> urls = new LinkedHashSet<>();
        // 清理 baseUrl 已有的分页参数
        String cleanUrl = baseUrl.replaceAll("[?&]page(numb?er?|num)?(=|/)\\d+", "")
                .replaceAll("[?&]pageSize=\\d+", "");
        // 去掉末尾多余的 ? 和 &
        cleanUrl = cleanUrl.replaceAll("[?&]$", "");

        String[][] patterns = {
                {cleanUrl + "?page=%d", "?page="},
                {cleanUrl + "?pageNum=%d", "?pageNum="},
                {cleanUrl + "?page_number=%d", "?page_number="},
                {cleanUrl + "&page=%d", "&page="},
                {cleanUrl + "/page/%d", "/page/"},
        };

        for (String[] pattern : patterns) {
            // 先试第2页是否有效
            String testUrl = String.format(pattern[0], 2);
            if (visitedUrls.contains(testUrl)) continue;

            try {
                org.jsoup.nodes.Document testDoc = fetchPage(testUrl);
                if (testDoc == null) continue;

                // 检查是否有产品卡片（页面有效）
                for (String sel : PRODUCT_CARD_SELECTORS) {
                    if (testDoc.select(sel).size() >= 1) {
                        log.info("分页模式有效: {}, 生成最多{}页", pattern[1], maxPages);
                        visitedUrls.add(testUrl);
                        urls.add(testUrl);

                        // 生成更多页
                        for (int p = 3; p <= Math.min(maxPages, MAX_PAGES); p++) {
                            urls.add(String.format(pattern[0], p));
                        }
                        return urls; // 找到有效模式，立即返回
                    }
                }
                visitedUrls.add(testUrl); // 标记已访问
            } catch (Exception e) {
                log.debug("分页构造测试失败: {} - {}", testUrl, e.getMessage());
            }
        }

        return urls;
    }

    /**
     * 从页面发现产品详情页链接
     */
    private Set<String> discoverProductUrls(org.jsoup.nodes.Document doc) {
        Set<String> urls = new LinkedHashSet<>();
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String href = link.absUrl("href");
            if (href.isBlank() || !href.startsWith("http")) continue;
            if (visitedUrls.contains(href)) continue;
            if (href.contains("#") || href.equals(BASE_URL) || href.equals(BASE_URL + "/")) continue;
            if (isBlacklistedUrl(href)) continue;  // URL 黑名单

            String lower = href.toLowerCase();

            // 跳过分类/列表页
            boolean isListPage = false;
            for (String p : LIST_PAGE_PATTERNS) {
                if (lower.contains(p)) { isListPage = true; break; }
            }
            if (isListPage) continue;

            // 匹配产品详情页
            boolean isProduct = false;
            for (String pattern : PRODUCT_DETAIL_PATTERNS) {
                if (lower.contains(pattern)) { isProduct = true; break; }
            }
            if (isProduct || link.text().length() > 5) {
                urls.add(resolveUrl(href));
            }
        }
        return urls;
    }

    /**
     * 发现分页链接
     */
    private Set<String> discoverPaginationUrls(org.jsoup.nodes.Document doc, String baseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        Elements pagination = doc.select(
                ".next-pagination-item a, .next-pagination a, " +
                ".pagination a, .pager a, .page-numbers a, ul.pagination li a, .pages a");

        for (Element link : pagination) {
            String href = link.absUrl("href");
            String text = link.text().trim();
            // 跳过当前页（通常不可点击）
            if (!href.isBlank() && !href.equals(baseUrl) && !visitedUrls.contains(href)
                    && !link.hasClass("current") && !link.hasClass("active")) {
                // 跳过"上一页""下一页"文本跳转
                if (text.equalsIgnoreCase("prev") || text.equalsIgnoreCase("previous")
                        || text.equalsIgnoreCase("next")) continue;
                // 只接受数字页码或正常页码链接
                if (text.matches("\\d+") || href.contains("page=") || href.contains("page-")) {
                    urls.add(resolveUrl(href));
                }
            }
        }

        // 也检查带 "page" 参数的链接
        if (urls.isEmpty()) {
            for (Element link : doc.select("a[href*='page='], a[href*='page-'], a[href*='page/']")) {
                String href = link.absUrl("href");
                if (!href.isBlank() && !href.equals(baseUrl) && !visitedUrls.contains(href)) {
                    urls.add(resolveUrl(href));
                }
            }
        }

        // 通用兜底：找数字文本链接
        if (urls.isEmpty()) {
            for (Element link : doc.select("a[href]")) {
                String text = link.text().trim();
                String href = link.absUrl("href");
                if (text.matches("\\d+") && !href.equals(baseUrl) && !visitedUrls.contains(href)
                        && !link.hasClass("current") && !link.hasClass("active")) {
                    urls.add(resolveUrl(href));
                }
            }
        }

        log.info("分页发现: 基准={}, 页码数={}", baseUrl, urls.size());
        return urls;
    }

    /**
     * 去重检查
     */
    private boolean isDuplicate(ScrapedProduct product) {
        return products.stream().anyMatch(p ->
                Objects.equals(p.getName(), product.getName()) &&
                        Objects.equals(p.getUrl(), product.getUrl()));
    }

    /**
     * 获取页面文档
     */
    /**
     * 用 Java 原生 HttpClient 抓取（绕过 Jsoup 被封的问题）
     */
    private org.jsoup.nodes.Document fetchWithHttpClient(String url) {
        try {
            // 信任所有 SSL 证书
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sslCtx = javax.net.ssl.SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .sslContext(sslCtx)
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", getReferer(url))
                    .timeout(java.time.Duration.ofSeconds(TIMEOUT_MS / 1000))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return Jsoup.parse(response.body(), url);
            }
        } catch (Exception e) {
            log.warn("HttpClient 抓取失败: {} - {}", url, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return null;
    }

    private org.jsoup.nodes.Document fetchPage(String url) {
        try {
            // 信任所有 SSL 证书（处理某些 CDN/防火墙的 SSL 问题）
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sslCtx = javax.net.ssl.SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());
            
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", getReferer(url))
                    .sslSocketFactory(sslCtx.getSocketFactory())
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
        } catch (Exception e) {
            log.warn("无法访问页面: {} - {}", url, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return null;
        }
    }

    private String getReferer(String url) {
        if (url.contains("displaystandpop.com")) return "https://www.google.com/";
        if (url.contains("alibaba.com")) return "http://www.displaystandpop.com/productlist.html";
        return "https://www.google.com/";
    }

    private String extractText(org.jsoup.nodes.Document doc, String[] selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private String extractImageUrl(org.jsoup.nodes.Document doc) {
        for (String selector : PRODUCT_IMAGE_SELECTORS) {
            if (selector.startsWith("meta[")) {
                // Open Graph meta tag
                Element meta = doc.selectFirst(selector);
                if (meta != null) {
                    String content = meta.attr("content");
                    if (!content.isBlank()) return content;
                }
                continue;
            }
            Element img = doc.selectFirst(selector);
            if (img != null) {
                String src = img.absUrl("src");
                if (src.isBlank()) src = img.absUrl("data-src");
                if (src.isBlank()) src = img.absUrl("srcset");
                if (src.isBlank()) src = img.attr("data-ks-lazyload");
                if (src.isBlank()) src = img.attr("data-original");
                if (!src.isBlank()) return src;
            }
        }
        // 兜底：找第一个尺寸 >= 200 的大图
        Elements imgs = doc.select("img[src$=.jpg], img[src$=.png], img[src$=.webp], img[src$=.jpeg]");
        for (Element img : imgs) {
            String src = img.absUrl("src");
            if (src.isBlank()) src = img.absUrl("data-src");
            if (src.isBlank()) src = img.absUrl("srcset");
            if (src.isBlank()) src = img.attr("data-ks-lazyload");
            if (src.isBlank()) src = img.attr("data-original");
            if (src.isBlank()) continue;
            int w = 0;
            try { w = Integer.parseInt(img.attr("width")); } catch (NumberFormatException ignored) {}
            if (w >= 200 || (w == 0 && !src.isBlank())) return src;
        }
        return null;
    }

    private String resolveUrl(String url) {
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        try {
            return new URI(BASE_URL).resolve(url).toString();
        } catch (Exception e) {
            return BASE_URL + (url.startsWith("/") ? url : "/" + url);
        }
    }

    /**
     * URL 黑名单检查
     */
    private boolean isBlacklistedUrl(String url) {
        if (url == null || url.isBlank()) return true;
        String lower = url.toLowerCase();
        for (String pattern : URL_BLACKLIST) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    /**
     * 名称/标题黑名单检查
     */
    private boolean isBlacklistedName(String name) {
        if (name == null || name.isBlank()) return true;
        String lower = name.toLowerCase().trim();
        for (String pattern : NAME_BLACKLIST) {
            if (lower.contains(pattern)) return true;
        }
        // 名称过短或过长也不太像产品
        if (name.length() < 3 || name.length() > 200) return true;
        return false;
    }

    private boolean isBadgeImage(String imageUrl) {
        if (imageUrl == null) return false;
        // 过滤阿里认证徽章图
        return imageUrl.contains("H118ee5207f09449ab9912b17e2b59bebw");
    }

    private void saveProductsToFile() {
        // 1. 保存到 MySQL
        try {
            for (ScrapedProduct sp : products) {
                // 按 sku + name 去重
                List<Product> existing = productRepo.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
                        sp.getName() != null ? sp.getName().substring(0, Math.min(sp.getName().length(), 200)) : "",
                        sp.getSku() != null ? sp.getSku() : "___none___");
                if (existing == null || existing.isEmpty()) {
                    productRepo.save(Product.builder()
                            .name(sp.getName())
                            .url(sp.getUrl())
                            .imageUrl(sp.getImageUrl())
                            .price(sp.getPrice())
                            .sku(sp.getSku())
                            .description(sp.getDescription())
                            .category(sp.getCategory())
                            .enabled(true)
                            .build());
                }
            }
            log.info("产品数据已保存到 MySQL: {} 条", products.size());
        } catch (Exception e) {
            log.error("保存产品到 MySQL 失败", e);
        }

        // 2. 同时保存到 JSON 文件（备份）
        try {
            Path knowledgeDir = Paths.get(SAVE_DIR);
            Files.createDirectories(knowledgeDir);

            String jsonPath = knowledgeDir.resolve("products.json").toString();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonPath), products);
            log.info("产品数据已保存到: {}", jsonPath);

            StringBuilder md = new StringBuilder();
            md.append("# JC Display 产品目录\n\n");
            md.append("> 以下数据从官网 ").append(BASE_URL)
                    .append(" 自动抓取，更新时间: ")
                    .append(LocalDateTime.now()).append("\n\n");
            md.append("共 ").append(products.size()).append(" 款产品\n\n");

            for (int i = 0; i < products.size(); i++) {
                md.append("### 产品 ").append(i + 1).append("\n\n");
                md.append(products.get(i).toMarkdown());
            }

            String mdPath = knowledgeDir.resolve("products-catalog.md").toString();
            Files.writeString(Paths.get(mdPath), md.toString());
            log.info("产品知识库文档已保存到: {}", mdPath);

        } catch (IOException e) {
            log.error("保存产品数据失败", e);
        }
    }

    public List<ScrapedProduct> getCachedProducts() {
        if (!products.isEmpty()) return new ArrayList<>(products);

        // 优先从 MySQL 加载
        try {
            List<com.ecommerce.agent.model.Product> dbProducts = productRepo.findByEnabledTrueOrderByNameAsc();
            if (!dbProducts.isEmpty()) {
                for (com.ecommerce.agent.model.Product dbp : dbProducts) {
                    products.add(ScrapedProduct.builder()
                            .name(dbp.getName())
                            .url(dbp.getUrl())
                            .imageUrl(dbp.getImageUrl())
                            .price(dbp.getPrice())
                            .sku(dbp.getSku())
                            .description(dbp.getDescription())
                            .category(dbp.getCategory())
                            .build());
                }
                return new ArrayList<>(products);
            }
        } catch (Exception e) {
            log.warn("从 MySQL 加载产品失败，尝试文件缓存: {}", e.getMessage());
        }

        // 回退到 JSON 文件缓存
        try {
            Path path = Paths.get(SAVE_DIR, "products.json");
            if (Files.exists(path)) {
                ScrapedProduct[] arr = objectMapper.readValue(path.toFile(), ScrapedProduct[].class);
                products.addAll(Arrays.asList(arr));
            }
        } catch (IOException e) {
            log.warn("读取缓存产品数据失败: {}", e.getMessage());
        }
        return new ArrayList<>(products);
    }

    public List<Document> toDocuments() {
        List<ScrapedProduct> list = getCachedProducts();
        if (list.isEmpty()) return Collections.emptyList();

        List<Document> docs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("# 公司官网产品目录\n\n");
        sb.append("以下是从官网 ").append(BASE_URL).append(" 抓取的最新在售产品信息。\n\n");

        for (ScrapedProduct p : list) {
            sb.append(p.toMarkdown());
        }

        docs.add(Document.from(sb.toString()));
        return docs;
    }

    private ScrapeResult failResult(long start, String msg) {
        return ScrapeResult.builder()
                .totalProducts(products.size())
                .durationMs(System.currentTimeMillis() - start)
                .message(msg)
                .build();
    }

    /**
     * 补全缺失产品图片 — 从列表页提取缩略图
     * 注意：Alibaba 详情页需要登录，无法直接抓取，只能通过 displaystandpop.com 列表页获取缩略图
     */
    public int fillMissingImages() {
        int filled = 0;
        scrapeStatus = "filling images from list pages...";
        try {
            List<com.ecommerce.agent.model.Product> allProducts = productRepo.findAll();
            int totalMissing = 0;
            for (com.ecommerce.agent.model.Product p : allProducts) {
                if (p.getImageUrl() == null || p.getImageUrl().isBlank()) totalMissing++;
            }
            if (totalMissing == 0) {
                log.info("所有产品已有图片，无需补全");
                scrapeStatus = "filling done: all images present";
                return 0;
            }
            log.info("从列表页补全图片: {} 个缺失, {} 个品类", totalMissing, KNOWN_CATEGORY_URLS.length);

            for (String catPath : KNOWN_CATEGORY_URLS) {
                String listUrl = BASE_URL + catPath;
                try {
                    Thread.sleep(REQUEST_DELAY_MS);
                    org.jsoup.nodes.Document doc = fetchPage(listUrl);
                    if (doc == null) continue;
                    List<ScrapedProduct> cardProducts = extractProductsFromListPage(doc, listUrl);

                    for (ScrapedProduct sp : cardProducts) {
                        if (sp.getUrl() == null || sp.getUrl().isBlank()) continue;
                        if (sp.getImageUrl() == null || sp.getImageUrl().isBlank()) continue;
                        if (isBadgeImage(sp.getImageUrl())) continue;

                        // URL 匹配：同时尝试 http 和 https 版本
                        Optional<com.ecommerce.agent.model.Product> opt = productRepo.findByUrl(sp.getUrl());
                        if (opt.isEmpty() && sp.getUrl().startsWith("http://")) {
                            opt = productRepo.findByUrl(sp.getUrl().replace("http://", "https://"));
                        }
                        if (opt.isEmpty() && sp.getUrl().startsWith("https://")) {
                            opt = productRepo.findByUrl(sp.getUrl().replace("https://", "http://"));
                        }

                        if (opt.isPresent()) {
                            com.ecommerce.agent.model.Product p = opt.get();
                            if (p.getImageUrl() == null || p.getImageUrl().isBlank()) {
                                p.setImageUrl(sp.getImageUrl());
                                productRepo.save(p);
                                filled++;
                                scrapeProgress = filled;
                                scrapeStatus = "filling: " + filled + "/" + totalMissing;
                                log.info("补图 [{}/{}]: {}", filled, totalMissing, p.getName());
                            }
                        }
                    }
                    if (filled >= totalMissing) break;
                } catch (Exception e) {
                    log.warn("列表页图片提取失败: {} - {}", listUrl, e.getMessage());
                }
            }

            scrapeStatus = "filling done: " + filled + " / " + totalMissing;
            log.info("图片补全完成: {}/{} 个产品", filled, totalMissing);
        } catch (Exception e) {
            scrapeStatus = "filling error";
            log.warn("图片补全过程异常: {}", e.getMessage());
        }
        return filled;
    }
}
