package com.bank.agent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
     * <p>InMemoryChatMemoryRepository：内存存储，重启即清空（与向量库当初一样）。
     * 生产环境换 JdbcChatMemoryRepository（落数据库）或 Redis 实现，Bean 返回类型
     * 是 ChatMemory 接口，业务代码不用改。
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }
}
