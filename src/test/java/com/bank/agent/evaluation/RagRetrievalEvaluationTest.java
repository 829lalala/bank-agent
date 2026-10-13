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
import org.springframework.ai.vectorstore.VectorStore;

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
    VectorStore vectorStore;

    private static final int TOP_K = 3;
    private static final double PASS_RATE = 0.8;

    @Test
    @Order(1)
    void 前置_摄取知识库() {
        // 幂等防护：向量库已检索得到知识内容说明此前已摄取过，重复摄取会追加
        // 重复文档——重复主题会占满 topK 窗口，把对比型问题的另一主题挤出
        // （曾导致 E13 误报 MISS，评估结论失真）。探测一次检索即可判断
        List<Document> probe = vectorStore.similaritySearch(
                SearchRequest.builder().query("大额存单起购金额").topK(1).build());
        if (probe != null && !probe.isEmpty()) {
            System.out.println("[前置] 向量库已有知识数据，跳过重复摄取");
            return;
        }
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
                // MISS 时打印召回内容：评估失败必须可解释，否则只能靠猜
                System.out.printf("    [诊断] %s 的 top%d 召回：%n", c.id(), TOP_K);
                if (hits != null) {
                    for (Document d : hits) {
                        String text = d.getText().replaceAll("\\s+", " ");
                        String preview = text.length() > 80 ? text.substring(0, 80) + "..." : text;
                        System.out.printf("    - %s%n", preview);
                    }
                }
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
