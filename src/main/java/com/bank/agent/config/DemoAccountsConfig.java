package com.bank.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** 演示账号只用于面试展示，密码由运行环境提供。 */
@Configuration
public class DemoAccountsConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${demo.security.admin-password:}") String adminPassword,
            @Value("${demo.security.user-password:}") String userPassword,
            PasswordEncoder passwordEncoder) {
        // 缺少密码时立即拒绝启动，避免生成公开或空密码账号。
        if (adminPassword.isBlank() || userPassword.isBlank()) {
            throw new IllegalStateException("请设置 DEMO_ADMIN_PASSWORD 和 DEMO_USER_PASSWORD");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password(passwordEncoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername("demo").password(passwordEncoder.encode(userPassword)).roles("USER").build());
    }
}
