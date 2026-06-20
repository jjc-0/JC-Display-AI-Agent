package com.ecommerce.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslateRequest {
    @NotBlank(message = "翻译文本不能为空")
    private String text;
    private String sourceLanguage;
    @NotBlank(message = "目标语言不能为空")
    private String targetLanguage;
    private String context;
    private boolean ecommerceLocalization;
}
