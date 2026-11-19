# 企业级智能业务助手 Agent（bank-agent）

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-green)
![LLM](<https://img.shields.io/badge/LLM-GLM%20(glm--5.3)-blue>)
![MCP](https://img.shields.io/badge/MCP-Server-8A2BE2)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED)
![Tests](https://img.shields.io/badge/Offline%20Tests-39%20passed-success)
![Eval](https://img.shields.io/badge/RAG%20Eval-100%25%20%2F%20100%25-success)

> 基于 Spring Boot 4.1 + Spring AI 2.0 + GLM 大模型构建的多能力智能体，以合规要求最严苛的**金融场景**完成技术验证。架构层与业务解耦，可迁移至任意企业知识域。
>
> 📦 仓库地址：https://github.com/829lalala/bank-agent

## ✨ 功能亮点

| 能力                    | 说明                                                                  | 技术要点                                        |
| ----------------------- | --------------------------------------------------------------------- | ----------------------------------------------- |
| 🔍 **Agentic RAG**      | 知识库检索封装为工具，模型自主决策"何时查库"，回答附带来源标注        | 结构化切分 → 向量化 → 检索 → 引用溯源           |
| 🛠️ **Function Calling** | 查询余额、计算存款利息等业务工具，模型自主路由                        | `@Tool` 声明式工具；模型只"点菜"，执行在 JVM    |
| 🔌 **MCP Server**       | 三个工具以标准 MCP 协议暴露，Cursor/Claude Desktop 等客户端可直接调用 | spring-ai-starter-mcp-server-webmvc（SSE 传输） |
| 💬 **多轮会话记忆**     | 支持指代词追问（"那它的风险等级呢？"）                                | MessageWindowChatMemory 滑动窗口 + 会话隔离     |
| 🌊 **SSE 流式输出**     | 产品级聊天网页：欢迎引导、思考动画、来源标签、停止生成                | Reactor Flux + Server-Sent Events               |
| 🛡️ **安全护栏**         | 输入拦截（敏感词/长度）、路径穿越防御                                 | 程序级护栏在进入模型之前生效                    |
| 📊 **可观测性**         | 自研 Advisor 记录耗时、token 用量、工具调用日志                       | Advisor 链 = AI 领域的 AOP                      |
| 🧪 **两层评估体系**     | 检索层命中率 100% / glm-5.3 生成层最近两次均 100%，变更回归防线      | 15 例评估集：事实型/对比型/无答案型             |
| 📄 **多格式知识接入**   | Markdown（按标题切分）+ PDF（按页提取），摄取后自动落盘               | DocumentReader 可插拔                           |
| 🐳 **容器化部署**       | 多阶段构建 + compose 编排，数据卷持久化                               | 构建层注入阿里云 Maven 镜像                     |

## 🏗️ 架构

```
                    ┌─────────────────────────────────────────────┐
   浏览器 chat.html │               Spring Boot 4.1                │
  ─────────────────▶│  ┌────────────────────────────────────────┐ │
  （SSE 流式）       │  │         GuardRail 输入护栏              │ │
                    │  └──────────────────┬─────────────────────┘ │
                    │  ┌──────────────────▼─────────────────────┐ │
                    │  │      ChatClient（Advisor 链）            │ │
                    │  │  ┌──────────┐  ┌────────────────────┐  │ │
                    │  │  │ 记忆回放  │  │ObservabilityAdvisor│  │ │
                    │  │  └──────────┘  └────────────────────┘  │ │
                    │  └───┬──────────┬──────────────┬──────────┘ │
                    │      │          │              │            │
                    │  ┌───▼───┐ ┌────▼──────┐ ┌────▼─────────┐  │      ┌──────────────────┐
                    │  │ 智谱   │ │ BankTools │ │KnowledgeTools│──┼─────▶│  MCP Server (SSE) │
                    │  │ GLM   │ │ 余额/利息  │ │  知识库检索    │  │      │ Cursor/Claude 可调 │
                    │  └───────┘ └───────────┘ └────┬─────────┘  │      └──────────────────┘
                    │                        ┌──────▼─────────┐   │
                    │                        │ PGVector (PostgreSQL)│
                    │                        │向量/记忆/画像持久化 │
                    │                        └────────────────┘   │
                    └─────────────────────────────────────────────┘
```

## 🧰 技术栈

| 类别   | 选型                                                                 |
| ------ | -------------------------------------------------------------------- |
| 框架   | Spring Boot 4.1.1 / Spring AI 2.0.1 / Java 21                        |
| 大模型 | 智谱 GLM（glm-5.3 对话 / embedding-3 向量），OpenAI 兼容端点接入 |
| 存储   | PostgreSQL + pgvector（向量库 / 会话记忆 / 用户画像三合一持久化）    |
| 测试   | JUnit 5 + Mockito（本次默认离线执行 39 项）+ 15 道回归题与 45 道验收题 |
| 部署   | Docker 多阶段构建 + docker-compose                                   |

## 📊 核心指标

| 指标         | 数值                | 说明                                                               |
| ------------ | ------------------- | ------------------------------------------------------------------ |
| 检索层命中率 | **100%**（13/13）   | 15 例评估集，topK=3 命中标准答案（修复评估污染后）                 |
| 生成层通过率 | **100%（15/15，glm-5.3 连续两轮）** | 完整 Agent 链路，关键事实断言 + 拒答测试；2026-11-18 在当前代码版本复验，[报告](docs/evaluation-results/2026-11-18/README.md) |
| 离线测试     | **39 项通过（2026-11-18 复验）** | 护栏 / 路径守卫 / 业务工具 / 用户隔离 / MCP 安全 / 检索格式化       |
| 同步验收耗时 | glm-5.3 平均 17.91 秒 | 2026-10-29，135 次串行请求，含检索与工具循环；完整成本尚未核实 |
| MCP 工具     | 3 个                | 完整握手验证：initialize / tools/list                              |

## 🚀 快速开始

### 方式一：本地运行

```bash
# 1. 在运行应用的 Windows PowerShell 终端提供配置（只对当前终端及其启动的程序生效）
$env:ZHIPUAGENT_API_KEY = "你的智谱模型密钥"  # 模型调用；申请：https://open.bigmodel.cn/
$env:DEMO_ADMIN_PASSWORD = "自行设置的管理员密码"  # 演示账号 admin 的密码
$env:DEMO_USER_PASSWORD = "自行设置的普通用户密码"  # 演示账号 demo 的密码
$env:DEMO_MCP_ACCESS_KEY = "自行设置的外部工具密钥"  # 使用外部工具时需要；与网页登录密码分开

# 2. 启动 PostgreSQL（compose 管理，与完整部署共用配置，避免容器名冲突）
docker compose up -d postgres

# 3. 运行（两个演示密码缺失时会拒绝启动；数据库表在启动时创建）
mvn spring-boot:run
```

启动后打开 http://localhost:8080/chat.html，浏览器会先跳转到登录页。使用 `admin` 或 `demo` 和上面对应的密码登录。首次知识摄取需要管理员登录状态和跨站请求伪造（Cross-Site Request Forgery，CSRF）令牌；旧版匿名 `curl -X POST` 命令不再适用。

首次摄取知识：先用 `admin` 登录并停留在聊天页，打开浏览器开发者工具的控制台，粘贴并运行下面的代码。浏览器会自动携带当前登录状态；代码先取得 CSRF 令牌，再用该令牌提交摄取请求。

```javascript
const tokenResponse = await fetch("/agent/csrf");
if (!tokenResponse.ok) throw new Error("获取防伪令牌失败，请确认已用 admin 登录");
const csrf = await tokenResponse.json();
const response = await fetch("/rag/load", {
  method: "POST",
  headers: { "X-CSRF-TOKEN": csrf.token }
});
console.log(response.status, await response.text());
```

成功时应看到“摄取完成”和段落、切块数量；普通账号应收到 `403`（权限不足）。如果提示文件不存在，请检查应用运行目录下是否有 `docs/knowledge/bank-knowledge.md`。知识已存入数据库时，无需每次启动都重新摄取。

### 方式二：Docker 部署

```bash
$env:ZHIPUAGENT_API_KEY = "你的智谱模型密钥"
$env:DEMO_ADMIN_PASSWORD = "自行设置的管理员密码"
$env:DEMO_USER_PASSWORD = "自行设置的普通用户密码"
$env:DEMO_MCP_ACCESS_KEY = "自行设置的外部工具密钥"
docker compose up -d          # 应用 + PostgreSQL 双容器，数据挂载 pg-data 卷，重建不丢
```

### 访问入口

| 入口       | URL                                            | 说明                                 |
| ---------- | ---------------------------------------------- | ------------------------------------ |
| 网页对话   | http://localhost:8080/chat.html                | 完整聊天界面（打字机效果）           |
| Agent 对话 | `GET /agent/chat?q=问题&conversationId=会话ID` | 工具 + RAG + 记忆全能力              |
| 知识摄取   | `POST /rag/load`                               | 支持指定 `?path=文档名`（md/pdf）    |
| MCP 端点   | `GET /sse`                                     | 标准 MCP 协议，客户端可发现 3 个工具 |

## 🧪 质量保障

四类简历指标的采集口径、同条件模型对比命令与费用边界见 [指标采集说明](docs/measurement-guide.md)。2026-10-29 完成 45 道验收题的双模型三轮实验：自动通过率 82.22% → 92.59%，平均同步耗时 3.85 秒 → 17.91 秒；详见 [原始报告与复核说明](docs/evaluation-results/2026-10-29/README.md)。2026-11-18 在当前代码版本完成回归复验：检索层 13/13、生成层连续两轮 15/15、离线测试 39/39，详见 [回归复验报告](docs/evaluation-results/2026-11-18/README.md)。15 道回归题的历史 100% 不能代表新增验收题成绩。

```powershell
mvn test  # 默认只运行离线测试；本次验证通过，不需要数据库或模型密钥
mvn verify  # 同时运行离线测试并打包；提交后的自动检查使用此命令

# 以下两项必须先启动 PostgreSQL，并提供模型密钥与两个演示账号密码；会调用外部模型服务。
mvn test '-Dtest=RagRetrievalEvaluationTest'  # 检索层：必要时摄取知识并调用向量模型
mvn test '-Dtest=RagAnswerEvaluationTest'  # 生成层：使用已有知识库调用对话模型
```

评估集覆盖三类问题：**事实型**（数字断言）、**对比型**（多资料召回）、**无答案型**（拒答测试）。2026-10-15 在线验证：检索层 13/13（100%）；glm-5.3 连续两次生成层评估 15/15（100%，含数据库与模型密钥）。生成层受模型随机性影响，任何提示词、参数或模型变更后，都应重新运行并记录日期、模型、样本数与结果。

## 📁 项目结构

```
src/main/java/com/bank/agent/
├── BankAgentApplication.java    启动类
├── controller/
│   ├── ChatController           基础对话（同步/流式，对照实验用）
│   ├── RagController            知识摄取 + RAG 问答
│   └── AgentController          融合 Agent（工具 + Agentic RAG + 记忆）
├── tool/
│   ├── BankTools                银行业务工具（余额/利息）
│   └── KnowledgeTools           知识库检索工具（Agentic RAG）
├── config/
│   ├── RagConfig                向量库装配 + 本地持久化
│   ├── AgentConfig              对话记忆装配
│   └── McpConfig                MCP 工具暴露
├── guard/
│   ├── GuardRail                输入安全护栏
│   └── PathGuard                文档路径守卫
└── observation/ObservabilityAdvisor  耗时/token 观测
src/test/java/com/bank/agent/
├── guard/  tool/  config/       离线逻辑与安全测试
└── evaluation/                  两层评估体系（15 例评估集）
```

## 🔬 验证记录（为什么这个设计可信）

- **幻觉抑制对照实验**：同一问题无 RAG 时回答"大概几千到几万元，建议咨询客服"，有 RAG 精准回答"1万元（依据：资料1）"
- **工具执行实证**：利息计算"5万 × 2年 × 1.85% = 1850 元"由 Java 方法计算，数学精确
- **工具路由测试矩阵**：产品问题→检索工具 / 查余额→余额工具 / 闲聊→零调用，四用例全通过
- **评估驱动修复实录**：评估集跑出"房贷额度回答 70成"（模型单位转述错误）→ 知识库改双表述修复；跑出 6 条断言空格误判 → 归一化修复
- **MCP 完整握手实测**：initialize → tools/list 返回 3 个工具（含中文描述与 JSON Schema）

## 🗺️ Roadmap

- [x] ~~PGVector 迁移~~（已完成：向量库/会话记忆/用户画像三合一持久化到 PostgreSQL，VectorStore 接口可移植性验证通过，业务代码零改动；踩坑：pgvector 索引 2000 维上限 vs embedding-3 的 2048 维）
- [x] 用户鉴权与会话绑定（演示账号、会话/画像/余额按登录用户隔离；银行数据仍为模拟数据）
- [x] ~~对比型问题优化~~（已闭环：真因是评估测试重复摄取污染向量库，非 topK 不足；修复摄取幂等性后检索层 100%，Agent 层新增工具描述引导的查询拆分）
- [x] CI 流水线（GitHub Actions：离线测试 + 构建）

## 📄 说明

本项目为技术学习与面试准备用途；银行业务数据均为模拟数据，不涉及真实金融机构信息。
