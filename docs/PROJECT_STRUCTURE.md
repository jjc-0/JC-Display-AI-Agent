# 项目结构说明

这是一个面向外贸平台的 AI Agent 项目，后端负责 Agent、RAG、工具调度和业务 API，前端负责业务工作台、对话、知识库和运营页面。

## 顶层目录

| 目录 | 用途 |
| --- | --- |
| `backend/` | Spring Boot 后端，包含 Agent Runtime、RAG、工具、控制器、服务和数据模型 |
| `frontend/` | React + Vite 前端，包含页面、布局、UI 基础组件和业务模块 |
| `scripts/` | 本地自动化脚本，例如 JC claw / WeChat bridge 启动脚本 |
| `pictures/` | 原始图片素材 |
| `uploads/` | 本地上传文件，不建议提交业务无关临时图片 |
| `docs/` | 项目维护文档和结构说明 |

## 前端约定

| 路径 | 用途 |
| --- | --- |
| `src/App.tsx` | 应用入口，只负责路由框架、登录保护和路由加载状态 |
| `src/config/routes.tsx` | 受保护页面路由表，页面使用懒加载降低首包体积 |
| `src/config/navigation.tsx` | 侧边栏导航配置，新增页面优先在这里登记入口 |
| `src/components/ui/` | 基础 UI 组件，保持通用，不写外贸业务逻辑 |
| `src/components/layout/` | 应用壳层、侧边栏、顶部栏 |
| `src/features/` | 可复用业务模块，外贸场景、数据模型、工作流片段放这里 |
| `src/pages/` | 路由页面，只做页面组装和少量状态协调 |
| `src/lib/` | API 客户端、工具函数、通用适配层 |

## 外贸 Agent 业务边界

外贸场景新增内容优先放到 `src/features/trade-workspace/`，包括：

| 文件 | 用途 |
| --- | --- |
| `tradeScenarios.ts` | 询盘、报价、样品、物流、售后等场景数据和 Agent prompt |
| `TradeWorkspace.tsx` | 外贸作战台页面，展示业务链路并跳转到 Agent 对话 |

后续如果继续扩展，建议拆分为：

| 目录 | 用途 |
| --- | --- |
| `features/inquiry/` | 询盘评分、客户分层、追问清单 |
| `features/product-catalog/` | 产品库字段、产品推荐、报价引用 |
| `features/quote/` | 报价包、贸易条款、样品费、交期 |
| `features/logistics/` | 包装、清关、DDP 风险、HS Code |

## 后端约定

| 路径 | 用途 |
| --- | --- |
| `agent/` | Agent Runtime、会话管理、任务调度、工作流执行 |
| `tool/` | Agent 可调用工具，例如产品库、知识库状态、微信控制、翻译、汇率 |
| `rag/` | 知识库加载、分块、向量检索、产品抓取 |
| `llm/` | LLM Provider、模型编排和 Prompt 模板管理 |
| `controller/` | REST API 控制器 |
| `service/` | 业务服务 |
| `repository/` | JPA Repository |
| `model/` | Entity、DTO 和请求响应模型 |
| `config/` | Spring、AI、安全、JWT、初始化数据等配置 |

## 维护原则

1. 新增页面先判断是否只是场景数据，能放 `features/` 就不要直接堆进页面。
2. 导航和路由保持配置驱动，避免多个文件重复维护同一个入口。
3. Agent 回答涉及公司产品、报价、材料、链接时，应优先调用产品库或 RAG，而不是凭空生成。
4. 上传图片、日志和构建产物保持忽略，不作为源码提交。
5. 已经调好的页面和后端链路不要做大规模无关重写，重构以边界清晰和可验证为准。
