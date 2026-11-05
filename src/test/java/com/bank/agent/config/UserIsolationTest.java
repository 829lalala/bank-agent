package com.bank.agent.config;

import com.bank.agent.controller.AgentController;
import com.bank.agent.memory.LongTermMemoryService;
import com.bank.agent.service.ReflectionService;
import com.bank.agent.tool.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

/** 使用真实控制器和安全链，模型与数据库替身只用于隔离外部依赖。 */
@WebMvcTest(controllers = AgentController.class)
@Import({WebSecurityConfig.class, DemoAccountsConfig.class})
@TestPropertySource(properties = {
        "demo.security.admin-password=test-admin", "demo.security.user-password=test-demo"})
class UserIsolationTest {
    @Autowired MockMvc mvc;
    @MockitoBean BankTools bankTools;
    @MockitoBean KnowledgeTools knowledgeTools;
    @MockitoBean ChatMemory chatMemory;
    @MockitoBean ChatClient.Builder builder;
    @MockitoBean ReflectionService reflection;
    @MockitoBean LongTermMemoryService memory;

    @Test
    void 修改用户参数不能读取他人画像() throws Exception {
        when(memory.recall("demo")).thenReturn(List.of("普通用户画像"));
        when(memory.recall("admin")).thenReturn(List.of("管理员画像"));
        mvc.perform(get("/agent/memory").param("userId", "admin").with(user("demo")))
                .andExpect(status().isOk()).andExpect(content().json("[\"普通用户画像\"]"));
        mvc.perform(get("/agent/memory").param("userId", "demo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().json("[\"管理员画像\"]"));
        verify(memory).recall("demo");
        verify(memory).recall("admin");
    }

    @Test
    void 反思模式忽略伪造身份() throws Exception {
        mvc.perform(get("/agent/chat-reflect").param("q", "我的余额")
                .param("userId", "admin").with(user("demo"))).andExpect(status().isOk());
        verify(reflection).reflect("我的余额", "demo");
        verifyNoMoreInteractions(reflection);
    }

    @Test
    void 非法会话编号在调用模型前拒绝() throws Exception {
        mvc.perform(get("/agent/chat").param("q", "你好")
                .param("conversationId", "admin:伪造编号").with(user("demo")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(memory, reflection);
    }

    @Test
    void 匿名请求不能访问任何聊天记忆入口() throws Exception {
        for (String path : List.of("/agent/chat", "/agent/chat-reflect", "/agent/memory", "/agent/me")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        }
        verifyNoInteractions(memory, reflection);
    }

    @Test
    void 两账号相同会话编号使用不同记忆键及余额工具() {
        ChatClient client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        var stream = mock(ChatClient.StreamResponseSpec.class);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.stream()).thenReturn(stream);
        when(stream.content()).thenReturn(reactor.core.publisher.Flux.just("测试回答"));
        when(memory.buildContext(anyString())).thenReturn("");
        var controller = new AgentController(bankTools, knowledgeTools, chatMemory, builder, reflection, memory);
        String id = "16fb2f00-d1c1-4444-8111-111111111111";
        controller.chat("我的余额", id, () -> "admin");
        controller.chat("我的余额", id, () -> "demo");
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.function.Consumer<ChatClient.AdvisorSpec>> capture =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(request, times(2)).advisors(capture.capture());
        var adminAdvisor = mock(ChatClient.AdvisorSpec.class);
        var demoAdvisor = mock(ChatClient.AdvisorSpec.class);
        capture.getAllValues().get(0).accept(adminAdvisor);
        capture.getAllValues().get(1).accept(demoAdvisor);
        verify(adminAdvisor).param(ChatMemory.CONVERSATION_ID, "admin:" + id);
        verify(demoAdvisor).param(ChatMemory.CONVERSATION_ID, "demo:" + id);
        var tools = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(request, times(2)).tools(tools.capture());
        org.junit.jupiter.api.Assertions.assertTrue(((AccountBalanceTool) tools.getAllValues().get(0)[2])
                .getMyAccountBalance().contains("8888.50"));
        org.junit.jupiter.api.Assertions.assertTrue(((AccountBalanceTool) tools.getAllValues().get(1)[2])
                .getMyAccountBalance().contains("1234.56"));
        verify(memory).buildContext("admin");
        verify(memory).buildContext("demo");
    }
}
