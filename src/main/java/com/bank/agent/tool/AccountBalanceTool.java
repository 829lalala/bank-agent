package com.bank.agent.tool;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;

/** 仅供已登录聊天使用的演示余额工具；账号在创建工具时固定。 */
public final class AccountBalanceTool {

    private static final Map<String, String> DEMO_BALANCES = Map.of(
            "admin", "8888.50",
            "demo", "1234.56");

    private final String accountId;

    public AccountBalanceTool(String accountId) {
        if (!DEMO_BALANCES.containsKey(accountId)) {
            throw new IllegalArgumentException("未知演示账号");
        }
        this.accountId = accountId;
    }

    @Tool(description = "查询当前已登录账号的模拟余额。用户询问自己的余额时调用；不能查询其他人的余额")
    public String getMyAccountBalance() {
        return "当前演示账号 " + accountId + " 的模拟余额为 "
                + DEMO_BALANCES.get(accountId) + " 元（非真实银行数据）";
    }
}
