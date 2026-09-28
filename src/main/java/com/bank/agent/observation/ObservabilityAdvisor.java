package com.bank.agent.observation;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

/**
 * 可观测性拦截器：记录每次对话的问题、耗时、token 用量。
 *
 * <p>为什么用 Advisor：Advisor 链是 Spring AI 的 AOP——拦截器包裹整个
 * "记忆回放 + 工具调用 + 模型生成"流程，天然适合做观测切面。
 *
 * <p>token 用量说明：流式响应中用量信息随最后一个 chunk 返回（或为空），
 * 所以用 AtomicReference 记录流过程中看到的最后一个有效用量，在流结束时打印。
 *
 * <p>多请求并发安全：不使用实例字段存状态（Advisor 是单例），开始时间、
 * 用量都是 adviseStream 方法的局部变量，每个请求各自独立。
 */
public class ObservabilityAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAdvisor.class);

    /** 排在记忆顾问外侧，计时覆盖"记忆回放 + 工具执行 + 模型生成"全流程 */
    private static final int ORDER = DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 1;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 同步调用路径使用；流式路径的重写版 adviseStream 自行处理
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 同步调用路径使用；流式路径在 doFinally 中打印观测数据
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        // 只取最后一条用户消息作为"问题"：prompt.getContents() 会聚合 System Prompt
        // 与历史消息，直接打印会把整个上下文当成问题
        String question = request.prompt().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .reduce((first, second) -> second)
                .map(Message::getText)
                .orElse("");
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        return chain.nextStream(request)
                .doOnNext(response -> {
                    if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
                        Usage usage = response.chatResponse().getMetadata().getUsage();
                        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                            usageRef.set(usage); // 流中最后一个有效用量即整轮消耗
                        }
                    }
                })
                .doFinally(signal -> {
                    long cost = System.currentTimeMillis() - start;
                    Usage usage = usageRef.get();
                    if (usage != null) {
                        log.info("[观测] 问题=\"{}\" 耗时={}ms 提示tokens={} 生成tokens={} 总tokens={}",
                                question, cost,
                                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
                    } else {
                        log.info("[观测] 问题=\"{}\" 耗时={}ms（本次未获取到 token 用量）", question, cost);
                    }
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
