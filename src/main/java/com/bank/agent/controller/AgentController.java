package com.bank.agent.controller;

import com.bank.agent.guard.GuardRail;
import com.bank.agent.observation.ObservabilityAdvisor;
import com.bank.agent.tool.BankTools;
import com.bank.agent.tool.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * Agent 接口：工具调用 + Agentic RAG + 多轮记忆的完整智能助手。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    /** System Prompt：约束模型行为边界（Agent 的"人设 + 纪律"） */
    private static final String SYSTEM_PROMPT = """
            你是银行的智能业务助手，称呼为"星辰小助手"。
            行为准则：
            0. 凡涉及本行产品（存款、理财、信用卡、贷款）或业务规则的问题，必须先调用知识库检索工具获取资料，禁止凭记忆回答；
            1. 产品信息（利率、金额、期限、规则）必须以知识库检索工具返回的资料为准，数字不得改动；
            2. 资料中没有的信息，如实告知"知识库中没有相关信息"，绝不编造；
            3. 涉及账户数据的操作（如查余额）必须通过工具获取，不得虚构数字；
            4. 回答简洁、礼貌、专业；
            5. 回答引用了资料的产品信息时，须在对应句末标注依据来源，格式：（依据：资料N）。
               未引用资料的回答不要标注。
            """;

    private final ChatClient chatClient;
    private final KnowledgeTools knowledgeTools;
    private final BankTools bankTools;
    private final Advisor memoryAdvisor;

    public AgentController(BankTools bankTools, KnowledgeTools knowledgeTools,
                           ChatMemory chatMemory, ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeTools = knowledgeTools;
        this.bankTools = bankTools;
        // 记忆顾问：请求前回放历史消息、响应后把本轮对话写入记忆
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 完整 Agent 对话（流式 + 多轮记忆）。
     * 用法示例：
     *   GET http://localhost:8080/agent/chat?q=大额存单起购金额&conversationId=abc123
     *
     * @param q              用户问题
     * @param conversationId 会话 ID：同一 ID 的多轮请求共享记忆（前端生成 UUID 并复用）
     */
    @GetMapping("/chat")
    public ResponseEntity<Flux<String>> chat(@RequestParam String q,
                                             @RequestParam(defaultValue = "default") String conversationId) {
        // 输入护栏：在进入模型之前拦截（省钱、快速失败、比模型自我审查更可靠）
        String blocked = GuardRail.check(q);
        if (blocked != null) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                    .body(Flux.just(blocked));
        }

        Flux<String> body = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .tools(bankTools, knowledgeTools)
                .user(q)
                .advisors(memoryAdvisor)
                // 2.0 新要求：记忆顾问必须显式拿到会话 ID，通过 advisor 参数传递
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new ObservabilityAdvisor()) // 可观测性：耗时 + token 用量
                .stream()
                .content();

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .body(body);
    }
}
