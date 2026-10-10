package com.bank.agent.tool;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * 知识库检索工具：把 RAG 的"检索"环节封装为模型可调用的工具。
 *
 * <p>这就是 Agentic RAG：模型自主判断"这个问题需要查知识库吗"——
 * 产品类问题触发检索，闲聊不触发。相比"每次请求都无条件检索"的传统 RAG：
 * 延迟更低（不查就不等）、噪音更少（上下文只装相关内容）、架构更统一
 * （检索和查余额一样，都是工具）。
 */
public class KnowledgeTools {

    private final SimpleVectorStore vectorStore;

    public KnowledgeTools(SimpleVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "在银行产品知识库中检索资料。当用户询问本行产品（存款利率、理财、信用卡、贷款）或业务办理规则时调用；闲聊、查余额、算利息不要调用。"
            + "对比多个产品的问题，请分别检索每个产品（每次调用只查一个产品），例如问'A和B哪个好'应分两次调用：一次查A、一次查B")
    public String searchBankKnowledge(
            @ToolParam(description = "检索关键词或问题，例如：大额存单 起购金额") String query) {
        // 检索知识库中最相似的 3 个片段（与 /rag/ask 相同的检索逻辑）。
        // 排查记录：E13（对比型）曾误报 MISS，归因"topK 不足"——加 MISS 诊断打印后
        // 发现真因是评估测试重复摄取，重复主题占满窗口挤出另一主题；
        // 干净库上 topK=3 即可 13/13 全过，故保持 3（窗口小、噪声少）
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build());

        if (hits == null || hits.isEmpty()) {
            return "知识库中未找到相关资料";
        }

        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeTools.class);
        log.info("[工具调用] 检索知识库 query=\"{}\" 命中 {} 块", query, hits.size());

        // 引用溯源：每段资料带上编号与来源标签，System Prompt 要求模型回答时标注依据。
        // 来源元数据在摄取时写入：Markdown 为 source（文件名），PDF 为 file_name + page_number
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (Document doc : hits) {
            Object source = doc.getMetadata().get("source");
            if (source == null) {
                source = doc.getMetadata().get("file_name");
            }
            Object pageNumber = doc.getMetadata().get("page_number");
            String label = (source == null ? "未知来源" : source.toString())
                    + (pageNumber != null ? " 第" + pageNumber + "页" : "");
            context.append("[资料").append(index++).append("·来源: ").append(label).append("]\n")
                    .append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }
}
