package com.bank.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

class McpConfigTest {

    @Test
    void 外部客户端只能发现三个非个人数据工具() {
        McpConfig config = new McpConfig();
        ToolCallbackProvider provider = config.bankToolCallbackProvider(
                config.bankTools(), config.knowledgeTools(mock(VectorStore.class)));

        Set<String> names = java.util.Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(Set.of("searchBankKnowledge", "calcFixedDepositInterest",
                "calcWealthManagementReturn"), names);
    }
}
