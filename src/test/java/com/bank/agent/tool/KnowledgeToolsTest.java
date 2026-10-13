package com.bank.agent.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * KnowledgeTools 知识库检索工具单元测试。
 *
 * <p>技术要点：直接 mock VectorStore 接口（KnowledgeTools 的依赖），而不是
 * 构造真实向量库 + mock EmbeddingModel。理由：KnowledgeTools 的职责是
 * "调用检索 + 格式化溯源标签"，向量库内部实现（向量化/相似度计算）不是
 * 它的测试范围——单元测试只测"自己这一层"的逻辑，依赖全部隔离。
 *
 * <p>第一次尝试（真实 store + mock EmbeddingModel）失败于
 * "Vectors must not be null"：store 内部走的 embedding 调用链与 stub 的
 * 方法不匹配——这正是"mock 依赖而不是 mock 依赖的实现细节"的教训。
 */
class KnowledgeToolsTest {

    private VectorStore mockStore;
    private KnowledgeTools tools;

    /** 预置的两条命中资料（模拟向量库检索结果） */
    private static final List<Document> HITS = List.of(
            new Document("大额存单 20 万元起购，一年期年利率 1.90%", Map.of("source", "bank-knowledge.md")),
            new Document("星辰白金信用卡年费 800 元/年，首年免年费", Map.of("source", "credit-card.md")));

    @BeforeEach
    void setUp() {
        mockStore = Mockito.mock(VectorStore.class);
        tools = new KnowledgeTools(mockStore);
    }

    @Test
    void 检索命中_应返回资料内容与溯源标签() {
        when(mockStore.similaritySearch(any(SearchRequest.class))).thenReturn(HITS);

        String result = tools.searchBankKnowledge("大额存单");
        assertTrue(result.contains("[资料1·来源: bank-knowledge.md]"), "资料1应带溯源标签，实际: " + result);
        assertTrue(result.contains("[资料2·来源: credit-card.md]"), "资料2应带溯源标签，实际: " + result);
        assertTrue(result.contains("20 万元起购"), "应包含命中资料原文");
    }

    @Test
    void 检索请求_应把用户问题传给向量库() {
        when(mockStore.similaritySearch(any(SearchRequest.class))).thenReturn(HITS);

        tools.searchBankKnowledge("大额存单 起购");

        // 验证交互：用 ArgumentCaptor 捕获传给向量库的 SearchRequest（argThat 在重载方法间有类型歧义）
        org.mockito.ArgumentCaptor<SearchRequest> captor =
                org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        Mockito.verify(mockStore).similaritySearch(captor.capture());
        assertTrue("大额存单 起购".equals(captor.getValue().getQuery()),
                "SearchRequest 里应携带用户原始问题，实际: " + captor.getValue().getQuery());
    }

    @Test
    void 检索为空_应返回未找到提示() {
        when(mockStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String result = tools.searchBankKnowledge("完全无关的问题");
        assertTrue(result.contains("未找到"), "空结果应返回未找到提示，实际: " + result);
    }

    @Test
    void 检索返回null_应返回未找到提示() {
        // VectorStore 契约允许返回 null，必须防御
        when(mockStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        String result = tools.searchBankKnowledge("任何问题");
        assertTrue(result.contains("未找到"), "null 结果应被防御，实际: " + result);
    }
}
