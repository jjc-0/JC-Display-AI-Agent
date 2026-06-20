package com.ecommerce.agent.tool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Tool {

    String getName();

    String getDescription();

    Map<String, Object> getParametersSchema();

    CompletableFuture<String> execute(Map<String, Object> params);
}
