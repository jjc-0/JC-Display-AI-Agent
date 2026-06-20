# JC Display AI Agent — B2B 智能运营助手

B2B 出口贸易 AI 运营平台，为展示架（POP Display）行业提供智能客服、询盘分析、文案撰写、多语言翻译、市场分析等能力。后端 Spring Boot 集成 LLM，前端 React + Shadcn UI + TailwindCSS。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3 + JDK 17 |
| 持久层 | Spring Data JPA + MySQL（自动降级 H2） |
| LLM | DeepSeek API / OpenAI 兼容协议 |
| RAG | 本地嵌入 + 向量检索，数据存储于 MySQL |
| 前端框架 | React 18 + TypeScript + Vite 5 |
| UI | Shadcn UI + Radix Primitives + TailwindCSS |
| 图表 | Recharts |

---

## 功能模块

| 模块 | 说明 |
|---|---|
| AI Agent 对话 | 多轮对话 + 工具调用，支持 DeepSeek |
| 询盘价值评分 | B2B 询盘智能分析，评估客户质量 |
| 文案 & 询盘回复 | 展示架产品详情页、英文邮件生成 |
| 多语言翻译 | 展示架行业术语翻译，多语言互译 |
| 市场分析 | 目标市场出口机会评估 |
| AI 智能识图 | 上传产品图片进行视觉分析 |
| RAG 知识库 | 公司产品与行业知识向量化检索 |
| Prompt 模板 | 可配置 Prompt 模板管理 |
| Workflow Builder | 可视化工作流编排 |
| JC-CLAW 助手 | 跨系统自动化任务编排 |

---

## 项目结构

```
├── backend/
│   └── src/main/java/com/ecommerce/agent/
│       ├── agent/           # Agent 调度、会话管理
│       ├── config/          # AI 配置、Web 配置
│       ├── controller/      # REST API 控制器
│       ├── llm/             # LLM Provider 适配
│       ├── model/           # JPA Entity + DTO
│       ├── rag/             # RAG 检索增强生成
│       ├── repository/      # JPA Repository
│       ├── service/         # 业务服务
│       ├── tool/            # 工具调用集
│       └── util/            # 工具类
│
├── frontend/
│   └── src/
│       ├── components/      # 通用组件(ui + layout)
│       ├── pages/           # 页面组件
│       ├── lib/             # API 封装、工具函数
│       └── index.css        # 全局样式(TailwindCSS)
│
└── pictures/                # 系统图片资源
```

---

## 快速启动

### 环境要求
- JDK 17+
- MySQL 8.0+（可选）
- Node.js 18+

### 1. 配置 API Key

`backend/src/main/resources/application-secrets.yml`：

```yaml
DEEPSEEK_API_KEY: sk-your-key
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8088
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3001
```

---

## 主要 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/agent/chat` | 发送对话 |
| GET | `/api/agent/sessions` | 会话列表 |
| GET | `/api/agent/session/{id}/history` | 对话历史 |
| PUT | `/api/agent/session/{id}/title` | 修改标题 |
| POST | `/api/agent/session/{id}/auto-title` | AI 自动命名 |
| POST | `/api/copywriting/generate` | 生成文案 |
| POST | `/api/translate` | 文本翻译 |
| POST | `/api/analysis/market` | 市场分析 |
| POST | `/api/image/recognize` | 图片识别 |
| GET | `/api/deepseek/usage` | DeepSeek 用量/余额 |

---

## 配置项

| 环境变量 | 说明 |
|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API Key |
| `MYSQL_URL` | 数据库连接（默认 `localhost:3306/jc_agent`） |
| `MYSQL_USER` | 数据库用户 |
| `MYSQL_PASS` | 数据库密码 |

---

## 版权

&copy; 2026 深圳市杰创包装展示有限公司（Shenzhen JC Display Ltd.）保留所有权利。
