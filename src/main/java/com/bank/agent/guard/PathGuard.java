package com.bank.agent.guard;

import java.nio.file.Path;
import java.util.Set;

/**
 * 知识库文档路径守卫：防止路径穿越攻击。
 *
 * <p>从 RagController 抽取为独立类的原因：内联在业务方法里的校验逻辑
 * 无法单独测试（为可测性重构）。抽成纯函数后，可以用单元测试穷举
 * 各种攻击路径，而不需要启动整个应用。
 */
public final class PathGuard {

    /** 白名单根目录：所有知识库文档必须位于此目录内（相对项目工作目录） */
    static final Path BASE_DIR = Path.of("docs/knowledge").toAbsolutePath().normalize();

    /** 扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".md", ".pdf");

    private PathGuard() {
    }

    /**
     * 把用户传入的相对路径解析为白名单目录内的安全路径。
     *
     * @param relativePath 相对 docs/knowledge 目录的路径，例如 "bank-knowledge.md"
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 路径为空、越出白名单目录、或扩展名不允许时抛出
     */
    public static Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文档路径不能为空");
        }
        // resolve + normalize 消解 ../ 等相对片段，再用 startsWith 判断是否仍在白名单内
        Path target = BASE_DIR.resolve(relativePath).normalize();
        if (!target.startsWith(BASE_DIR)) {
            throw new IllegalArgumentException("拒绝访问：文档路径必须位于 docs/knowledge 目录内。");
        }
        String lowerName = target.getFileName().toString().toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new IllegalArgumentException("仅支持 .md 与 .pdf 文档。");
        }
        return target;
    }
}
