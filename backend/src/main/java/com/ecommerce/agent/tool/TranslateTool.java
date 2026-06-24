package com.ecommerce.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class TranslateTool implements Tool {

    private static final Map<String, String> DICTIONARY_TEMPLATES = new LinkedHashMap<>();

    static {
        DICTIONARY_TEMPLATES.put("en", "English");
        DICTIONARY_TEMPLATES.put("zh", "Chinese (Simplified)");
        DICTIONARY_TEMPLATES.put("ja", "Japanese");
        DICTIONARY_TEMPLATES.put("ko", "Korean");
        DICTIONARY_TEMPLATES.put("fr", "French");
        DICTIONARY_TEMPLATES.put("de", "German");
        DICTIONARY_TEMPLATES.put("es", "Spanish");
        DICTIONARY_TEMPLATES.put("pt", "Portuguese");
        DICTIONARY_TEMPLATES.put("it", "Italian");
        DICTIONARY_TEMPLATES.put("ar", "Arabic");
        DICTIONARY_TEMPLATES.put("th", "Thai");
        DICTIONARY_TEMPLATES.put("vi", "Vietnamese");
    }

    @Override
    public String getName() {
        return "translate";
    }

    @Override
    public String getCategory() { return "GENERATION"; }

    @Override
    public String getDescription() {
        return "多语言翻译工具，支持中英日韩法等语言互译。特别优化电商场景（商品标题、描述本地化）。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> textProp = new LinkedHashMap<>();
        textProp.put("type", "string");
        textProp.put("description", "要翻译的文本");
        props.put("text", textProp);

        Map<String, Object> fromProp = new LinkedHashMap<>();
        fromProp.put("type", "string");
        fromProp.put("description", "源语言代码，如zh、en、ja");
        props.put("from", fromProp);

        Map<String, Object> toProp = new LinkedHashMap<>();
        toProp.put("type", "string");
        toProp.put("description", "目标语言代码，如en、ja、ko");
        props.put("to", toProp);

        schema.put("properties", props);
        schema.put("required", List.of("text", "to"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String text = (String) params.getOrDefault("text", "");
            String from = (String) params.getOrDefault("from", "zh");
            String to = (String) params.getOrDefault("to", "en");

            String fromLang = DICTIONARY_TEMPLATES.getOrDefault(from, "Unknown");
            String toLang = DICTIONARY_TEMPLATES.getOrDefault(to, "Unknown");

            return String.format("""
                    🌐 翻译请求已接收:
                    - 原文: %s
                    - 源语言: %s
                    - 目标语言: %s
                    
                    💡 提示: 完整的AI翻译功能需要结合LLM来实现电商本地化翻译。
                    请使用 /api/translate 接口进行AI驱动的电商本地化翻译。
                    """, text, fromLang, toLang);
        });
    }
}
