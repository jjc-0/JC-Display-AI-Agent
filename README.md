# JC Display AI Agent

面向 B2B 出口贸易场景的 AI 智能运营助手，聚焦展示架、POP Display 等行业的客户沟通、询盘分析、文案生成、多语言翻译、图片识别、RAG 知识库和自动化工作流。

仓库地址：[https://github.com/jjc-0/AI_Prompt](https://github.com/jjc-0/AI_Prompt)

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.2、JDK 17、Spring Security、Spring Data JPA |
| 数据库 | MySQL 8，支持 H2 控制台 |
| AI/LLM | DeepSeek API、OpenAI 兼容接口、LangChain4j |
| RAG | 本地知识库、文档解析、向量检索、产品知识索引 |
| 前端 | React 18、TypeScript、Vite 5 |
| UI | Tailwind CSS、Shadcn UI、Radix Primitives、Lucide Icons |
| 图表 | Recharts |

## 功能模块

| 模块 | 说明 |
| --- | --- |
| AI Agent 对话 | 多轮对话、工具调用、会话管理、自动标题 |
| 询盘价值评分 | 对 B2B 询盘进行客户质量、采购意向和跟进优先级分析 |
| 文案生成 | 生成产品详情页、营销文案、英文邮件和询盘回复 |
| 多语言翻译 | 面向外贸场景的行业术语翻译 |
| 市场分析 | 分析目标市场、出口机会和竞争要点 |
| 图片识别 | 上传产品图片并进行视觉理解与信息提取 |
| 产品图片生成 | 调用图片生成能力制作产品或营销图片 |
| RAG 知识库 | 管理公司资料、行业资料和产品知识，增强 Agent 回答 |
| Prompt 模板 | 管理可复用 Prompt 模板 |
| Workflow Builder | 可视化编排自动化流程 |
| JC-CLAW 助手 | 跨系统自动化任务与微信桥接能力 |

## 项目结构

```text
.
├── backend/                  # Spring Boot 后端服务
│   ├── src/main/java/com/ecommerce/agent/
│   │   ├── agent/            # Agent 运行时、任务调度、工作流
│   │   ├── config/           # 安全、JWT、AI、Web 配置
│   │   ├── controller/       # REST API 控制器
│   │   ├── llm/              # LLM Provider 适配
│   │   ├── model/            # Entity 与 DTO
│   │   ├── rag/              # RAG、文档解析、检索增强
│   │   ├── repository/       # JPA Repository
│   │   ├── service/          # 业务服务
│   │   └── tool/             # Agent 工具集合
│   └── src/main/resources/
│       ├── application.yml   # 默认配置
│       └── knowledge/        # 内置知识库资料
├── frontend/                 # React + Vite 前端
│   ├── src/components/       # 通用组件与布局
│   ├── src/config/           # 路由与导航配置
│   ├── src/features/         # 业务功能封装
│   ├── src/pages/            # 页面组件
│   └── src/lib/              # API 与工具函数
├── docs/                     # 项目文档
├── pictures/                 # 项目图片资源
├── scripts/                  # 本地辅助脚本
└── uploads/                  # 本地上传文件目录
```

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.9+，或使用 `backend/mvnw`
- Node.js 18+
- MySQL 8.0+

### 1. 克隆项目

```bash
git clone https://github.com/jjc-0/AI_Prompt.git
cd AI_Prompt
```

### 2. 配置后端密钥

在 `backend/src/main/resources/` 下创建 `application-secrets.yml`：

```yaml
DEEPSEEK_API_KEY: sk-your-deepseek-key
OPENAI_API_KEY: sk-your-openai-key
OPENAI_BASE_URL: https://api.openai.com/v1
OPENAI_MODEL: gpt-5.5

MYSQL_URL: jdbc:mysql://localhost:3306/jc_agent?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
MYSQL_USER: root
MYSQL_PASS: root
```

如不使用某些 AI 或邮件能力，可以保留默认占位配置。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认运行在 [http://localhost:8088](http://localhost:8088)。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 [http://localhost:3000](http://localhost:3000)，并通过 Vite proxy 转发 `/api` 与 `/uploads` 到后端。

## 常用命令

```bash
# 前端开发
cd frontend
npm run dev

# 前端构建
cd frontend
npm run build

# 后端开发
cd backend
./mvnw spring-boot:run

# 后端测试
cd backend
./mvnw test
```

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent/chat` | 发送 Agent 对话 |
| `GET` | `/api/agent/sessions` | 获取会话列表 |
| `GET` | `/api/agent/session/{id}/history` | 获取会话历史 |
| `PUT` | `/api/agent/session/{id}/title` | 修改会话标题 |
| `POST` | `/api/agent/session/{id}/auto-title` | 自动生成会话标题 |
| `POST` | `/api/copywriting/generate` | 生成文案 |
| `POST` | `/api/translate` | 文本翻译 |
| `POST` | `/api/analysis/market` | 市场分析 |
| `POST` | `/api/image/recognize` | 图片识别 |
| `POST` | `/api/product-image/generate` | 产品图片生成 |
| `GET` | `/api/deepseek/usage` | DeepSeek 用量查询 |

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | `sk-placeholder` | DeepSeek API Key |
| `OPENAI_API_KEY` | `sk-placeholder` | OpenAI 或兼容接口 API Key |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容接口地址 |
| `OPENAI_MODEL` | `gpt-5.5` | 默认文本模型 |
| `IMAGE_GEN_ENABLED` | `false` | 是否启用图片生成 |
| `IMAGE_GEN_MODEL` | `gpt-image-2` | 图片生成模型 |
| `MYSQL_URL` | 本地 `jc_agent` | MySQL JDBC 地址 |
| `MYSQL_USER` | `root` | 数据库用户名 |
| `MYSQL_PASS` | `root` | 数据库密码 |
| `MAIL_HOST` | `smtp.qq.com` | 邮件服务主机 |
| `MAIL_PORT` | `465` | 邮件服务端口 |
| `MAIL_USERNAME` | 空 | 邮件账号 |
| `MAIL_PASSWORD` | 空 | 邮件授权码 |
| `GOOGLE_API_KEY` | 空 | Google Search API Key |
| `GOOGLE_CX` | 空 | Google Custom Search Engine ID |

## 注意事项

- `application-secrets.yml`、`.env`、构建产物和依赖目录已在 `.gitignore` 中忽略。
- 首次启动前请确认 MySQL 服务可用，默认数据库名为 `jc_agent`。
- RAG 知识库默认读取 `backend/src/main/resources/knowledge/`。
- 上传文件会写入本地 `uploads/` 或 `backend/uploads/` 目录，生产环境建议改为对象存储。

## 版权

Copyright © 2026 Shenzhen JC Display Ltd. All rights reserved.
