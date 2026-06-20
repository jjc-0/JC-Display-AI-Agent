package com.ecommerce.agent.rag;

import dev.langchain4j.data.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * JC Display 知识库文档 — 公司业务核心知识
 *
 * 注意：这些是初始化种子数据。首次启动后会自动写入 MySQL 的 knowledge_documents 表。
 * 如需修改知识库内容，请直接更新 MySQL 中对应的记录，或调用 POST /api/agent/knowledge/reload 重新索引。
 */
public final class KnowledgeDocuments {

    private KnowledgeDocuments() {}

    public static List<Document> getAllDocuments() {
        List<Document> docs = new ArrayList<>();
        docs.add(companyInfo());
        docs.add(productSpecs());
        docs.add(marketAnalysis());
        docs.add(complianceAndCertification());
        docs.add(logisticsAndShipping());
        docs.add(tradeShowsAndExhibitions());
        docs.add(industryTerminology());
        docs.add(b2bPlatformOptimization());
        docs.add(emailInquiryTemplates());
        docs.add(localizationGuide());
        return docs;
    }

    private static Document companyInfo() {
        return Document.from("""
                ## 公司信息
                深圳杰创展示有限公司 (Shenzhen JC Display Ltd) 是一家专业的纸展示架/POP陈列架制造商。
                主营产品：瓦楞纸展示架、POP陈列架、PDQ展示盒、纸货架、Counter Display、Pallet Display。
                出口国家和地区：美国、英国、德国、法国、日本、韩国、澳大利亚、加拿大等。
                官网: www.displaystandpop.com
                核心优势: 自有工厂、免费3D设计、免费打样、平摊包装节省运费。
                行业定位: B2B出口制造，FOB深圳为主要贸易方式。
                """);
    }

    private static Document productSpecs() {
        return Document.from("""
                ## 产品规格与技术参数
                材质: 铜版纸/灰板纸 + B/E/F楞瓦楞纸板。
                印刷: 4C CMYK胶版印刷，支持高光/哑光覆膜。
                表面处理: 光面覆膜、哑面覆膜、UV局部上光。
                结构类型: Floor Display Stand(落地展示架)、Counter Display(柜台展示架)、
                Pallet Display(托盘展示架)、PDQ Display(快速陈列盒)、Dump Bin(散装陈列箱)、
                Shelf Ready Packaging(即上架包装)。
                定制程度: 尺寸、形状、颜色、印刷完全可定制。
                MOQ起订量: 按设计不同，可协商。
                生产周期: 按订单量不同。
                包装方式: Flat Pack平摊包装。
                适用场景: 超市、便利店、药妆店、零售店、品牌促销、贸易展会。
                """);
    }

    private static Document marketAnalysis() {
        return Document.from("""
                ## 目标市场分析
                美国市场: 全球最大POP展示架消费市场，超市连锁需求旺盛。FMCG品牌商季度促销驱动稳定需求。
                日本市场: 便利店文化发达，对展示架精细度和印刷质量要求极高。
                英国市场: 环保法规严格，FSC认证为基本准入要求。
                德国市场: 欧洲最大经济体，零售业发达。环保标准高，ISTA包装认证重要。
                韩国市场: 美妆护肤品类展示架需求大。偏好时尚设计感，对色彩和印刷效果要求高。
                澳大利亚市场: 零售连锁发达，对包装环保性要求高。
                """);
    }

    private static Document complianceAndCertification() {
        return Document.from("""
                ## 合规与认证要求
                FSC认证: 森林管理委员会认证，证明纸张原料来自可持续管理森林。欧美大型零售商的常见要求。
                ISTA认证: 国际安全运输协会包装标准。涵盖运输包装的跌落、振动、压缩测试。
                ROHS环保标准: 限制有害物质指令，影响印刷油墨选择。
                食品级认证: 如需直接接触食品的展示架，需符合FDA或EU 1935/2004食品接触材料标准。
                """);
    }

    private static Document logisticsAndShipping() {
        return Document.from("""
                ## 物流与运输
                主要港口: 深圳盐田港、蛇口港。FOB深圳为常见贸易条款。
                海运时效: 美国西海岸15-20天，英国25-30天，日本5-7天。
                包装方式: Flat Pack平摊包装，可大幅减少体积和运费。
                运输注意事项: 瓦楞纸展示架在运输中需防潮，建议使用防潮袋+干燥剂。
                """);
    }

    private static Document tradeShowsAndExhibitions() {
        return Document.from("""
                ## 行业展会
                参加的主要展会: Canton Fair(广交会), Global Sources(环球资源), Ambiente Frankfurt, EuroShop。
                这些展会是接触国际买家的重要渠道，展示公司最新产品和设计能力。
                """);
    }

    private static Document industryTerminology() {
        return Document.from("""
                ## 行业术语
                POP: Point of Purchase，购买点陈列
                PDQ: Product Display Quickly，快速陈列产品
                FSD: Floor Standing Display，落地式展示架
                CTD: Counter Top Display，柜台展示架
                SRP: Shelf Ready Packaging，即上架包装
                FSDU: Free Standing Display Unit，独立式陈列单元
                MOQ: Minimum Order Quantity，最小起订量
                FOB: Free On Board，船上交货价
                CMYK: Cyan/Magenta/Yellow/Black 四色印刷
                """);
    }

    private static Document b2bPlatformOptimization() {
        return Document.from("""
                ## B2B平台优化建议
                Alibaba国际站: 关键词需包含材质、功能、定制能力。产品标题优化: 品牌词+产品词+材质+用途。
                使用优质白底图+多角度展示图。描述中突出工厂直供、定制能力、认证信息。
                及时回复率高可提升店铺权重。建议24小时内回复所有询盘。
                """);
    }

    private static Document emailInquiryTemplates() {
        return Document.from("""
                ## 询盘回复要点
                首次询盘回复应包含: 感谢+公司介绍+产品报价+MOQ+交期+样品政策+联系方式。
                高价值询盘特征: 明确采购量、提供公司信息、询问具体产品规格。
                回复建议: 提供阶梯报价，附产品目录PDF，强调定制能力和认证资质。
                """);
    }

    private static Document localizationGuide() {
        return Document.from("""
                ## 目标市场本地化指南
                美国: 使用Imperial单位(inches/lbs)，报价以USD为主。客户期望快速响应(24h内)。
                日本: 使用Metric+Japanese单位，报价可USD或JPY。日本客户注重细节和长期关系。
                德国: 报价EUR，注重认证文件。德国客户对产品质量和交期要求严格。
                英国: 报价GBP或USD，注重环保认证FSC。
                """);
    }
}
