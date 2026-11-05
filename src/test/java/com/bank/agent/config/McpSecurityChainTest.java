package com.bank.agent.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只加载安全配置和替身入口，验证实际路径经过密钥过滤链。 */
@WebMvcTest(controllers = McpSecurityChainTest.StubMcpEndpoints.class)
@Import({WebSecurityConfig.class, DemoAccountsConfig.class, McpSecurityChainTest.StubMcpEndpoints.class})
@TestPropertySource(properties = {
        "demo.security.admin-password=admin-test-password",
        "demo.security.user-password=user-test-password",
        "demo.security.mcp-access-key=mcp-test-key"
})
class McpSecurityChainTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 连接入口要求独立密钥() throws Exception {
        mvc.perform(get("/sse")).andExpect(status().isUnauthorized());
        mvc.perform(get("/sse").header("X-MCP-Access-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/sse").header("X-MCP-Access-Key", "mcp-test-key"))
                .andExpect(status().isOk());
    }

    @Test
    void 后续消息入口也要求独立密钥() throws Exception {
        mvc.perform(post("/mcp/message")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp/message").header("X-MCP-Access-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp/message").header("X-MCP-Access-Key", "mcp-test-key"))
                .andExpect(status().isOk());
    }

    @RestController
    static class StubMcpEndpoints {
        @GetMapping("/sse")
        String connect() {
            return "connected";
        }

        @PostMapping("/mcp/message")
        String message() {
            return "received";
        }
    }
}
