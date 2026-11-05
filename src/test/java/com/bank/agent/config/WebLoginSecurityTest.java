package com.bank.agent.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证网页业务入口确实要求登录。 */
@WebMvcTest(controllers = WebLoginSecurityTest.StubAgentEndpoint.class)
@Import({WebSecurityConfig.class, DemoAccountsConfig.class, WebLoginSecurityTest.StubAgentEndpoint.class})
@TestPropertySource(properties = {
        "demo.security.admin-password=admin-test-password",
        "demo.security.user-password=user-test-password"
})
class WebLoginSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 匿名请求进入登录页而普通账号可访问() throws Exception {
        mvc.perform(get("/agent/me")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/agent/me").with(user("demo").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void 只有管理员可以摄取知识() throws Exception {
        mvc.perform(post("/rag/load").with(user("demo").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/rag/load").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @RestController
    static class StubAgentEndpoint {
        @GetMapping("/agent/me")
        String me() {
            return "demo";
        }

        @PostMapping("/rag/load")
        String load() {
            return "摄取完成";
        }
    }
}
