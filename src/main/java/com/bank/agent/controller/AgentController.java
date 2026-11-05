package com.bank.agent.controller;

import com.bank.agent.guard.GuardRail;
import com.bank.agent.memory.LongTermMemoryService;
import com.bank.agent.observation.ObservabilityAdvisor;
import com.bank.agent.prompts.Prompts;
import com.bank.agent.service.ReflectionService;
import com.bank.agent.service.ReflectionService.ReflectionOutcome;
import com.bank.agent.tool.BankTools;
import com.bank.agent.tool.AccountBalanceTool;
import com.bank.agent.tool.KnowledgeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final LongTermMemoryService memory;

    /** 页面只需显示账号名称，不向浏览器返回密码或内部权限信息。 */
    @GetMapping("/me")
    public Map<String, String> currentUser(Principal principal) {
        return Map.of("username", principal.getName());
    }

    /** 退出表单使用的防伪令牌。 */
    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken token) {
        return Map.of("parameterName", token.getParameterName(), "token", token.getToken());
    }

    public AgentController(BankTools bankTools, KnowledgeTools knowledgeTools,
                           ChatMemory chatMemory, ChatClient.Builder chatClientBuilder,
                           ReflectionService reflectionService, LongTermMemoryService memory) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeTools = knowledgeTools;
        this.bankTools = bankTools;
        this.reflectionService = reflectionService;
        this.memory = memory;
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
                                             @RequestParam String conversationId,
                                             Principal principal) {
        // 浏览器提供的编号仅用于区分同一账号的会话，不能充当用户身份。
        try {
            UUID.fromString(conversationId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话编号格式无效");
        }
        String userId = principal.getName();
        String scopedConversationId = userId + ":" + conversationId;
        // 输入护栏：在进入模型之前拦截（省钱、快速失败、比模型自我审查更可靠）
        String blocked = GuardRail.check(q);
        if (blocked != null) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                    .body(Flux.just(blocked));
        }

        StringBuilder answerBuf = new StringBuilder();
        Flux<String> body = chatClient.prompt()
                // 长期画像注入：个性化 System Prompt（无事实时不追加）
                .system(Prompts.AGENT_SYSTEM_PROMPT + memory.buildContext(userId))
                .tools(bankTools, knowledgeTools, new AccountBalanceTool(userId))
                .user(q)
                .advisors(memoryAdvisor)
                // 2.0 新要求：记忆顾问必须显式拿到会话 ID，通过 advisor 参数传递
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, scopedConversationId))
                .advisors(new ObservabilityAdvisor()) // 可观测性：耗时 + token 用量
                .stream()
                .content()
                // 累积回答文本，流结束后抽取长期画像事实（抽取失败不影响已完成的响应）
                .doOnNext(answerBuf::append)
                .doFinally(sig -> memory.extractAndStore(userId, q, answerBuf.toString()));

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * 查看用户长期画像（调试与管理用）。
     *
     * @return 当前登录用户的全部画像事实
     */
    @GetMapping("/memory")
    public List<String> memory(Principal principal) {
        return memory.recall(principal.getName());
    }

    /**
     * 反思模式对话（同步 JSON）：生成 → 评审 → 修正循环（Evaluator-Optimizer）。
     * 用法示例：
     *   GET http://localhost:8080/agent/chat-reflect?q=我有1万块存三年定期和买稳盈90天哪个划算
     *
     * <p>与 /chat 的区别：多 2~4 次 LLM 调用（评审 + 修正），延迟更高，
     * 但回答经过质量评审与自动修正，返回评审轨迹便于审计。
     *
     * @param q      用户问题
     * @param principal 当前登录用户：注入其长期画像，并在对话后更新其画像
     * @return 最终答案 + 评审轨迹
     */
    @GetMapping("/chat-reflect")
    public ReflectionOutcome chatReflect(@RequestParam String q,
                                         Principal principal) {
        // 输入护栏：与 /chat 相同标准
        String blocked = GuardRail.check(q);
        if (blocked != null) {
            return new ReflectionOutcome(q, blocked, false, List.of(), 0);
        }
        return reflectionService.reflect(q, principal.getName());
    }
}
