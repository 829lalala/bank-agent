package com.bank.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 银行业务工具集：模型可通过 Function Calling 调用的"双手"。
 *
 * <p>工作原理：Spring AI 把 @Tool 的 description（写给模型看的说明书）随请求发给模型；
 * 模型判断需要时返回"调用意图"（工具名 + 参数），由 Spring AI 在本进程内执行真正的
 * Java 方法，再把结果回传给模型生成回答。模型只"点菜"，永远不执行代码——
 * 鉴权、审计、权限控制都在我们的方法体内，这是安全的边界。
 *
 * <p>注意：本类的数据是模拟的；真实场景应在方法内查询数据库/核心系统。
 */
public class BankTools {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BankTools.class);

    @Tool(description = "查询指定客户的银行账户余额。当用户询问账户余额、卡里还有多少钱、查余额时调用")
    public String getAccountBalance(
            @ToolParam(description = "客户姓名，例如：张三") String customerName) {
        log.info("[工具调用] 查询余额 customerName={}", customerName);
        // 模拟数据：真实场景此处查询核心系统（需登录态与权限校验）
        double balance = 8888.50;
        return String.format("客户 %s 的账户余额为 %.2f 元", customerName, balance);
    }

    @Tool(description = "计算定期存款（整存整取）到期利息。当用户询问存一笔钱到期利息多少、本息合计多少时调用")
    public String calcFixedDepositInterest(
            @ToolParam(description = "存款本金，单位元，例如 10000") double principal,
            @ToolParam(description = "存款期限年数，只支持 1、2 或 3") int years) {
        log.info("[工具调用] 计算利息 principal={} years={}", principal, years);
        // 利率与本行知识库保持一致（真实场景应从利率表读取）
        double rate = switch (years) {
            case 1 -> 0.0155;
            case 2 -> 0.0185;
            case 3 -> 0.0220;
            default -> -1;
        };
        if (rate < 0) {
            return "本工具仅支持 1、2、3 年期整存整取的利息计算";
        }
        double interest = principal * rate * years;
        return String.format("本金 %.2f 元，存期 %d 年，年利率 %.2f%%，到期利息约 %.2f 元，本息合计 %.2f 元",
                principal, years, rate * 100, interest, principal + interest);
    }
}
