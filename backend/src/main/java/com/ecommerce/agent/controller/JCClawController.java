package com.ecommerce.agent.controller;

import com.ecommerce.agent.agent.AgentRuntime;
import com.ecommerce.agent.model.AgentRequest;
import com.ecommerce.agent.model.AgentResponse;
import com.ecommerce.agent.service.JCClawService;
import com.ecommerce.agent.service.WeChatBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/jc-claw")
public class JCClawController {

    private final JCClawService jcClawService;
    private final WeChatBotService weChatBotService;
    private final AgentRuntime agentRuntime;

    public JCClawController(JCClawService jcClawService,
                            WeChatBotService weChatBotService,
                            AgentRuntime agentRuntime) {
        this.jcClawService = jcClawService;
        this.weChatBotService = weChatBotService;
        this.agentRuntime = agentRuntime;
    }

    /** 发起微信扫码绑定 */
    @PostMapping("/bind/start")
    public ResponseEntity<Map<String, Object>> startBind(@RequestParam(defaultValue = "false") boolean force) {
        try {
            Map<String, Object> result = jcClawService.startLogin(null, force);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("绑定失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** 查询绑定状态 */
    @GetMapping("/bind/status")
    public ResponseEntity<Map<String, Object>> bindStatus() {
        Map<String, Object> result = jcClawService.checkStatus(null);
        return ResponseEntity.ok(result);
    }

    /** 获取Bot运行状态 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> botStatus() {
        Map<String, Object> status = new LinkedHashMap<>(jcClawService.getConnectionStatus());
        List<Map<String, Object>> activeUsers = weChatBotService.getActiveUsers();
        status.put("activeUserCount", activeUsers.size());
        status.put("activeUsers", activeUsers);
        return ResponseEntity.ok(status);
    }

    /** 断开当前连接 */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestParam(defaultValue = "false") boolean clearSaved) {
        jcClawService.disconnect(clearSaved);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 取消绑定 */
    @PostMapping("/bind/cancel")
    public ResponseEntity<Map<String, Object>> cancelBind() {
        jcClawService.cancelLogin();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** JC claw 网页工作台对话：可直接调度微信控制、产品库和其他工具 */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = body.get("sessionId") != null ? String.valueOf(body.get("sessionId")) : null;

        AgentRequest request = AgentRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .taskType("jc-claw")
                .enableTools(true)
                .build();

        AgentResponse response = agentRuntime.execute(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", response.getSessionId());
        result.put("message", response.getMessage());
        result.put("status", response.getStatus());
        result.put("processingTimeMs", response.getProcessingTimeMs());
        result.put("modelUsed", "JC agent");

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        if (response.getToolCalls() != null) {
            for (AgentResponse.ToolCallRecord tc : response.getToolCalls()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolName", tc.getToolName());
                item.put("status", tc.getStatus());
                item.put("output", tc.getOutput());
                item.put("durationMs", tc.getDurationMs());
                toolCalls.add(item);
            }
        }
        result.put("toolCalls", toolCalls);
        return ResponseEntity.ok(result);
    }
}
