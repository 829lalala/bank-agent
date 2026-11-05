package com.bank.agent.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

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
    VectorStore vectorStore;

    @Autowired
    Environment environment;

    @Test
    void 库外问题_不得凭常识提供办理步骤() {
        ChatClient client = chatClientBuilder.build();
        String answer = client.prompt()
                .system(SYSTEM_PROMPT)
                .tools(new com.bank.agent.tool.BankTools(),
                        new com.bank.agent.tool.KnowledgeTools(vectorStore))
                .user("怎么办理护照？")
                .call()
                .content();

        assertTrue(isSafeOutOfScopeAnswer(answer),
                "库外问题应拒答且不得提供办理步骤，实际回答：" + answer);
    }

    @Test
    void 生成层评估() {
        ChatClient client = chatClientBuilder.build();
        var knowledgeTools = new com.bank.agent.tool.KnowledgeTools(vectorStore);

        int total = 0, pass = 0;
        List<String> failures = new ArrayList<>();

        // 同一命令可选择题组与重复轮次；不改变原有默认回归题。
        String dataset = System.getProperty("eval.dataset", "regression");
        List<EvalDataset.EvalCase> cases = switch (dataset) {
            case "regression" -> EvalDataset.all();
            case "acceptance" -> EvalDataset.acceptance();
            default -> throw new IllegalArgumentException("eval.dataset 只支持 regression 或 acceptance");
        };
        int rounds = Integer.parseInt(System.getProperty("eval.rounds", "1"));
        if (rounds < 1 || rounds > 5) throw new IllegalArgumentException("评估轮次必须在 1 至 5 之间");
        String model = environment.getProperty("spring.ai.openai.chat.model", "unknown");
        var report = new EvaluationReport(model, dataset,
                environment.getProperty("spring.ai.openai.chat.temperature", "unknown"));
        for (int round = 1; round <= rounds; round++) {
        for (EvalDataset.EvalCase c : cases) {
            long started = System.nanoTime();
            String answer = null;
            org.springframework.ai.chat.metadata.Usage usage = null;
            String error = "";
            try {
            var response = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .tools(new com.bank.agent.tool.BankTools(), knowledgeTools)
                    .user(c.question())
                    .call()
                    .chatResponse();
            if (response != null) {
                answer = response.getResult().getOutput().getText();
                usage = response.getMetadata().getUsage();
            }
            } catch (Exception e) {
                // 报告只记录异常类型，避免将认证头或服务端响应中的敏感信息写入文件。
                error = e.getClass().getSimpleName();
            }

            boolean ok;
            if ("no_answer".equals(c.category())) {
                // 拒答判定：允许不同礼貌表达，但禁止输出具体办理步骤。
                ok = isSafeOutOfScopeAnswer(answer);
            } else {
                // 归一化后断言：模型转述知识库"20 万元"时常省略空格，直接 contains 会误判
                String normAnswer = norm(answer);
                ok = c.expectedFacts().stream().allMatch(f -> normAnswer.contains(norm(f)));
            }
            total++;
            report.add(round, c, answer, ok, (System.nanoTime() - started) / 1_000_000,
                    usage, error);
            if (ok) {
                pass++;
            } else {
                failures.add(c.id() + " " + c.question() + "\n    期望含: " + c.expectedFacts() + "\n    实际: " + answer);
            }
            System.out.printf("[%s] %s %s%n    -> %s%n", ok ? "PASS" : "FAIL", c.id(), c.question(), answer);
        }
        }
        // 即使分数不达标也先落盘，不能只保留成功轮次。
        report.write();

        double rate = total == 0 ? 0 : (double) pass / total;
        System.out.printf("%n========== 生成层评估报告 ==========%n");
        System.out.printf("样本: %d, 通过: %d, 失败: %d, 通过率: %.1f%%%n", total, pass, failures.size(), rate * 100);
        failures.forEach(f -> System.out.println("失败 -> " + f));
        System.out.println("====================================");

        assertTrue(rate >= 0.7, "生成层通过率应 ≥ 70%，实际 " + String.format("%.1f%%", rate * 100));
    }

    /** 归一化：去除空格（含全角空格），规避模型转述时的空格差异 */
    static String norm(String s) {
        return s == null ? "" : s.replaceAll("[\\s　*]", "")
                .replaceAll("(\\d+)周岁[至到](\\d+)周岁", "$1-$2")
                .replace('：', ':');
    }

    /** 检查回答行为，不要求模型逐字复述固定话术。 */
    static boolean isSafeOutOfScopeAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        boolean refused = java.util.regex.Pattern
                .compile("没有相关|没有.*信息|无法|找不到|不能提供|无法提供|抱歉|不属于.*范围|不在.*范围|请.*提供.*年份")
                .matcher(answer).find();
        boolean gaveSteps = java.util.regex.Pattern
                .compile("准备材料|提交申请|等待审核|领取护照|填写.*申请表")
                .matcher(answer).find();
        return refused && !gaveSteps;
    }
}
