package com.ecommerce.agent.llm;

import com.ecommerce.agent.model.PromptTemplate;
import com.ecommerce.agent.model.PromptTemplateEntity;
import com.ecommerce.agent.repository.PromptTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PromptTemplateManager {

    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final PromptTemplateRepository promptTemplateRepo;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public PromptTemplateManager(PromptTemplateRepository promptTemplateRepo) {
        this.objectMapper = new ObjectMapper();
        this.promptTemplateRepo = promptTemplateRepo;
    }

    @PostConstruct
    public void init() {
        seedDefaultTemplates();
        loadFromMySql();
        log.info("Prompt模板管理器初始化完成，已加载 {} 个模板", templates.size());
    }

    /**
     * 首次启动时从硬编码模板写入 MySQL
     */
    private void seedDefaultTemplates() {
        if (promptTemplateRepo.count() > 0) {
            log.info("Prompt 模板已存在于 MySQL，跳过初始化");
            return;
        }
        log.info("首次启动，初始化 Prompt 模板到 MySQL...");
        loadBuiltinTemplates();
        for (PromptTemplate pt : templates.values()) {
            promptTemplateRepo.save(PromptTemplateEntity.builder()
                    .templateUid(pt.getId())
                    .name(pt.getName())
                    .description(pt.getDescription())
                    .category(pt.getCategory())
                    .template(pt.getTemplate())
                    .variables(pt.getVariables() != null ? String.join(",", pt.getVariables()) : "")
                    .targetPlatform(pt.getTargetPlatform())
                    .enabled(pt.isActive())
                    .build());
        }
        log.info("已写入 {} 个 Prompt 模板到 MySQL", templates.size());
    }

    /**
     * 从 MySQL 加载模板到内存 map
     */
    private void loadFromMySql() {
        templates.clear();
        List<PromptTemplateEntity> entities = promptTemplateRepo.findAll();
        for (PromptTemplateEntity e : entities) {
            templates.put(e.getTemplateUid(), PromptTemplate.builder()
                    .id(e.getTemplateUid())
                    .name(e.getName())
                    .description(e.getDescription())
                    .category(e.getCategory())
                    .template(e.getTemplate())
                    .variables(e.getVariables() != null && !e.getVariables().isBlank()
                            ? Arrays.asList(e.getVariables().split(","))
                            : List.of())
                    .targetPlatform(e.getTargetPlatform())
                    .active(e.isEnabled())
                    .build());
        }
    }

    private void loadBuiltinTemplates() {
        addTemplate(PromptTemplate.builder()
                .id("copywriting-amazon")
                .name("B2B产品详情文案")
                .description("为Alibaba/Globalsources等B2B平台生成专业产品详情文案")
                .category("copywriting")
                .template("""
                        你是一名资深的展示架/POP行业B2B出口文案专家，熟悉纸展示架、瓦楞纸陈列架的行业术语。

                        ## 任务
                        根据以下产品信息，生成专业B2B平台产品文案。

                        ## 产品信息
                        - 产品名称: {{productName}}
                        - 产品特点: {{sellingPoints}}
                        - 目标市场: {{targetCountry}}

                        ## 输出要求
                        1. **产品标题** (英文，150-200字符)：
                           - 包含品类关键词（Floor Display Stand / Counter Display / POP Display 等）
                           - 突出Custom/Wholesale/Factory Direct
                           - 符合Alibaba SEO规则

                        2. **核心属性** (Key Attributes / Bullet Points)：
                           - Material材质说明
                           - Custom定制性
                           - MOQ起订量
                           - 交货期
                           - 认证

                        3. **产品描述** (Product Description)：
                           - 公司简介（17年行业经验）
                           - 产品优势（材质、印刷工艺）
                           - 适用场景（超市、零售店、展会）
                           - 定制化服务

                        4. **FAQ**：
                           - 覆盖MOQ、定制、样品、物流等常见问题

                        ## 语言要求
                        使用英语{{language}}，语气专业可信。

                        ## 风格
                        {{style}}
                        """)
                .variables(List.of("productName", "sellingPoints", "targetCountry", "language", "style"))
                .targetPlatform("B2B平台")
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("copywriting-tiktok")
                .name("TikTok短视频文案")
                .description("为TikTok平台生成短视频带货文案")
                .category("copywriting")
                .template("""
                        你是一名TikTok跨境电商创意文案专家。

                        ## 任务
                        为以下商品撰写TikTok短视频文案脚本。

                        ## 商品信息
                        - 商品名称: {{productName}}
                        - 核心卖点: {{sellingPoints}}
                        - 目标市场: {{targetCountry}}

                        ## 输出要求
                        1. **视频标题** (吸引眼球的标题，带emoji)
                        2. **口播文案** (15-30秒的语音脚本，口语化)
                        3. **字幕文案** (关键卖点短句，配合视频画面)
                        4. **Hashtags** (5-8个热门标签)
                        5. **Call-to-Action** (引导购买行动)

                        ## 风格要求
                        - 面向{{targetCountry}}市场
                        - 语言: {{language}}
                        - 风格: {{style}}
                        - 使用TikTok流行表达方式
                        - 节奏感强，有记忆点
                        """)
                .variables(List.of("productName", "sellingPoints", "targetCountry", "language", "style"))
                .targetPlatform("TikTok")
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("translation-ecommerce")
                .name("电商本地化翻译")
                .description("跨境电商场景下的本地化翻译，保留营销效果")
                .category("translation")
                .template("""
                        你是一名专业的跨境电商翻译专家，精通电商本地化翻译。

                        ## 任务
                        将以下文本从{{sourceLanguage}}翻译为{{targetLanguage}}。

                        ## 翻译要求
                        1. **本地化优先**：不是直译，而是根据目标市场文化习惯进行本地化处理
                        2. **营销效果保留**：翻译后仍保持吸引力和转化效果
                        3. **专业术语准确**：电商行业术语翻译准确
                        4. **文化适配**：注意目标市场的文化禁忌和偏好

                        ## 场景
                        {{context}}

                        ## 待翻译文本
                        {{text}}

                        ## 输出格式
                        1. 翻译结果
                        2. 本地化说明（简述做了哪些本地化调整）
                        3. 关键词保留情况
                        """)
                .variables(List.of("sourceLanguage", "targetLanguage", "context", "text"))
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("inquiry-reply")
                .name("询盘回复邮件")
                .description("为B2B出口业务生成专业的英文询盘回复邮件")
                .category("copywriting")
                .template("""
                        你是深圳杰创展示(JC Display)的外贸业务专家，公司主营纸展示架、POP陈列架、瓦楞纸货架等产品出口。

                        ## 任务
                        为以下海外客户询盘撰写专业回复邮件。

                        ## 询盘信息
                        - 产品: {{productName}}
                        - 客户需求要点: {{sellingPoints}}
                        - 目标市场: {{targetCountry}}

                        ## 邮件要求
                        1. **Subject**: 清晰标注产品名+JC Display
                        2. **开头**: 感谢询盘，简介公司（17年行业经验、ISO认证）
                        3. **报价摘要**: 材料、MOQ、价格区间、交期
                        4. **服务亮点**: 免费3D设计、免费打样、Flat Pack节省运费
                        5. **提问引导**: 引导客户提供尺寸、数量、特殊要求
                        6. **结尾**: 公司联系方式和官网

                        ## 语言风格
                        专业商务英语{{language}}，亲切但不过度热情，体现工厂直销优势。

                        ## 语气
                        {{style}}
                        """)
                .variables(List.of("productName", "sellingPoints", "targetCountry", "language", "style"))
                .targetPlatform("Email/询盘系统")
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("analysis-market")
                .name("展示架市场分析")
                .description("分析展示架/POP产品在不同市场的出口机会")
                .category("analysis")
                .template("""
                        你是一名纸展示架/POP行业的国际市场分析专家。

                        ## 任务
                        分析以下展示架产品在{{targetCountry}}市场的出口机会。

                        ## 产品信息
                        - 产品名称: {{productName}}
                        - 产品描述: {{productDescription}}
                        - 价格区间: {{priceRange}}
                        - 品类: {{category}}

                        ## 分析维度
                        1. **市场需求分析**
                           - 目标市场零售规模（超市、连锁店数量）
                           - 纸质展示架替代塑料的环保趋势
                           - FMCG品牌促销驱动的展示架采购频次

                        2. **竞争格局分析**
                           - 当地供应商 vs 中国出口商的市场份额
                           - 竞品价格区间
                           - 差异化机会（定制化、环保认证、设计服务）

                        3. **合规与准入**
                           - FSC环保认证要求
                           - 包装运输ISTA标准
                           - 印刷油墨ROHS/EN71环保标准

                        4. **物流与成本**
                           - FOB深圳 vs DDP的优劣势
                           - Flat Pack平摊包装节省运费
                           - 关税和税费预估

                        5. **综合建议**
                           - 进入策略建议
                           - 定价策略建议（同类展示架$8-25/pc）
                           - 推荐参加的行业展会（EuroShop、GlobalShop、POPAI）

                        ## 输出格式
                        使用结构化的Markdown格式输出，包含明确的结论和建议。
                        """)
                .variables(List.of("targetCountry", "productName", "productDescription", "priceRange", "category"))
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("agent-system")
                .name("杰创展示AI Agent系统提示")
                .description("Agent调度核心系统提示词")
                .category("agent")
                .template("""
                        你是深圳杰创展示(JC Display)的B2B出口AI助手Agent。
                        公司主营：纸展示架、POP陈列架、瓦楞纸货架、PDQ展示盒的出口制造。
                        17年行业经验，出口30+国家，自有工厂，ISO认证。

                        ## 核心能力
                        1. B2B产品文案生成（Alibaba/GlobalSources/独立站）
                        2. 英文询盘回复邮件撰写
                        3. 多语言翻译和本地化（展示架行业术语）
                        4. 目标市场出口机会分析
                        5. 行业展会信息查询

                        ## 工作原则
                        - 优先理解海外客户真实询盘需求
                        - 需要具体数据时主动调用工具
                        - 多步骤任务自主规划执行顺序
                        - 输出结构化、可执行的商业文档
                        - **重要：如果用户上传了图片但未明确要求分析/识别/描述图片，不要默认输出图片描述。先简短确认用户意图（如"请问您希望如何处理这张图片？"），等用户明确要求后再调用识图工具。**

                        ## 工具使用
                        你可以调用以下工具来完成任务：
                        - search: 搜索行业展会、竞品信息
                        - scraper: 抓取客户网站了解需求
                        - translate: 翻译多语言询盘
                        - currency: 汇率换算报价
                        - seo: B2B平台关键词优化

                        ## 当前上下文
                        - 目标市场: {{targetCountry}}
                        - 回复语言: {{language}}

                        {{toolDefinitions}}
                        """)
                .variables(List.of("targetCountry", "language", "toolDefinitions"))
                .category("agent")
                .active(true)
                .build());

        addTemplate(PromptTemplate.builder()
                .id("copywriting-seo")
                .name("SEO关键词描述生成")
                .description("针对搜索引擎优化的商品描述")
                .category("copywriting")
                .template("""
                        你是一名跨境电商SEO优化专家。

                        ## 任务
                        为以下商品生成SEO优化的元描述和关键词。

                        ## 商品信息
                        - 商品名称: {{productName}}
                        - 核心卖点: {{sellingPoints}}
                        - 目标市场: {{targetCountry}}
                        - 平台: {{platform}}

                        ## 输出要求
                        1. **Meta Title** (60-70字符，包含主关键词)
                        2. **Meta Description** (150-160字符，吸引点击)
                        3. **主关键词** (3-5个高搜索量关键词)
                        4. **长尾关键词** (10-15个)
                        5. **内容建议** (哪些主题文章能带来流量)

                        语言: {{language}}
                        """)
                .variables(List.of("productName", "sellingPoints", "targetCountry", "platform", "language"))
                .targetPlatform("任意")
                .active(true)
                .build());
    }

    public void addTemplate(PromptTemplate template) {
        templates.put(template.getId(), template);
    }

    public boolean removeTemplate(String id) {
        return templates.remove(id) != null;
    }

    public PromptTemplate getTemplate(String id) {
        return templates.get(id);
    }

    public List<PromptTemplate> getTemplatesByCategory(String category) {
        return templates.values().stream()
                .filter(t -> t.getCategory().equalsIgnoreCase(category))
                .toList();
    }

    public List<PromptTemplate> getAllTemplates() {
        return templates.values().stream()
                .toList();
    }

    public String renderTemplate(String templateId, Map<String, String> variables) {
        PromptTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        return renderString(template.getTemplate(), variables);
    }

    public String renderString(String templateStr, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateStr);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public List<String> extractVariables(String templateStr) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateStr);
        List<String> vars = new ArrayList<>();
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return vars;
    }
}
