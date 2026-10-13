# 企业级智能业务助手 Agent（bank-agent）

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-green)
![LLM](<https://img.shields.io/badge/LLM-GLM%20(glm--4--flash)-blue>)
![MCP](https://img.shields.io/badge/MCP-Server-8A2BE2)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED)
![Tests](https://img.shields.io/badge/Tests-26%20passed-success)
![Eval](https://img.shields.io/badge/RAG%20Eval-100%25%20%2F%20~86%25-success)

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
| 🧪 **两层评估体系**     | 检索层命中率 92.3% / 生成层通过率 86.7%，变更回归防线                 | 15 例评估集：事实型/对比型/无答案型             |
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
| 大模型 | 智谱 GLM（glm-4-flash 对话 / embedding-3 向量），OpenAI 兼容端点接入 |
| 存储   | PostgreSQL + pgvector（向量库 / 会话记忆 / 用户画像三合一持久化）    |
| 测试   | JUnit 5 + Mockito（26 个单元测试）+ 两层评估集（15 例）              |
| 部署   | Docker 多阶段构建 + docker-compose                                   |

## 📊 核心指标

| 指标         | 数值                | 说明                                                               |
| ------------ | ------------------- | ------------------------------------------------------------------ |
| 检索层命中率 | **100%**（13/13）   | 15 例评估集，topK=3 命中标准答案（修复评估污染后）                 |
| 生成层通过率 | **80%~87%**（波动） | 完整 Agent 链路，关键事实断言 + 拒答测试；边缘用例受模型随机性影响 |
| 单元测试     | **26/26 通过**      | 护栏 / 路径守卫 / 业务工具 / 检索格式化                            |
| 单次问答成本 | ~1240 tokens / ~3s  | ObservabilityAdvisor 实测（提示占 97%）                            |
| MCP 工具     | 3 个                | 完整握手验证：initialize / tools/list                              |

## 🚀 快速开始

### 方式一：本地运行

```bash
# 1. 配置密钥（Windows PowerShell）
$env:ZHIPUAGENT_API_KEY = "你的智谱APIKey"   # 申请：https://open.bigmodel.cn/

# 2. 启动 PostgreSQL（向量库/会话记忆/画像的持久化库）
docker run -d --name bank-agent-pg -e POSTGRES_PASSWORD=bankagent -e POSTGRES_DB=bankagent -p 5432:5432 pgvector/pgvector:pg16

# 3. 运行（建表自动完成：vector_store / spring_ai_chat_memory / user_memory_facts）
mvn spring-boot:run

# 4. 首次摄取知识库（仅需一次，数据落 PG，重启不丢）
curl -X POST http://localhost:8080/rag/load
```

### 方式二：Docker 部署

```bash
$env:ZHIPUAGENT_API_KEY = "你的智谱APIKey"
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

```bash
mvn test                                          # 26 个单元测试（免费、离线、毫秒级；评估类测试需 PG 与 API Key）
mvn test -Dtest=RagRetrievalEvaluationTest        # 检索层评估（需密钥，调 embedding）
$env:RUN_LLM_EVAL="true"; mvn test -Dtest=RagAnswerEvaluationTest   # 生成层评估（调 LLM）
```

评估集覆盖三类问题：**事实型**（数字断言）、**对比型**（多资料召回）、**无答案型**（拒答测试）。任何提示词/参数/模型变更，先过评估再上线。

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
├── guard/  tool/                26 个单元测试
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
- [ ] 用户鉴权与会话绑定（查余额工具接入真实用户体系）
- [x] ~~对比型问题优化~~（已闭环：真因是评估测试重复摄取污染向量库，非 topK 不足；修复摄取幂等性后检索层 100%，Agent 层新增工具描述引导的查询拆分）
- [ ] CI 流水线（GitHub Actions：测试 + 评估 + 镜像构建）

## 📄 说明

本项目为技术学习与面试准备用途；银行业务数据均为模拟数据，不涉及真实金融机构信息。
