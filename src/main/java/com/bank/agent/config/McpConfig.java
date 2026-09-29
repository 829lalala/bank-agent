package com.bank.agent.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bank.agent.tool.BankTools;
import com.bank.agent.tool.KnowledgeTools;

/**
 * MCP（Model Context Protocol）服务配置。
 *
 * <p>作用：把带 @Tool 注解的工具方法暴露为 MCP 服务，通过 WebMVC/SSE 传输。
 * 任何 MCP 客户端（Cursor、Claude Desktop、其他 Agent 应用）连接后
 * 即可发现并调用这些工具——工具能力从"本应用私有"变为"生态可复用"。
 *
 * <p>双通道注册（踩坑实录）：
 * 1. 工具类注册为 Spring Bean——供 MCP 注解扫描器发现容器中的 @Tool 方法；
 * 2. MethodToolCallbackProvider 显式收集工具对象——供自动配置组装工具列表。
 * 只注册 Bean 不提供 Provider 时，自动配置日志报
 * "No tool methods found in the provided tool objects: []"，工具列表为空。
 */
@Configuration
public class McpConfig {

    @Bean
    public BankTools bankTools() {
        return new BankTools();
    }

    @Bean
    public KnowledgeTools knowledgeTools(SimpleVectorStore vectorStore) {
        return new KnowledgeTools(vectorStore);
    }

    @Bean
    public ToolCallbackProvider bankToolCallbackProvider(BankTools bankTools, KnowledgeTools knowledgeTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(bankTools, knowledgeTools)
                .build();
    }
}

