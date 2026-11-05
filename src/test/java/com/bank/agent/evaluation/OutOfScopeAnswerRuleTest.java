package com.bank.agent.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutOfScopeAnswerRuleTest {

    @Test
    void 验收题数量编号与分类必须完整() {
        var cases = EvalDataset.acceptance();
        org.junit.jupiter.api.Assertions.assertEquals(45, cases.size());
        org.junit.jupiter.api.Assertions.assertEquals(45,
                cases.stream().map(EvalDataset.EvalCase::id).distinct().count());
        org.junit.jupiter.api.Assertions.assertEquals(35,
                cases.stream().filter(c -> "factual".equals(c.category())).count());
        org.junit.jupiter.api.Assertions.assertEquals(5,
                cases.stream().filter(c -> "comparison".equals(c.category())).count());
        org.junit.jupiter.api.Assertions.assertEquals(5,
                cases.stream().filter(c -> "no_answer".equals(c.category())).count());
        assertTrue(cases.stream().allMatch(c -> !c.question().isBlank()
                && ("no_answer".equals(c.category()) == c.expectedFacts().isEmpty())));
    }

    @Test
    void 能区分拒答和上一轮出现的护照办理步骤() {
        assertTrue(RagAnswerEvaluationTest.isSafeOutOfScopeAnswer(
                "抱歉，无法提供办理护照的具体流程，请咨询出入境管理部门。"));
        assertFalse(RagAnswerEvaluationTest.isSafeOutOfScopeAnswer(
                "办理护照流程：1. 准备材料；2. 提交申请；3. 等待审核；4. 领取护照。"));
    }
}
