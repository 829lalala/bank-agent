package com.bank.agent.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * PathGuard 路径守卫单元测试：穷举路径穿越攻击与正常路径。
 */
class PathGuardTest {

    @Test
    void 正常markdown路径_应解析成功() {
        Path path = PathGuard.resolve("bank-knowledge.md");
        assertTrue(path.startsWith(PathGuard.BASE_DIR), "解析结果必须在白名单目录内");
        assertEquals("bank-knowledge.md", path.getFileName().toString());
    }

    @Test
    void 正常pdf路径_应解析成功() {
        Path path = PathGuard.resolve("sub/handbook.pdf");
        assertTrue(path.startsWith(PathGuard.BASE_DIR), "子目录内的 pdf 也应放行");
        assertEquals("handbook.pdf", path.getFileName().toString());
    }

    @Test
    void 上级目录穿越_应拒绝() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("../../pom.xml"));
        assertTrue(e.getMessage().contains("docs/knowledge"), "拒绝信息应说明白名单目录");
    }

    @Test
    void 深层穿越_应拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("a/b/../../../../../../etc/passwd"));
    }

    @Test
    void 绝对路径_应拒绝() {
        // resolve(绝对路径) 时 Java 会直接采用该绝对路径，必然不在白名单内
        assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("C:/Windows/win.ini"));
        assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("D:/young/JavaCode/AI-Agent/pom.xml"));
    }

    @Test
    void 非法扩展名_应拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("notes.txt"), "txt 不在扩展名白名单");
        assertThrows(IllegalArgumentException.class,
                () -> PathGuard.resolve("script.sh"), "sh 不在扩展名白名单");
    }

    @Test
    void 空路径_应拒绝() {
        assertThrows(IllegalArgumentException.class, () -> PathGuard.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> PathGuard.resolve("  "));
    }
}
