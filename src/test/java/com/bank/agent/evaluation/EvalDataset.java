package com.bank.agent.evaluation;

import java.util.List;

/**
 * RAG 评估数据集。
 *
 * <p>三种类型：
 * - factual：事实型，标准答案含关键事实（数字/规则），检索与生成层都断言"包含关键事实"
 * - comparison：对比型，需要综合两段资料作答
 * - no_answer：无答案型，知识库中没有相关问题，期望模型拒答（"没有相关信息"）
 *
 * <p>设计原则：问题措辞刻意与知识库原文不同（测语义检索而非关键词匹配）；
 * expectedFacts 断言的是关键事实片段（数字、规则），不是完整句子——
 * 容忍模型的表达差异，锁定事实底线。
 *
 * <p>生产化提示：真实项目应放 JSON/CSV 由评估平台管理，支持多人标注与版本管理。
 */
public final class EvalDataset {

    /**
     * @param id            用例编号
     * @param question      评估问题
     * @param category      类型：factual / comparison / no_answer
     * @param expectedFacts 关键事实片段（答案必须包含的数字/规则）
     */
    public record EvalCase(String id, String question, String category, List<String> expectedFacts) {
    }

    public static final List<EvalCase> CASES = List.of(
            // ---- 事实型 ----
            new EvalCase("E01", "大额存单的起购金额是多少？", "factual", List.of("20 万元")),
            new EvalCase("E02", "我想买稳盈90天理财，最少要存多少钱？", "factual", List.of("1 万元")),
            new EvalCase("E03", "稳盈90天理财的业绩比较基准是多少？", "factual", List.of("2.85%")),
            new EvalCase("E04", "三年期定期存款的利率是多少？", "factual", List.of("2.20%")),
            new EvalCase("E05", "白金信用卡的年费标准是什么？", "factual", List.of("800 元")),
            new EvalCase("E06", "白金卡消费积分怎么累计？", "factual", List.of("2 积分", "3 倍积分")),
            new EvalCase("E07", "首套房贷最高能贷房价的几成？", "factual", List.of("70%")),
            new EvalCase("E08", "星辰消费贷最多能借多少钱？", "factual", List.of("30 万元")),
            new EvalCase("E09", "稳盈365天理财最低买多少？", "factual", List.of("5 万元")),
            new EvalCase("E10", "消费贷对借款人年龄有什么要求？", "factual", List.of("22-55")),
            new EvalCase("E11", "进取混合基金投顾策略的风险等级？", "factual", List.of("R4")),
            new EvalCase("E12", "你们网点周末几点开门？", "factual", List.of("10:00")),
            // ---- 对比型 ----
            new EvalCase("E13", "稳盈90天和稳盈365天哪个起购门槛更低？", "comparison", List.of("1 万元")),
            // ---- 无答案型（期望拒答） ----
            new EvalCase("E14", "怎么办理护照？", "no_answer", List.of()),
            new EvalCase("E15", "贵行去年年报的净利润是多少？", "no_answer", List.of()));

    private EvalDataset() {
    }

    /** 检索层评估只跑有标准答案的类型（no_answer 在检索层无法断言"不相关"） */
    public static List<EvalCase> withAnswer() {
        return CASES.stream().filter(c -> !"no_answer".equals(c.category())).toList();
    }

    public static List<EvalCase> all() {
        return CASES;
    }
}
