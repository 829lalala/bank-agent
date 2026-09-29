package com.bank.agent.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.bank.agent.controller.RagController;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索层评估：只评估"向量检索是否命中标准答案"，不调用 LLM。
 *
 * <p>这是 RAG 质量保障的第一道关：检索不准，生成层再好的提示词也救不回来。
 * 每次调整切分参数、topK、embedding 模型后跑一遍，命中率下降即说明改动有害。
 *
 * <p>需要真实 embedding API（摄取与查询都要向量化），请确保环境变量
 * ZHIPUAGENT_API_KEY 已设置。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagRetrievalEvaluationTest {

    @Autowired
    RagController ragController;

    @Autowired
    SimpleVectorStore vectorStore;

    private static final int TOP_K = 3;
    private static final double PASS_RATE = 0.8;

    @Test
    @Order(1)
    void 前置_摄取知识库() {
        // 复用生产摄取逻辑（含落盘）。若 data/vector-store.json 已恢复数据，
        // 本次会追加重复向量——对"包含关键事实"的断言无影响
        String result = ragController.load("bank-knowledge.md");
        System.out.println("[前置] " + result);
    }

    @Test
    @Order(2)
    void 检索命中率评估() {
        int total = 0, hit = 0;
        List<String> failures = new ArrayList<>();

        for (EvalDataset.EvalCase c : EvalDataset.withAnswer()) {
            List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(c.question()).topK(TOP_K).build());
            String joined = hits == null ? ""
                    : hits.stream().map(Document::getText).reduce("", (a, b) -> a + "\n" + b);

            // 命中标准：topK 资料的并集包含全部关键事实
            boolean ok = c.expectedFacts().stream().allMatch(joined::contains);
            total++;
            if (ok) {
                hit++;
            } else {
                failures.add(c.id() + " " + c.question() + " 期望: " + c.expectedFacts());
            }
            System.out.printf("[%s] %s %s%n", ok ? "PASS" : "MISS", c.id(), c.question());
        }

        double rate = total == 0 ? 0 : (double) hit / total;
        System.out.printf("%n========== 检索层评估报告 ==========%n");
        System.out.printf("样本: %d, 命中: %d, 未命中: %d, 命中率: %.1f%%%n", total, hit, failures.size(), rate * 100);
        failures.forEach(f -> System.out.println("未命中 -> " + f));
        System.out.println("====================================");

        assertTrue(rate >= PASS_RATE,
                "检索命中率应 ≥ " + (PASS_RATE * 100) + "%，实际 " + String.format("%.1f%%", rate * 100));
    }
}
