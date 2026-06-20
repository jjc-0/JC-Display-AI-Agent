package com.ecommerce.agent.controller;

import com.ecommerce.agent.llm.PromptTemplateManager;
import com.ecommerce.agent.model.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/copywriting/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final PromptTemplateManager promptTemplateManager;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTemplates(@RequestParam(required = false) String category) {
        List<PromptTemplate> templates;
        if (category != null && !category.isEmpty()) {
            templates = promptTemplateManager.getTemplatesByCategory(category);
        } else {
            templates = promptTemplateManager.getAllTemplates();
        }
        return ResponseEntity.ok(templates.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTemplate(@PathVariable String id) {
        PromptTemplate t = promptTemplateManager.getTemplate(id);
        if (t == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toMap(t));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(@RequestBody Map<String, Object> body) {
        String id = (String) body.get("id");
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String category = (String) body.getOrDefault("category", "copywriting");
        String template = (String) body.get("template");
        String targetPlatform = (String) body.get("targetPlatform");
        Boolean active = Boolean.TRUE.equals(body.get("active"));

        @SuppressWarnings("unchecked")
        List<String> variables = (List<String>) body.get("variables");
        if (variables == null) variables = List.of();

        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        PromptTemplate pt = PromptTemplate.builder()
                .id(id)
                .name(name)
                .description(description)
                .category(category)
                .template(template)
                .targetPlatform(targetPlatform)
                .variables(variables)
                .active(active != null ? active : true)
                .build();

        promptTemplateManager.addTemplate(pt);
        return ResponseEntity.ok(toMap(pt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(@PathVariable String id,
                                                               @RequestBody Map<String, Object> body) {
        PromptTemplate existing = promptTemplateManager.getTemplate(id);
        if (existing == null) return ResponseEntity.notFound().build();

        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
        if (body.containsKey("category")) existing.setCategory((String) body.get("category"));
        if (body.containsKey("template")) existing.setTemplate((String) body.get("template"));
        if (body.containsKey("targetPlatform")) existing.setTargetPlatform((String) body.get("targetPlatform"));
        if (body.containsKey("active")) existing.setActive(Boolean.TRUE.equals(body.get("active")));

        @SuppressWarnings("unchecked")
        List<String> variables = (List<String>) body.get("variables");
        if (variables != null) existing.setVariables(variables);

        return ResponseEntity.ok(toMap(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable String id) {
        boolean removed = promptTemplateManager.removeTemplate(id);
        if (!removed) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleTemplate(@PathVariable String id) {
        PromptTemplate t = promptTemplateManager.getTemplate(id);
        if (t == null) return ResponseEntity.notFound().build();
        t.setActive(!t.isActive());
        return ResponseEntity.ok(toMap(t));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> previewTemplate(@PathVariable String id,
                                                                @RequestBody Map<String, String> variables) {
        try {
            String rendered = promptTemplateManager.renderTemplate(id, variables);
            return ResponseEntity.ok(Map.of("rendered", rendered));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(PromptTemplate t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("name", t.getName());
        map.put("description", t.getDescription());
        map.put("category", t.getCategory());
        map.put("template", t.getTemplate());
        map.put("variables", t.getVariables() != null ? t.getVariables() : List.of());
        map.put("targetPlatform", t.getTargetPlatform());
        map.put("active", t.isActive());
        map.put("hitRate", 0.85);
        map.put("version", "1.0");
        return map;
    }
}
