package com.ecommerce.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyWritingRequest {
    @NotBlank(message = "产品名称不能为空")
    private String productName;
    private String sellingPoints;
    private String targetCountry;
    private String platform;
    private String style;
    private String language;
    private Map<String, Object> extraParams;
}
