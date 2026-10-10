package com.bank.agent.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.bank.agent.memory.LongTermMemoryService;
import com.bank.agent.prompts.Prompts;
import com.bank.agent.tool.BankTools;
import com.bank.agent.tool.KnowledgeTools;

/**
 * 反思循环服务（Evaluator-Optimizer 模式）——让助手具备"自我检查并修正"的能力。
 *
 * <p>流程：生成初答 → 评估者结构化评审 → 不合格则带评审意见修正 → 再评审（上限 {@value #MAX_REVISE_ROUNDS} 轮修正）。
 *
 * <p>设计要点：
 * 1. 评估者独立于生成者（不同提示词、独立调用），且**挂载知识库检索工具**——
 *    能核对回答中的数字是否与资料一致，而不只是看逻辑通顺；
 * 2. 评审结果用结构化输出（entity 映射为 ReviewResult record），解析失败降级为通过，
 *    防止评估异常导致死循环；
 * 3. 修正轮有上限（防成本失控），超上限即使未通过也返回最后一次答案并标记 pass=false。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    /** 修正轮上限：每轮修正 = 1 次修正生成 + 1 次评审，防成本失控 */
    private static final int MAX_REVISE_ROUNDS = 2;

    /** 评审员提示词：结构化评审维度 + JSON 输出约束 */
    private static final String REVIEWER_PROMPT = """
            你是银行智能助手回答的质量评审员。请严格评审给定的"AI 回答"是否合格。
            评审维度：
            1. 事实准确性：回答中的产品数字（利率/额度/期限/规则）如需核实，必须调用知识库检索工具核对；
            2. 问题完整性：是否回答了用户问题的全部诉求；
            3. 比较类口径：不同期限产品的收益比较必须统一年化口径，并提示风险等级差异
               （定期存款保本，理财为业绩比较基准且非保本）；
            4. 来源标注：引用产品信息时应有（依据：资料N）标注。
            只输出如下 JSON，不要输出任何其他内容：
            {"pass": true或false, "score": 0到10的整数, "issues": ["问题1", "问题2"], "suggestion": "给修正者的具体建议"}
            """;

    private final ChatClient chatClient;
    private final LongTermMemoryService memory;

    public ReflectionService(BankTools bankTools, KnowledgeTools knowledgeTools,
                             LongTermMemoryService memory, ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.bankTools = bankTools;
        this.knowledgeTools = knowledgeTools;
        this.memory = memory;
    }

    private final BankTools bankTools;
    private final KnowledgeTools knowledgeTools;

    /** 单轮评审结果（结构化输出映射目标） */
    public record ReviewResult(boolean pass, int score, List<String> issues, String suggestion) {
    }

    /** 单轮记录：该轮答案 + 评审结论 */
    public record RoundRecord(int round, String answer, ReviewResult review) {
    }

    /** 反思循环最终结果 */
    public record ReflectionOutcome(String question, String finalAnswer, boolean passedReview,
                                    List<RoundRecord> rounds, long elapsedMs) {
    }

    /**
     * 执行反思循环（含长期记忆注入与画像抽取）。
     *
     * @param question 用户问题
     * @param userId   用户标识：决定注入哪些长期画像事实
     * @return 评审轨迹与最终答案
     */
    public ReflectionOutcome reflect(String question, String userId) {
        long start = System.currentTimeMillis();
        List<RoundRecord> rounds = new ArrayList<>();
        String answer = null;
        ReviewResult review = null;
        int round = 0;
        // 长期画像注入：每个生成/修正轮共用同一份个性化 System Prompt
        String system = Prompts.AGENT_SYSTEM_PROMPT + memory.buildContext(userId);

        while (true) {
            round++;
            // 生成（第 1 轮）或修正（后续轮）
            if (round == 1) {
                answer = generate(question, system);
            } else {
                answer = revise(question, answer, review.suggestion(), system);
            }

            // 评审
            review = evaluate(question, answer);
            rounds.add(new RoundRecord(round, answer, review));
            log.info("[反思] 第{}轮 score={} pass={} issues={}", round, review.score(), review.pass(), review.issues());

            // 通过，或达到修正上限：结束
            if (review.pass() || round > MAX_REVISE_ROUNDS) {
                boolean passed = review.pass();
                // 对话结束：抽取长期画像事实（失败不影响主流程）
                memory.extractAndStore(userId, question, answer);
                return new ReflectionOutcome(question, answer, passed, rounds,
                        System.currentTimeMillis() - start);
            }
        }
    }

    /** 生成初答（与 /agent/chat 相同链路：Agent 人设 + 长期画像 + 全部工具） */
    private String generate(String question, String system) {
        return chatClient.prompt()
                .system(system)
                .tools(bankTools, knowledgeTools)
                .user(question)
                .call()
                .content();
    }

    /** 评审：独立 LLM 调用，挂知识库检索工具以核对回答中的数字 */
    private ReviewResult evaluate(String question, String answer) {
        String user = "【用户问题】\n" + question + "\n\n【AI 回答】\n" + answer;
        try {
            return chatClient.prompt()
                    .system(REVIEWER_PROMPT)
                    .tools(knowledgeTools)
                    .user(user)
                    .call()
                    .entity(ReviewResult.class);
        } catch (Exception e) {
            // 评审异常降级为通过：宁可通过也不死循环（生产可改为重试或人工兜底）
            log.warn("[反思] 评审解析失败，降级为通过: {}", e.getMessage());
            return new ReviewResult(true, 10, List.of(), "评审异常降级");
        }
    }

    /** 修正：携带评审意见重新生成完整回答 */
    private String revise(String question, String previousAnswer, String feedback, String system) {
        String user = "【用户问题】\n" + question
                + "\n\n【你的上一轮回答】\n" + previousAnswer
                + "\n\n【评审意见】\n" + feedback
                + "\n\n请根据评审意见输出修正后的完整回答（不要提及评审过程）。";
        return chatClient.prompt()
                .system(system)
                .tools(bankTools, knowledgeTools)
                .user(user)
                .call()
                .content();
    }
}
