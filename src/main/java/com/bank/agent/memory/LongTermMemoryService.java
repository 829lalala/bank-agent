package com.bank.agent.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 跨会话长期记忆服务：维护每个用户的画像事实（姓名、风险偏好、投资偏好等）。
 *
 * <p>与短期记忆的区别：短期记忆（MessageWindowChatMemory）按会话隔离、重启丢失；
 * 长期记忆按**用户**隔离、跨会话存在——"换个会话 ID 依然记得你是谁"。
 *
 * <p>三步工作流：
 * 1. recall：生成前取回该用户的已知事实；
 * 2. 注入：事实拼入 System Prompt，让回答个性化（如按风险偏好筛选推荐）；
 * 3. extractAndStore：对话结束后用 LLM 抽取新事实（结构化输出），去重合并入画像。
 *
 * <p>存储说明：内存 Map（重启丢失，教学够用）；生产换数据库/Redis。
 * 抽取为同步调用（每轮多 1 次 LLM 调用，约 1~2s）；生产可改异步。
 */
@Service
public class LongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryService.class);

    /** 每用户画像上限：防无限膨胀，超出淘汰最早的事实 */
    private static final int MAX_FACTS = 20;

    /** userId → 画像事实列表 */
    private final Map<String, List<String>> store = new ConcurrentHashMap<>();

    private final ChatClient chatClient;

    /** 事实抽取提示词：只抽取用户明确表达的事实，禁止推测 */
    private static final String EXTRACTOR_PROMPT = """
            从对话中抽取值得长期记住的用户个人信息与偏好（如姓名、风险偏好、投资偏好、已购产品、所在城市等）。
            规则：
            1. 只抽取用户明确表达的事实，禁止推测；
            2. 与"已知用户信息"重复的内容不要重复抽取；
            3. 助手的回答内容不属于用户信息，不要抽取；
            4. 没有新事实时返回空数组。
            只输出如下 JSON，不要输出任何其他内容：
            {"facts": ["用户姓名是张三", "用户风险偏好为低风险，只接受 R2 及以下产品"]}
            """;

    /** LLM 抽取结果的结构化映射目标 */
    public record UserFacts(List<String> facts) {
    }

    public LongTermMemoryService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /** 取回用户画像事实 */
    public List<String> recall(String userId) {
        return store.getOrDefault(userId, List.of());
    }

    /**
     * 拼装注入 System Prompt 的画像上下文。
     *
     * @return 无事实时返回空串（不污染提示词）
     */
    public String buildContext(String userId) {
        List<String> facts = recall(userId);
        log.info("[长期记忆] buildContext userId=\"{}\" 召回事实数={}", userId, facts.size());
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【该用户已确认的信息——回答时必须直接遵循，严禁再次询问以下任何一项】\n");
        facts.forEach(f -> sb.append("- ").append(f).append("\n"));
        sb.append("例如：已知用户姓名时直接称呼，已知用户风险偏好为低风险时只能推荐 R1/R2 产品且不要再询问其风险偏好。");
        return sb.toString();
    }

    /**
     * 对话结束后抽取新事实并入画像（幂等：与已有事实重复的自动跳过）。
     *
     * @param userId   用户标识
     * @param question 本轮用户输入
     * @param answer   本轮助手回答
     */
    public void extractAndStore(String userId, String question, String answer) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        log.info("[长期记忆] 开始抽取 userId={} 问题长度={} 回答长度={} 线程={}",
                userId, question.length(), answer.length(), Thread.currentThread().getName());
        try {
            String user = "【已知用户信息】\n" + String.join("\n", recall(userId))
                    + "\n\n【本轮对话】\n用户：" + question + "\n助手：" + answer;
            UserFacts result = chatClient.prompt()
                    .system(EXTRACTOR_PROMPT)
                    .user(user)
                    .call()
                    .entity(UserFacts.class);
            log.info("[长期记忆] 抽取结果 facts={}", result == null ? "null" : result.facts());

            if (result == null || result.facts() == null || result.facts().isEmpty()) {
                return; // 本轮没有新事实
            }
            List<String> facts = new ArrayList<>(recall(userId));
            boolean added = false;
            for (String fact : result.facts()) {
                if (fact != null && !fact.isBlank() && !facts.contains(fact.trim())) {
                    facts.add(fact.trim());
                    added = true;
                }
            }
            while (facts.size() > MAX_FACTS) {
                facts.remove(0); // 超上限淘汰最早的
            }
            store.put(userId, facts);
            if (added) {
                log.info("[长期记忆] userId={} 画像更新，新增 {} 条，当前共 {} 条", userId, result.facts().size(), facts.size());
            }
        } catch (Exception e) {
            // 记忆抽取失败不应影响主流程：记完整堆栈便于排查
            log.warn("[长期记忆] 抽取失败（忽略）", e);
        }
    }
}
