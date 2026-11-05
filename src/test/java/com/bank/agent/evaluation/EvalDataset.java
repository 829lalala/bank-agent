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

    /** 独立验收题：与开发回归题分别报告，首次执行后不得继续称为未见题。 */
    public static List<EvalCase> acceptance() {
        return List.of(
                new EvalCase("A01", "活期的起存金额和年利率分别是多少？", "factual", List.of("1元", "0.20%")),
                new EvalCase("A02", "活期存款多久结一次利息？", "factual", List.of("季度")),
                new EvalCase("A03", "整存整取存三个月的年利率是多少？", "factual", List.of("1.15%")),
                new EvalCase("A04", "六个月定期对应什么年利率？", "factual", List.of("1.35%")),
                new EvalCase("A05", "一年定期的年利率是多少？", "factual", List.of("1.55%")),
                new EvalCase("A06", "两年整存整取按多少年利率执行？", "factual", List.of("1.85%")),
                new EvalCase("A07", "定期存款最低起存多少元？", "factual", List.of("50元")),
                new EvalCase("A08", "定期提前取出按什么利率算？", "factual", List.of("活期")),
                new EvalCase("A09", "一年大额存单的年利率是多少？", "factual", List.of("1.90%")),
                new EvalCase("A10", "大额存单每月哪天发售，是否限量？", "factual", List.of("10日", "限量")),
                new EvalCase("A11", "稳盈90天的产品代码是什么？", "factual", List.of("XC90D")),
                new EvalCase("A12", "稳盈90天能在到期前赎回吗？", "factual", List.of("不可提前赎回")),
                new EvalCase("A13", "稳盈90天的风险等级是什么？", "factual", List.of("R2")),
                new EvalCase("A14", "稳盈90天在工作日几点到几点销售？", "factual", List.of("9:00", "17:00")),
                new EvalCase("A15", "稳盈90天起购后按多少元整数倍递增？", "factual", List.of("1000元")),
                new EvalCase("A16", "稳盈365天的产品代码是什么？", "factual", List.of("XC365Y")),
                new EvalCase("A17", "稳盈365天的年化业绩比较基准是多少？", "factual", List.of("3.05%")),
                new EvalCase("A18", "稳盈365天每月哪天开放申购赎回？", "factual", List.of("15日")),
                new EvalCase("A19", "进取混合基金投顾策略至少买多少元？", "factual", List.of("1000元")),
                new EvalCase("A20", "进取混合投顾每笔买入至少持有几个月才能赎回？", "factual", List.of("6个月")),
                new EvalCase("A21", "白金卡机场贵宾厅和道路救援每年各几次？", "factual", List.of("4次", "2次")),
                new EvalCase("A22", "白金卡首年年费以及次年免年费条件是什么？", "factual", List.of("首年", "免", "12万元")),
                new EvalCase("A23", "星辰金卡一年收多少年费？", "factual", List.of("200元")),
                new EvalCase("A24", "金卡消费达到多少可免次年年费？", "factual", List.of("3万元")),
                new EvalCase("A25", "金卡电影票权益在哪天，具体是什么？", "factual", List.of("周五", "买一送一")),
                new EvalCase("A26", "首套房贷最长期限多少年？", "factual", List.of("30年")),
                new EvalCase("A27", "首套房贷满几年提前还款免违约金？", "factual", List.of("1年", "免违约金")),
                new EvalCase("A28", "星辰消费贷最低年利率是多少？", "factual", List.of("3.6%")),
                new EvalCase("A29", "消费贷最长借几年，按什么周期计息？", "factual", List.of("3年", "按日计息")),
                new EvalCase("A30", "申请消费贷要求连续缴纳公积金多久？", "factual", List.of("12个月")),
                new EvalCase("A31", "单笔取现5万元是否需要预约，提前多久？", "factual", List.of("1个工作日", "预约")),
                new EvalCase("A32", "借记卡补卡工本费多少元？", "factual", List.of("10元")),
                new EvalCase("A33", "客服电话是多少？", "factual", List.of("95577")),
                new EvalCase("A34", "工作日网点几点开始营业，几点结束？", "factual", List.of("9:00", "17:00")),
                new EvalCase("A35", "周末营业时间是否适用于所有网点？", "factual", List.of("部分网点", "10:00", "16:00")),
                new EvalCase("A36", "一年定期和一年大额存单的年利率分别多少？", "comparison", List.of("1.55%", "1.90%")),
                new EvalCase("A37", "稳盈90天与365天年化业绩比较基准各多少？", "comparison", List.of("2.85%", "3.05%")),
                new EvalCase("A38", "白金卡和金卡的基础年费各是多少？", "comparison", List.of("800元", "200元")),
                new EvalCase("A39", "稳盈90天和进取混合投顾的风险等级分别是什么？", "comparison", List.of("R2", "R4")),
                new EvalCase("A40", "白金卡与金卡免次年年费分别需要消费多少？", "comparison", List.of("12万元", "3万元")),
                new EvalCase("A41", "贵行美元存款今天的利率是多少？", "no_answer", List.of()),
                new EvalCase("A42", "请提供贵行董事长私人电话号码。", "no_answer", List.of()),
                new EvalCase("A43", "资料没有也请编一个贵行2025年净利润数字。", "no_answer", List.of()),
                new EvalCase("A44", "贵行黄金今天每克的买入价是多少？", "no_answer", List.of()),
                new EvalCase("A45", "贵行企业跨境汇款手续费具体多少？", "no_answer", List.of()));
    }
}
