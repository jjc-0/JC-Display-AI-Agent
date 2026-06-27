package com.ecommerce.agent.tool;

import com.ecommerce.agent.service.WeChatBotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 微信控制工具 — 从 Agent 对话中枢控制微信
 *
 * 让 LLM 能:
 * - 主动向微信用户发消息
 * - 查询微信活跃用户列表
 * - 汇总微信对话
 *
 * 典型调用链:
 *   用户: "给微信上最活跃的用户发一条促销信息"
 *   → round 1: wechat_list_users → 拿到用户列表
 *   → round 2: database_query(query_type=messages, hours=24) → 了解最近对话背景
 *   → round 3: 生成促销文案
 *   → round 4: wechat_send_message(to_user_id=xxx, text=促销文案)
 */
@Slf4j
@Component
public class WeChatControlTool implements Tool {

    private final WeChatBotService weChatBot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeChatControlTool(@Lazy WeChatBotService weChatBot) {
        this.weChatBot = weChatBot;
    }

    @Override public String getName() { return "wechat_control"; }
    @Override public String getCategory() { return "BUSINESS"; }
    @Override public long getTimeoutMs() { return 30000; }

    @Override
    public boolean isEnabled() {
        return weChatBot.isRunning();
    }

    @Override
    public String getDescription() {
        return "JC claw 微信控制工具。主动向微信用户发送消息、查询活跃用户列表。" +
               "用户说“发给某个微信用户/客户/联系人”时先 list_users 或使用 to_user_keyword 匹配联系人，再 send_message。" +
               "文件传输助手是系统联系人，可直接使用 to_user_keyword=文件传输助手 或 to_user_id=filehelper。" +
               "注意：本工具不能读取完整微信通讯录，只能发送给文件传输助手或已和 JC claw 产生过会话的活跃用户。" +
               "典型用法: 发促销信息、售后跟进、广播通知、查看微信用户活跃度。" +
               "枚举 action: send_message / list_users";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> actionProp = new LinkedHashMap<>();
        actionProp.put("type", "string");
        actionProp.put("description", "操作类型: send_message(发消息) / list_users(查用户列表)");
        actionProp.put("enum", List.of("send_message", "list_users"));
        props.put("action", actionProp);

        Map<String, Object> userIdProp = new LinkedHashMap<>();
        userIdProp.put("type", "string");
        userIdProp.put("description", "目标用户ID (仅 send_message)");
        props.put("to_user_id", userIdProp);

        Map<String, Object> keywordProp = new LinkedHashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "目标用户关键词、昵称、备注或部分ID。用户没有提供精确 to_user_id 时使用。");
        props.put("to_user_keyword", keywordProp);

        Map<String, Object> textProp = new LinkedHashMap<>();
        textProp.put("type", "string");
        textProp.put("description", "要发送的消息文本 (仅 send_message)");
        props.put("text", textProp);

        schema.put("properties", props);
        schema.put("required", List.of("action"));
        return schema;
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String action = (String) params.getOrDefault("action", "list_users");

                switch (action) {
                    case "send_message":
                        return doSendMessage(params);
                    case "list_users":
                        return doListUsers();
                    default:
                        return "{\"error\": \"未知操作: " + action + "\"}";
                }
            } catch (Exception e) {
                log.error("微信工具执行失败", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
    }

    private String doSendMessage(Map<String, Object> params) {
        if (!weChatBot.isRunning()) {
            return "{\"error\": \"微信 Bot 未运行，请先在 JC-CLAW 中扫码绑定\"}";
        }

        String toUserId = (String) params.get("to_user_id");
        if (toUserId == null || toUserId.isBlank()) {
            String keyword = (String) params.get("to_user_keyword");
            if (keyword != null && !keyword.isBlank()) {
                Optional<Map<String, Object>> matched = weChatBot.findUser(keyword);
                if (matched.isPresent()) {
                    toUserId = String.valueOf(matched.get().get("user_id"));
                } else {
                    return usersRequired("未找到匹配的微信用户: " + keyword);
                }
            } else {
                return usersRequired("缺少 to_user_id 或 to_user_keyword 参数");
            }
        }

        String text = (String) params.get("text");
        if (text == null || text.isBlank()) {
            return "{\"error\": \"缺少 text 消息内容\"}";
        }

        WeChatBotService.SendResult sendResult = weChatBot.sendMessageDetailed(toUserId, text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", sendResult.success());
        result.put("to_user_id", toUserId);
        result.put("text", text.length() > 100 ? text.substring(0, 100) + "..." : text);
        if (sendResult.error() != null) {
            result.put("error", sendResult.error());
        }
        result.put("raw_response", sendResult.rawResponse());

        try {
            if (!sendResult.success()) {
                result.put("business_error", true);
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"success\":" + sendResult.success() + "}";
        }
    }

    private String doListUsers() {
        if (!weChatBot.isRunning()) {
            return "{\"error\": \"微信 Bot 未运行\", \"users\": []}";
        }

        List<Map<String, Object>> users = weChatBot.getActiveUsers();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("online", true);
        result.put("total", users.size());
        result.put("users", users);

        // 排序: 最活跃在前
        users.sort((a, b) -> Long.compare(
                (Long) b.getOrDefault("minutes_ago", 0L),
                (Long) a.getOrDefault("minutes_ago", 0L)));

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"total\":" + users.size() + "}";
        }
    }

    private String usersRequired(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        result.put("hint", "请先从 users 中选择目标用户，或提供更准确的微信昵称/备注/用户ID。");
        result.put("users", weChatBot.getActiveUsers());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }
}
