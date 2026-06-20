package com.ecommerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.ecommerce.agent.repository")
public class ECommerceAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceAgentApplication.class, args);
    }
}
