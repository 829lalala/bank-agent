package com.bank.agent.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * BankTools 银行业务工具单元测试：验证利息计算的数学正确性。
 */
class BankToolsTest {

    private final BankTools tools = new BankTools();

    @Test
    void 查询余额_应返回模拟金额() {
        String result = tools.getAccountBalance("张三");
        assertTrue(result.contains("张三"), "结果应包含客户名");
        assertTrue(result.contains("8888.50"), "结果应包含模拟数据金额（工具真实执行的证据）");
    }

    @Test
    void 利息_一年期_数学正确() {
        // 10000 × 1.55% × 1 = 155.00
        String result = tools.calcFixedDepositInterest(10000, 1);
        assertTrue(result.contains("155.00"), "一年期利息应为 155.00，实际: " + result);
        assertTrue(result.contains("10155.00"), "本息合计应为 10155.00");
    }

    @Test
    void 利息_三年期_数学正确() {
        // 100000 × 2.20% × 3 = 6600.00
        String result = tools.calcFixedDepositInterest(100000, 3);
        assertTrue(result.contains("6600.00"), "三年期利息应为 6600.00，实际: " + result);
        assertTrue(result.contains("106600.00"), "本息合计应为 106600.00");
    }

    @Test
    void 利息_非法年限_应返回提示而非异常() {
        String result = tools.calcFixedDepositInterest(10000, 5);
        assertTrue(result.contains("仅支持"), "非法年限应返回友好提示而不是抛异常");
    }
}
