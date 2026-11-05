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
import com.bank.agent.tool.AccountBalanceTool;
import com.bank.agent.tool.KnowledgeTools;

/**
 * 反思循环服务（Evaluator-Optimizer 模式）——让助手具备"自我检查并修正"的能力。
 *
 * <p>流程：生成初答 → 评估者结构化评审 → 不合格则带评审意见修正 → 再评审（上限 {@value #MAX_REVISE_ROUNDS} 轮修正）。
 *
 * <p>设计要点：
 * 1. 评估者独立于生成者（不同提示词、独立调用），且**挂载知识库检索工具**——
 *    能核对回答中的数字是否与资料一致，而不只是看逻辑通顺；
 * 2. 评审结果用结构化输出（entity 映射为 ReviewResult record），解析失败时**降级为"未知"**
 *    而不是"通过"——拿不到评审结论时既不放行也不判失败，避免死循环的同时不污染质量指标
 *    （ReviewResult.reviewValid = false）；
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

    /**
     * 单轮评审结果（结构化输出映射目标）。
     *
     * <p>三态语义：
     * <ul>
     *   <li>reviewValid=true, pass=true：评审有效且判定合格；</li>
     *   <li>reviewValid=true, pass=false：评审有效但判定不合格，需要修正；</li>
     *   <li>reviewValid=false：评审本身失败（调用异常/JSON 解析失败），结论未知——
     *       此时 {@code pass} 字段无意义，调用方**不得据此认为回答合格**。</li>
     * </ul>
     */
    public record ReviewResult(boolean pass, int score, List<String> issues, String suggestion,
                               boolean reviewValid) {

        /** 评审未知：调用方据此把该轮排除在质量指标之外，而不是当作满分通过 */
        static ReviewResult unknown(String reason) {
            return new ReviewResult(false, -1, List.of(), reason, false);
        }
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
        AccountBalanceTool accountBalanceTool = new AccountBalanceTool(userId);

        while (true) {
            round++;
            // 生成（第 1 轮）或修正（后续轮）
            if (round == 1) {
                answer = generate(question, system, accountBalanceTool);
            } else {
                answer = revise(question, answer, review.suggestion(), system, accountBalanceTool);
            }

            // 评审
            review = evaluate(question, answer);
            rounds.add(new RoundRecord(round, answer, review));
            log.info("[反思] 第{}轮 score={} pass={} issues={}", round, review.score(), review.pass(), review.issues());

            // 结束条件：明确通过 / 评审结论未知（没有可信建议可据此修正）/ 达到修正上限
            if (review.pass() || !review.reviewValid() || round > MAX_REVISE_ROUNDS) {
                // passedReview 语义收紧：只有"评审有效且判定合格"才算通过。
                // 评审未知时如实返回 false——宁可让调用方看到"没通过评审"，
                // 也不能把一次失败的质量检查伪装成满分。
                boolean passed = review.reviewValid() && review.pass();
                // 对话结束：抽取长期画像事实（失败不影响主流程）
                memory.extractAndStore(userId, question, answer);
                return new ReflectionOutcome(question, answer, passed, rounds,
                        System.currentTimeMillis() - start);
            }
        }
    }

    /** 生成初答（与 /agent/chat 相同链路：Agent 人设 + 长期画像 + 全部工具） */
    private String generate(String question, String system, AccountBalanceTool accountBalanceTool) {
        return chatClient.prompt()
                .system(system)
                .tools(bankTools, knowledgeTools, accountBalanceTool)
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
            // 评审降级为"未知"：既不判合格（会污染质量指标）也不判不合格（会触发无意义的修正轮）。
            // 修正轮已由 MAX_REVISE_ROUNDS 兜底，不会因此死循环。
            log.warn("[反思] 评审调用或解析失败，本轮结论标记为未知: {}", e.getMessage());
            return ReviewResult.unknown("评审异常：" + e.getMessage());
        }
    }

    /** 修正：携带评审意见重新生成完整回答 */
    private String revise(String question, String previousAnswer, String feedback, String system,
                          AccountBalanceTool accountBalanceTool) {
        String user = "【用户问题】\n" + question
                + "\n\n【你的上一轮回答】\n" + previousAnswer
                + "\n\n【评审意见】\n" + feedback
                + "\n\n请根据评审意见输出修正后的完整回答（不要提及评审过程）。";
        return chatClient.prompt()
                .system(system)
                .tools(bankTools, knowledgeTools, accountBalanceTool)
                .user(user)
                .call()
                .content();
    }
}
