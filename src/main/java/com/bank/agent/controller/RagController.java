package com.bank.agent.controller;

import com.bank.agent.config.RagConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * RAG 控制器：文档摄取 + 知识库问答
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    /** 知识库默认文档：path 参数是相对 docs/knowledge 目录的路径 */
    private static final String KNOWLEDGE_FILE = "bank-knowledge.md";

    private final SimpleVectorStore vectorStore;
    private final ChatClient chatClient;

    public RagController(SimpleVectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 触发文档摄取（ETL 前半段），支持 Markdown 与 PDF 两种格式。
     * 用法：
     *   POST http://localhost:8080/rag/load                        → 默认摄取金融知识库
     *   POST http://localhost:8080/rag/load?path=学生手册.pdf       → 摄取指定文档
     *
     * <p>path 是相对 docs/knowledge 目录的路径（目录前缀由服务端固定，防路径穿越）。
     * 格式按扩展名识别：.pdf 走 PagePdfDocumentReader（按页），其余走
     * MarkdownDocumentReader（按标题）。
     *
     * <p>注意：SimpleVectorStore 是内存实现，摄取后自动落盘，重启时自动恢复。
     *
     * @param path 文档相对路径（相对 docs/knowledge 目录）
     * @return 摄取统计信息
     */
    @PostMapping("/load")
    public String load(@RequestParam(defaultValue = KNOWLEDGE_FILE) String path) {
        // 安全护栏：路径规范化 + 白名单目录校验，防止 ../ 穿越读取任意文件
        //（如 ?path=../../pom.xml 或 ?path=C:/Windows/...）
        Path baseDir = Path.of("docs/knowledge").toAbsolutePath().normalize();
        Path target = baseDir.resolve(path).normalize();
        if (!target.startsWith(baseDir)) {
            return "拒绝访问：文档路径必须位于 docs/knowledge 目录内。";
        }
        String fileName = target.getFileName().toString();
        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".pdf")) {
            return "仅支持 .md 与 .pdf 文档。";
        }

        // ① 读：按扩展名选择读取器，统一产出 List<Document>
        FileSystemResource resource = new FileSystemResource(target.toFile());
        if (!resource.exists()) {
            return "文件不存在：" + path;
        }
        List<Document> sections;
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            // PDF 按页读取，自动携带文件名、页码元数据
            sections = new PagePdfDocumentReader(resource).get();
        } else {
            // Markdown 按标题层级读取，段落语义完整
            sections = new MarkdownDocumentReader(resource,
                    MarkdownDocumentReaderConfig.builder()
                            .withAdditionalMetadata("source", fileName)
                            .build()).get();
        }

        // ② 切：TokenTextSplitter 按 token 数把过长的段落细切
        List<Document> chunks = new TokenTextSplitter().apply(sections);

        // 边界防御：提取不到文本时给出可操作的提示，而不是抛出 500 错误
        if (chunks.isEmpty()) {
            return "未能从文档中提取到任何文本。最常见原因：扫描型 PDF（整页是图片、无文字层），"
                    + "需要先做 OCR 才能摄取。可用 Ctrl+F 搜索法确认文档类型。";
        }

        // ③ 存：add() 自动向量化并写入向量库
        vectorStore.add(chunks);

        // ④ 落盘：摄取成功后立即保存到本地文件（比关闭钩子更可靠——强杀也不丢）。
        //    save() 是 SimpleVectorStore 特有能力，故本类注入具体类型而非 VectorStore 接口
        try {
            RagConfig.DATA_FILE.getParentFile().mkdirs();
            vectorStore.save(RagConfig.DATA_FILE);
        } catch (Exception e) {
            return "摄取完成但落盘失败：" + e.getMessage();
        }

        return "摄取完成：" + fileName
                + "，标题/页段落数=" + sections.size()
                + "，切分后块数=" + chunks.size();
    }

    /**
     * RAG 知识库问答（流式）。
     * 用法：GET http://localhost:8080/rag/ask?q=稳盈90天理财的起购金额是多少
     *
     * <p>流程：④ 检索知识块 → ⑤ 把知识块作为参考资料拼进提示词 → 模型生成回答。
     *
     * @param q 用户问题
     * @return 流式回答
     */
    @GetMapping("/ask")
    public ResponseEntity<Flux<String>> ask(@RequestParam String q) {
        // ④ 检索：把问题向量化，在向量库中找语义最相似的 3 个知识块
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(q)
                        .topK(3) // 取最相似的前 3 块：太少信息不够，太多噪音多且费 token
                        .build());

        // 检索不到（库为空或无相关内容）时如实告知，不硬答
        if (hits == null || hits.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                    .body(Flux.just("知识库中没有找到相关资料（请先执行 POST /rag/load 摄取文档）。"));
        }

        // ⑤ 组装：把命中块拼成参考资料。模型被约束"只依据资料回答"，
        //    这是抑制幻觉（编造事实）的关键手段
        StringBuilder context = new StringBuilder();
        for (Document doc : hits) {
            context.append("---\n").append(doc.getText()).append("\n");
        }

        String systemPrompt = """
                你是银行的智能业务助手。请仅依据下面的参考资料回答用户问题。
                如果参考资料中没有答案，如实回答"知识库中没有相关信息"，绝不编造。
                数字（利率、金额、期限）必须与参考资料完全一致。

                参考资料：
                """ + context;

        Flux<String> body = chatClient.prompt()
                .system(systemPrompt)
                .user(q)
                .stream()
                .content();

        // 用 ResponseEntity 显式声明 Content-Type 含 charset=UTF-8：
        // produces 方式会被 Spring 的 SSE 输出路径剥离 charset，
        // 导致浏览器直接打开接口时按 windows-1252 误读中文
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .body(body);
    }
}
