package com.bank.agent.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * 对话测试接口：验证与智谱大模型的连通性
 */
@RestController // 标识为控制器，方法返回值直接写入 HTTP 响应体（而非页面名）
public class ChatController {

    // Spring AI 的对话客户端，所有与模型的交互都通过它
    private final ChatClient chatClient;

    /**
     * 构造器注入：Spring AI 自动配置已提供 ChatClient.Builder，
     * 它内部封装了 application.yml 里配好的地址、密钥、模型名
     */
    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 用法示例：GET http://localhost:8080/chat?q=你好
     *
     * @param q 用户的问题
     * @return 模型的回答文本
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String q) {
        // prompt() 构建请求 → user() 设置用户消息 → call() 同步调用 → content() 取纯文本结果
        return chatClient.prompt()
                .user(q)
                .call()
                .content();
    }

    /**
     * 流式对话接口（打字机效果）。
     * 用法示例：GET http://localhost:8080/chat/stream?q=你好
     *
     * <p>Content-Type 为 text/event-stream（SSE 协议），Spring MVC 检测到返回值
     * 是 Flux 后会逐条推送给客户端，而不是攒齐再返回。
     *
     * @param q 用户的问题
     * @return Flux：异步的"文本片段序列"，每生成一小段就推送一次
     */
    @GetMapping("/chat/stream")
    public ResponseEntity<Flux<String>> stream(@RequestParam String q) {
        // stream() 代替 call()：模型边生成边返回；content() 把每个响应块映射为纯文本片段
        Flux<String> body = chatClient.prompt()
                .user(q)
                .stream()
                .content();

        // 显式声明 charset=UTF-8：防止浏览器直接打开时按默认编码误读中文
        // （SSE 正确消费方是网页 fetch，charset 对其无影响，仅影响浏览器直接渲染）
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .body(body);
    }
}
