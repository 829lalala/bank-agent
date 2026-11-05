package com.bank.agent.evaluation;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.ai.chat.metadata.Usage;

/** 保存逐题证据；用量仅来自最终响应，不能冒充工具循环全链路账单。 */
class EvaluationReport {
    private final String model, dataset, temperature;
    private final List<Map<String, Object>> rows = new ArrayList<>();

    EvaluationReport(String model, String dataset, String temperature) {
        this.model = model;
        this.dataset = dataset;
        this.temperature = temperature;
    }

    void add(int round, EvalDataset.EvalCase c, String answer, boolean passed,
             long elapsedMs, Usage usage, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("round", round);
        row.put("id", c.id());
        row.put("category", c.category());
        row.put("question", c.question());
        row.put("expectedFacts", c.expectedFacts());
        row.put("answer", answer == null ? "" : answer);
        row.put("passed", passed && error.isEmpty());
        row.put("elapsedMs", elapsedMs);
        row.put("errorType", error);
        row.put("finalResponseInputTokens", usage == null ? null : usage.getPromptTokens());
        row.put("finalResponseOutputTokens", usage == null ? null : usage.getCompletionTokens());
        rows.add(row);
    }

    void write() {
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("createdAt", Instant.now().toString());
            report.put("model", model);
            report.put("dataset", dataset);
            report.put("temperature", temperature);
            report.put("scope", "无登录、记忆和流式输出的共享提示词与工具生成评估；耗时包含检索和工具循环");
            report.put("usageScope", "最终响应用量，可能不包含中间工具调用轮次、向量化和重试；费用不是完整账单");
            String inputPrice = System.getProperty("eval.inputPricePerMillion");
            String outputPrice = System.getProperty("eval.outputPricePerMillion");
            if (inputPrice != null && outputPrice != null) {
                double inputRate = Double.parseDouble(inputPrice);
                double outputRate = Double.parseDouble(outputPrice);
                if (!Double.isFinite(inputRate) || !Double.isFinite(outputRate)
                        || inputRate < 0 || outputRate < 0) {
                    throw new IllegalArgumentException("单价必须是非负有限数字");
                }
                boolean complete = rows.stream().allMatch(r -> r.get("finalResponseInputTokens") != null
                        && r.get("finalResponseOutputTokens") != null && "".equals(r.get("errorType")));
                report.put("priceSource", System.getProperty("eval.priceSource", "未提供，请勿引用费用"));
                report.put("currency", System.getProperty("eval.currency", "未提供"));
                report.put("inputPricePerMillion", inputRate);
                report.put("outputPricePerMillion", outputRate);
                report.put("partialUsageEstimatedCost", complete ? rows.stream().mapToDouble(r ->
                        (((Number) r.get("finalResponseInputTokens")).doubleValue() * inputRate
                        + ((Number) r.get("finalResponseOutputTokens")).doubleValue() * outputRate)
                        / 1_000_000).sum() : null);
            }
            Map<String, Object> categories = new LinkedHashMap<>();
            for (String category : List.of("factual", "comparison", "no_answer")) {
                var group = rows.stream().filter(r -> category.equals(r.get("category"))).toList();
                categories.put(category, Map.of("total", group.size(), "passed",
                        group.stream().filter(r -> Boolean.TRUE.equals(r.get("passed"))).count()));
            }
            report.put("categories", categories);
            long[] times = rows.stream().mapToLong(r -> (Long) r.get("elapsedMs")).sorted().toArray();
            report.put("averageMs", Arrays.stream(times).average().orElse(0));
            report.put("p50Ms", times[(int) Math.ceil(times.length * .50) - 1]);
            report.put("p95Ms", times[(int) Math.ceil(times.length * .95) - 1]);
            report.put("rows", rows);
            Path directory = Path.of("target", "evaluation-reports");
            Files.createDirectories(directory);
            Path output = directory.resolve(model.replaceAll("[^a-zA-Z0-9._-]", "_")
                    + "-" + dataset + "-" + UUID.randomUUID() + ".json");
            Files.writeString(output, new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
            System.out.println("逐题评估报告：" + output.toAbsolutePath());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法写入评估报告", e);
        }
    }
}
