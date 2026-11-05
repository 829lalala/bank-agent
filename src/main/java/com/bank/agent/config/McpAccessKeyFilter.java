package com.bank.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 每个外部工具请求均须提供独立访问密钥。 */
public class McpAccessKeyFilter extends OncePerRequestFilter {

    private final String accessKey;

    public McpAccessKeyFilter(String accessKey) {
        this.accessKey = accessKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-MCP-Access-Key");
        // 缺少配置或请求密钥时一律拒绝；固定时间比较避免逐字节泄露匹配程度。
        if (accessKey.isBlank() || supplied == null || !MessageDigest.isEqual(
                accessKey.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "外部工具访问密钥无效");
            return;
        }
        chain.doFilter(request, response);
    }
}
