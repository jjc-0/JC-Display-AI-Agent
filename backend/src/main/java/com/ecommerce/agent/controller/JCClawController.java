package com.ecommerce.agent.controller;

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

    public JCClawController(JCClawService jcClawService, WeChatBotService weChatBotService) {
        this.jcClawService = jcClawService;
        this.weChatBotService = weChatBotService;
    }

    /** 发起微信扫码绑定 */
    @PostMapping("/bind/start")
    public ResponseEntity<Map<String, Object>> startBind() {
        try {
            Map<String, Object> result = jcClawService.startLogin(null);
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", weChatBotService.isRunning());
        result.put("connected", weChatBotService.isRunning());
        return ResponseEntity.ok(result);
    }

    /** 取消绑定 */
    @PostMapping("/bind/cancel")
    public ResponseEntity<Map<String, Object>> cancelBind() {
        jcClawService.cancelLogin();
        return ResponseEntity.ok(Map.of("success", true));
    }
}
