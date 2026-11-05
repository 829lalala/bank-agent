package com.bank.agent.tool;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountBalanceToolTest {

    @Test
    void 演示账号只能获得各自的模拟余额() {
        assertTrue(new AccountBalanceTool("admin").getMyAccountBalance().contains("8888.50"));
        assertTrue(new AccountBalanceTool("demo").getMyAccountBalance().contains("1234.56"));
    }

    @Test
    void 未知账号不能创建余额工具() {
        assertThrows(IllegalArgumentException.class, () -> new AccountBalanceTool("guest"));
    }
}
