package com.bank.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpAccessKeyFilterTest {

    @Test
    void 正确密钥放行() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        request.addHeader("X-MCP-Access-Key", "demo-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new McpAccessKeyFilter("demo-key").doFilter(request, response, chain);

        assertTrue(chain.getRequest() != null);
    }

    @Test
    void 错误密钥拒绝后续消息() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("X-MCP-Access-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new McpAccessKeyFilter("demo-key").doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertFalse(chain.getRequest() != null);
    }

    @Test
    void 缺少密钥或服务端配置均拒绝() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        new McpAccessKeyFilter("demo-key").doFilter(request, missingResponse, new MockFilterChain());
        assertEquals(401, missingResponse.getStatus());

        request.addHeader("X-MCP-Access-Key", "demo-key");
        MockHttpServletResponse emptyConfigResponse = new MockHttpServletResponse();
        new McpAccessKeyFilter("").doFilter(request, emptyConfigResponse, new MockFilterChain());
        assertEquals(401, emptyConfigResponse.getStatus());
    }
}
