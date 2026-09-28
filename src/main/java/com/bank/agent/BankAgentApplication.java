package com.bank.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 银行智能业务助手 Agent —— Spring Boot 启动类
 */
@SpringBootApplication // 组合注解：开启自动配置 + 扫描本包及子包的组件 + 标识这是启动类
public class BankAgentApplication {

    public static void main(String[] args) {
        // SpringApplication.run：启动内嵌 Tomcat、初始化 Spring 容器、装配所有 Bean
        SpringApplication.run(BankAgentApplication.class, args);
    }
}
