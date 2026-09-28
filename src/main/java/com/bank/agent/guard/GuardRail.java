package com.bank.agent.guard;

import java.util.Set;

/**
 * 输入安全护栏：在请求进入模型之前拦截违规内容。
 *
 * <p>为什么要"进入模型之前"拦截：① 省钱（违规请求也消耗 token）；② 快速失败；
 * ③ 模型的"拒绝"不如程序拦截可靠（可被提示词注入绕过）。
 *
 * <p>教学版用关键词匹配（简单可控、零成本）；生产环境通常升级为
 * 专门的意图分类模型或云厂商的内容安全 API（如智谱的内容审核接口），
 * 关键词只是第一道粗筛，还要持续更新词库并统计误杀率。
 */
public final class GuardRail {

    /** 输入最大长度：超长输入既浪费 token，也可能是提示词注入的载体 */
    public static final int MAX_INPUT_LENGTH = 500;

    /** 演示用敏感词表：真实系统应接入内容安全服务并定期更新 */
    private static final Set<String> BLOCKED_KEYWORDS =
            Set.of("洗钱", "伪造", "诈骗", "黑客", "盗取", "攻击系统", "绕过实名");

    private GuardRail() {
    }

    /**
     * 输入检查。
     *
     * @param input 用户输入
     * @return null 表示通过检查；否则返回给用户的拒绝话术
     */
    public static String check(String input) {
        if (input == null || input.isBlank()) {
            return "请输入有效问题。";
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            return "您的问题过长（超过 " + MAX_INPUT_LENGTH + " 字），请精简后再试。";
        }
        for (String keyword : BLOCKED_KEYWORDS) {
            if (input.contains(keyword)) {
                return "您的请求涉及不合规内容，星辰小助手无法协助处理。"
                        + "如有正常业务需求，请拨打客服热线 95577。";
            }
        }
        return null;
    }
}
