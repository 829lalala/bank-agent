package com.bank.agent.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 基础配置：装配向量数据库（含本地文件持久化）
 */
@Configuration // 配置类：里面的 @Bean 方法返回的对象交给 Spring 容器统一管理
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    /** 向量库落盘文件：摄取后立即保存、启动时恢复，避免每次重启都重新付费摄取 */
    public static final File DATA_FILE = new File("data/vector-store.json");

    /**
     * 向量存储 Bean。
     *
     * <p>SimpleVectorStore：Spring AI 提供的纯内存向量库，这里叠加了文件持久化：
     * 启动时 load() 恢复（读的是已算好的向量，零 API 成本），摄取后 save() 落盘。
     * 注意：@Bean 方法返回类型声明为具体类 SimpleVectorStore——因为摄取接口需要
     * 注入它特有的 save() 能力，而容器按"方法声明类型"注册 Bean 的类型元数据。
     *
     * @param embeddingModel 向量化模型（Spring AI 自动配置已按 yml 里的 embedding-3 创建好）
     */
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // 启动恢复：文件存在则直接加载历史向量
        if (DATA_FILE.exists()) {
            try {
                store.load(DATA_FILE);
                log.info("已从 {} 恢复向量库数据", DATA_FILE.getPath());
            } catch (Exception e) {
                // 文件损坏不应阻塞启动：记日志、降级为空库，用户可重新摄取
                log.warn("向量库恢复失败，将使用空库（可重新调用 POST /rag/load 摄取）", e);
            }
        }

        // JVM 关闭钩子：应用正常关闭（Ctrl+C、IDE 停止按钮）时自动保存。
        // 局限：kill -9 强杀/断电时不会执行，生产环境应改用"摄取成功后立即落盘"
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                DATA_FILE.getParentFile().mkdirs();
                store.save(DATA_FILE);
                log.info("向量库已保存到 {}", DATA_FILE.getPath());
            } catch (Exception e) {
                log.warn("向量库保存失败", e);
            }
        }));

        return store;
    }
}
