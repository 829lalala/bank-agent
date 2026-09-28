# 企业级智能业务助手 Agent（bank-agent）

> 基于 Spring Boot 4.1 + Spring AI 2.0 + GLM 大模型构建的多能力智能体，以合规要求最严苛的**金融场景**完成技术验证。架构层与业务解耦，可迁移至任意企业知识域。

## ✨ 功能亮点

| 能力 | 说明 | 技术要点 |
|---|---|---|
| 🔍 **Agentic RAG** | 知识库检索封装为工具，模型自主决策"何时查库"，回答附带来源标注 | RAG 全链路：结构化切分 → 向量化 → 检索 → 引用溯源 |
| 🛠️ **Function Calling** | 查询余额、计算存款利息等业务工具，模型自主路由 | `@Tool` 注解声明式工具，模型只"点菜"，执行在 JVM |
| 💬 **多轮会话记忆** | 支持指代词追问（"那它的风险等级呢？"） | MessageWindowChatMemory 滑动窗口 + 会话隔离 |
| 🌊 **SSE 流式输出** | 打字机效果网页对话 | Reactor Flux + Server-Sent Events |
| 🛡️ **安全护栏** | 输入拦截（敏感词/长度）、路径穿越防御 | 程序级护栏在进入模型之前生效 |
| 📊 **可观测性** | 自研 Advisor 记录耗时、token 用量、工具调用日志 | Advisor 链 = AI 领域的 AOP |
| 📄 **多格式知识接入** | Markdown（按标题切分）+ PDF（按页提取），摄取后自动落盘持久化 | DocumentReader 可插拔 |

## 🏗️ 架构

```
                    ┌─────────────────────────────────────────┐
   浏览器 chat.html │              Spring Boot 4.1             │
  ─────────────────▶│  ┌───────────────────────────────────┐  │
  （SSE 流式）       │  │        GuardRail 输入护栏          │  │
                    │  └───────────────┬───────────────────┘  │
                    │  ┌───────────────▼───────────────────┐  │
                    │  │     ChatClient（Advisor 链）       │  │
                    │  │  ┌─────────┐ ┌─────────────────┐  │  │
                    │  │  │记忆回放  │ │ObservabilityAdvisor│ │ │
                    │  │  └─────────┘ └─────────────────┘  │  │
                    │  └──┬──────────┬──────────┬──────────┘  │
                    │     │          │          │             │
                    │  ┌──▼───┐ ┌────▼─────┐ ┌──▼──────────┐  │
                    │  │ 智谱  │ │ BankTools│ │KnowledgeTools│  │
                    │  │ GLM  │ │ 余额/利息 │ │  知识库检索   │  │
                    │  └──────┘ └──────────┘ └──┬──────────┘  │
                    │                    ┌──────▼──────┐       │
                    │                    │SimpleVectorStore│    │
                    │                    │ （落盘持久化）  │      │
                    │                    └─────────────┘       │
                    └─────────────────────────────────────────┘
```

## 🧰 技术栈

| 类别 | 选型 | 说明 |
|---|---|---|
| 框架 | Spring Boot 4.1.1 / Spring AI 2.0.1 | Spring 官方 AI 应用框架 |
| 大模型 | 智谱 GLM（glm-4-flash 对话 / embedding-3 向量） | OpenAI 兼容端点接入，换厂商只改配置 |
| 构建 | Java 21 / Maven 3.9 | |

## 🚀 快速开始

### 前置要求
- JDK 21+、Maven 3.9+
- [智谱开放平台](https://open.bigmodel.cn/) API Key（glm-4-flash 免费）

### 配置密钥
```bash
# Windows（PowerShell，永久生效：系统设置 → 环境变量）
$env:ZHIPUAGENT_API_KEY = "你的智谱APIKey"
```

### 构建与运行
```bash
mvn spring-boot:run
```

### 访问入口
| 入口 | URL | 说明 |
|---|---|---|
| 网页对话 | http://localhost:8080/chat.html | 完整聊天界面（打字机效果） |
| Agent 对话 | `GET /agent/chat?q=大额存单起购金额&conversationId=会话ID` | 工具 + RAG + 记忆全能力 |
| 知识摄取 | `POST /rag/load`（默认）/ `POST /rag/load?path=文档名.pdf` | 首次运行后执行一次 |
| 基础对话 | `GET /chat?q=你好` / `GET /chat/stream?q=你好` | 对照实验用 |

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
│   └── AgentConfig              对话记忆装配
├── guard/GuardRail              输入安全护栏
└── observation/ObservabilityAdvisor  耗时/token 观测
docs/knowledge/                  知识库文档目录（可投放 md/pdf）
```

## 🔬 验证记录（为什么这个设计可信）

- **幻觉抑制对照实验**：同一问题无 RAG 时回答"大概几千到几万元，建议咨询客服"，有 RAG 精准回答"1万元（依据：资料1）"
- **工具执行实证**：利息计算"5万 × 2年 × 1.85% = 1850 元"由 Java 方法计算，数学精确
- **工具路由测试矩阵**：产品问题→检索工具 / 查余额→余额工具 / 闲聊→零调用，四用例全通过
- **可观测数据**：单次问答约 1200 tokens、耗时约 3s（日志可见），提示词占比 97%——明确下一步优化方向

## 🗺️ Roadmap

- [ ] PGVector 迁移（生产级向量存储）
- [ ] 评估集与检索质量回归
- [ ] 用户鉴权与会话绑定
- [ ] MCP Server（工具接入开放生态）
- [ ] Docker 化部署

## 📄 说明

本项目为技术学习与面试准备用途；银行业务数据均为模拟数据，不涉及真实金融机构信息。
