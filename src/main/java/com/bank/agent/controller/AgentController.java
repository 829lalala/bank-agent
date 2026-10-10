package com.bank.agent.controller;

import com.bank.agent.guard.GuardRail;
import com.bank.agent.observation.ObservabilityAdvisor;
import com.bank.agent.prompts.Prompts;
import com.bank.agent.service.ReflectionService;
import com.bank.agent.service.ReflectionService.ReflectionOutcome;
import com.bank.agent.tool.BankTools;
import com.bank.agent.tool.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Agent 接口：工具调用 + Agentic RAG + 多轮记忆的完整智能助手。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    /** Agent 行为准则集中管理于 Prompts（与反思服务、评估测试共享，消除副本漂移） */

    private final ChatClient chatClient;
    private final KnowledgeTools knowledgeTools;
    private final BankTools bankTools;
    private final Advisor memoryAdvisor;
    private final ReflectionService reflectionService;

    public AgentController(BankTools bankTools, KnowledgeTools knowledgeTools,
                           ChatMemory chatMemory, ChatClient.Builder chatClientBuilder,
                           ReflectionService reflectionService) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeTools = knowledgeTools;
        this.bankTools = bankTools;
        this.reflectionService = reflectionService;
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
                .system(Prompts.AGENT_SYSTEM_PROMPT)
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

    /**
     * 反思模式对话（同步 JSON）：生成 → 评审 → 修正循环（Evaluator-Optimizer）。
     * 用法示例：
     *   GET http://localhost:8080/agent/chat-reflect?q=我有1万块存三年定期和买稳盈90天哪个划算
     *
     * <p>与 /chat 的区别：多 2~4 次 LLM 调用（评审 + 修正），延迟更高，
     * 但回答经过质量评审与自动修正，返回评审轨迹便于审计。
     *
     * @param q 用户问题
     * @return 最终答案 + 评审轨迹
     */
    @GetMapping("/chat-reflect")
    public ReflectionOutcome chatReflect(@RequestParam String q) {
        // 输入护栏：与 /chat 相同标准
        String blocked = GuardRail.check(q);
        if (blocked != null) {
            return new ReflectionOutcome(q, blocked, false, List.of(), 0);
        }
        return reflectionService.reflect(q);
    }
}
