package com.ecommerce.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {
    private String id;
    private String name;
    private String description;
    private String category;
    private String template;
    private List<String> variables;
    private Map<String, String> metadata;
    private String targetCountry;
    private String targetPlatform;
    private boolean active;
}
