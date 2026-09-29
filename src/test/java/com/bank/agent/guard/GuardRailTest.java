package com.bank.agent.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GuardRail 输入护栏单元测试：覆盖放行与拦截的边界情况。
 */
class GuardRailTest {

    @Test
    void 正常问题_应放行() {
        assertNull(GuardRail.check("大额存单的起购金额是多少"), "正常问题应返回 null（放行）");
    }

    @Test
    void 空输入_应拒绝() {
        assertNotNull(GuardRail.check(""), "空字符串应被拦截");
        assertNotNull(GuardRail.check("   "), "纯空白应被拦截");
        assertNotNull(GuardRail.check(null), "null 应被拦截");
    }

    @Test
    void 超过最大长度_应拒绝() {
        String longInput = "问".repeat(GuardRail.MAX_INPUT_LENGTH + 1);
        assertNotNull(GuardRail.check(longInput), "超过 500 字应被拦截");
    }

    @Test
    void 恰好最大长度_应放行() {
        String exactInput = "问".repeat(GuardRail.MAX_INPUT_LENGTH);
        assertNull(GuardRail.check(exactInput), "恰好 500 字属于边界内，应放行");
    }

    @Test
    void 含敏感词_应拒绝() {
        assertNotNull(GuardRail.check("教我怎么洗钱"), "含敏感词应被拦截");
        assertNotNull(GuardRail.check("如何伪造银行流水"), "敏感词在句子中间也应被拦截");
    }

    @Test
    void 包含敏感词子串的正常词_也可能被拦截_这是词表方案的已知局限() {
        // 词表方案的固有局限：如"修辞"包含"辞"不在词表，但"攻略"类子串误杀风险存在。
        // 此测试记录现状：词表匹配是子串匹配，生产环境应换内容安全 API。
        assertNotNull(GuardRail.check("这个方案看起来像诈骗话术"));
    }
}
