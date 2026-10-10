package com.bank.agent.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成层评估：走完整 Agent 链路（提示词 + 工具 + RAG + 模型生成），断言最终回答。
 *
 * <p>默认不运行（LLM 调用慢、有随机性、消耗 token），显式开启才执行：
 * <pre>$env:RUN_LLM_EVAL = "true"; mvn test -Dtest=RagAnswerEvaluationTest</pre>
 *
 * <p>断言策略（容忍表达差异、锁定事实底线）：
 * - factual/comparison：回答必须包含全部关键事实（数字类硬断言，System Prompt 已要求数字一致）
 * - no_answer：回答必须体现拒答（含"没有相关"/"无法"/"不"类表达）
 *
 * <p>已知局限：temperature=0.7 有随机性，偶发失败建议重跑确认；
 * 生产应固定 seed（若模型支持）或多次取多数结果。
 */
@SpringBootTest
class RagAnswerEvaluationTest {

    /** 直接引用公共提示词常量（此前为手动同步副本，Prompts 抽取后消除漂移隐患） */
    private static final String SYSTEM_PROMPT = com.bank.agent.prompts.Prompts.AGENT_SYSTEM_PROMPT;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Autowired
    SimpleVectorStore vectorStore;

    @Test
    void 生成层评估() {
        ChatClient client = chatClientBuilder.build();
        var knowledgeTools = new com.bank.agent.tool.KnowledgeTools(vectorStore);

        int total = 0, pass = 0;
        List<String> failures = new ArrayList<>();

        for (EvalDataset.EvalCase c : EvalDataset.all()) {
            String answer = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .tools(new com.bank.agent.tool.BankTools(), knowledgeTools)
                    .user(c.question())
                    .call()
                    .content();

            boolean ok;
            if ("no_answer".equals(c.category())) {
                // 拒答判定：出现"没有相关/无法/找不到/建议找官方渠道"类表达，且不编造数字
                ok = java.util.regex.Pattern
                        .compile("没有相关|无法|找不到|没有找到|建议.*(官网|客服|工作人员|前往)")
                        .matcher(answer).find();
            } else {
                // 归一化后断言：模型转述知识库"20 万元"时常省略空格，直接 contains 会误判
                String normAnswer = norm(answer);
                ok = c.expectedFacts().stream().allMatch(f -> normAnswer.contains(norm(f)));
            }
            total++;
            if (ok) {
                pass++;
            } else {
                failures.add(c.id() + " " + c.question() + "\n    期望含: " + c.expectedFacts() + "\n    实际: " + answer);
            }
            System.out.printf("[%s] %s %s%n    -> %s%n", ok ? "PASS" : "FAIL", c.id(), c.question(), answer);
        }

        double rate = total == 0 ? 0 : (double) pass / total;
        System.out.printf("%n========== 生成层评估报告 ==========%n");
        System.out.printf("样本: %d, 通过: %d, 失败: %d, 通过率: %.1f%%%n", total, pass, failures.size(), rate * 100);
        failures.forEach(f -> System.out.println("失败 -> " + f));
        System.out.println("====================================");

        assertTrue(rate >= 0.7, "生成层通过率应 ≥ 70%，实际 " + String.format("%.1f%%", rate * 100));
    }

    /** 归一化：去除空格（含全角空格），规避模型转述时的空格差异 */
    private static String norm(String s) {
        return s == null ? "" : s.replace(" ", "").replace("　", "");
    }
}
