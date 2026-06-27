import type { LucideIcon } from "lucide-react"
import {
  BadgeCheck,
  Boxes,
  FileSearch,
  Languages,
  MailCheck,
  Route,
  ShieldCheck,
  Truck,
} from "lucide-react"

export interface TradeScenario {
  id: string
  title: string
  subtitle: string
  icon: LucideIcon
  owner: string
  stage: string
  impact: string
  actions: string[]
  prompt: string
}

export const tradeScenarios: TradeScenario[] = [
  {
    id: "inquiry-triage",
    title: "询盘识别与分层",
    subtitle: "识别客户真实采购意图、预算、应用场景和缺失信息。",
    icon: FileSearch,
    owner: "销售",
    stage: "收到询盘后 10 分钟内",
    impact: "优先处理高价值客户，减少无效往返",
    actions: ["提取产品与数量", "判断客户阶段", "生成追问清单"],
    prompt: "请按外贸销售视角分析这条询盘：识别客户国家、行业、产品需求、数量、预算迹象、紧急程度、风险点，并给出英文回复草稿。",
  },
  {
    id: "quote-package",
    title: "报价包生成",
    subtitle: "把产品、MOQ、包装、交期、贸易条款组织成可发送的报价说明。",
    icon: BadgeCheck,
    owner: "销售经理",
    stage: "客户需求明确后",
    impact: "报价更完整，降低客户只看单价的概率",
    actions: ["整理报价假设", "补齐贸易条款", "生成邮件与报价备注"],
    prompt: "请帮我生成一份外贸报价包：包含报价假设、产品规格、MOQ、样品费、打样周期、量产交期、包装方式、FOB/DDP差异说明和英文邮件正文。",
  },
  {
    id: "sample-proof",
    title: "样品与打样跟进",
    subtitle: "围绕 3D 设计、打样确认、寄样物流和客户反馈形成闭环。",
    icon: Boxes,
    owner: "项目跟单",
    stage: "样品费确认后",
    impact: "让客户知道每一步卡在哪里、下一步要给什么",
    actions: ["列出打样节点", "生成客户提醒", "整理内部交付清单"],
    prompt: "请为这个客户生成样品跟进计划：列出内部设计、结构确认、印刷文件、打样、拍照确认、寄样、签收后反馈的时间线和英文跟进话术。",
  },
  {
    id: "listing-localization",
    title: "平台上架本地化",
    subtitle: "针对 Alibaba、Global Sources、独立站输出标题、属性、FAQ 和 SEO 词。",
    icon: Languages,
    owner: "运营",
    stage: "新品上架前",
    impact: "让 POP Display 产品更贴合买家搜索方式",
    actions: ["生成标题结构", "提炼属性", "输出 FAQ 与关键词"],
    prompt: "请为这个 POP display 产品生成 B2B 平台上架资料：英文标题、核心属性、卖点 bullet、产品描述、FAQ、关键词和适合的类目建议。",
  },
  {
    id: "compliance-logistics",
    title: "合规与物流检查",
    subtitle: "检查 FSC、油墨环保、ISTA 包装、HS Code、运输方式和目的港风险。",
    icon: Truck,
    owner: "供应链",
    stage: "下单前风控",
    impact: "提前暴露认证、包装、清关和运费问题",
    actions: ["检查认证缺口", "评估包装方式", "生成客户确认项"],
    prompt: "请从外贸合规和物流角度检查这个订单：列出 FSC、油墨环保、包装测试、HS Code、贸易条款、目的港清关、DDP 风险和需要客户确认的问题。",
  },
  {
    id: "after-sales",
    title: "售后与复购激活",
    subtitle: "围绕签收、陈列效果、补单、季节促销和新项目推荐持续跟进。",
    icon: MailCheck,
    owner: "客户成功",
    stage: "客户收货后 7-30 天",
    impact: "把一次订单变成季度采购节奏",
    actions: ["生成签收问候", "挖掘复购机会", "推荐下一批产品"],
    prompt: "请基于外贸客户成功视角生成售后复购跟进方案：包含签收确认、陈列效果询问、问题处理、复购提醒、新品推荐和英文邮件模板。",
  },
  {
    id: "risk-radar",
    title: "订单风险雷达",
    subtitle: "聚合付款、设计稿、数量变更、交期压缩和客户信用风险。",
    icon: ShieldCheck,
    owner: "负责人",
    stage: "订单执行中",
    impact: "让 Agent 主动提示哪些任务会影响交付",
    actions: ["列出红黄绿风险", "生成内部处理建议", "整理客户沟通口径"],
    prompt: "请把这个外贸订单按风险雷达分析：付款、设计稿、数量、交期、包装、物流、客户沟通逐项打分，并给出内部动作和客户沟通话术。",
  },
  {
    id: "route-to-agent",
    title: "Agent 工具调度",
    subtitle: "根据任务自动建议是否调用知识库、产品库、翻译、汇率、识图或微信触达。",
    icon: Route,
    owner: "AI Agent",
    stage: "复杂任务拆解时",
    impact: "让 AI 不只回答，还参与执行链路",
    actions: ["判断所需工具", "拆解执行步骤", "输出可追踪结果"],
    prompt: "请像外贸 AI Agent 一样拆解这个任务：先判断要调用哪些工具，再给出执行顺序、需要补充的信息、最终交付物格式和可直接发送给客户的内容。",
  },
]

export const tradePipeline = [
  { label: "线索", value: "Inquiry", hint: "识别客户、产品、国家、预算" },
  { label: "报价", value: "Quote", hint: "价格、MOQ、样品费、贸易条款" },
  { label: "打样", value: "Sample", hint: "结构、设计稿、印刷文件、物流" },
  { label: "订单", value: "Order", hint: "付款、排产、验货、出运" },
  { label: "复购", value: "Reorder", hint: "售后、陈列反馈、下一季计划" },
]
