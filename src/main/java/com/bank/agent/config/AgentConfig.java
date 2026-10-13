package com.bank.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置：多轮对话记忆。
 */
@Configuration
public class AgentConfig {

    /**
     * 对话记忆 Bean。
     *
     * <p>MessageWindowChatMemory：滑动窗口策略——每个会话（conversationId）最多保留
     * 最近 20 条消息，超出时丢弃最早的消息。为什么要有窗口：历史无限增长会撑爆
     * 上下文窗口（token 超限）且调用成本线性上升，"近期记忆最相关"是合理的近似。
     *
     * <p>JdbcChatMemoryRepository：历史消息持久化到 PostgreSQL（spring-ai-starter-model-
     * chat-memory-repository-jdbc 自动配置，表 AI_CHAT_MEMORY 由 initialize-schema 自动创建）。
     * 迁移价值：重启后同一 conversationId 的上下文仍在——应用升级发布不再"集体失忆"。
     * Bean 返回类型是 ChatMemory 接口，业务代码不用改（面向接口的又一次胜利）。
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
