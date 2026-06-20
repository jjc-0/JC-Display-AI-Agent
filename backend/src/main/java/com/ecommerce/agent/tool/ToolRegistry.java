package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
        log.info("工具注册中心初始化完成，已注册 {} 个工具: {}",
                tools.size(), String.join(", ", tools.keySet()));
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public List<Tool> getAllTools() {
        return List.copyOf(tools.keySet()).stream()
                .map(tools::get)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getToolDefinitionsForLLM() {
        return tools.values().stream()
                .map(tool -> {
                    Map<String, Object> def = new LinkedHashMap<>();
                    def.put("type", "function");
                    Map<String, Object> func = new LinkedHashMap<>();
                    func.put("name", tool.getName());
                    func.put("description", tool.getDescription());
                    func.put("parameters", tool.getParametersSchema());
                    def.put("function", func);
                    return def;
                })
                .collect(Collectors.toList());
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
