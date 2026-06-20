/**
 * JC-Claw WeChat Bridge
 * 完全按照 @tencent-weixin/openclaw-weixin v2.4.4 源码实现，
 * 直接调 iLink API 收发微信消息，经本地 Java Agent 回复。
 *
 * 用法: node scripts/wechat-bridge.mjs
 */

import { readFileSync, existsSync } from "node:fs";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";

// ── 配置 ──────────────────────────────────────────────────
const ILINK_BASE = "https://ilinkai.weixin.qq.com";
const AGENT_API = "http://localhost:8088/api/agent/chat";
const STATE_DIR = path.join(os.homedir(), ".openclaw", "openclaw-weixin");
const POLL_TIMEOUT = 35_000;

// ── plugin package.json 常量 ──────────────────────────────
const CHANNEL_VERSION = "2.4.4";
const ILINK_APP_ID = "bot";
// buildClientVersion("2.4.4") = (2<<16)|(4<<8)|4 = 132100
const ILINK_APP_CLIENT_VERSION = 132100;

// ── 工具函数 ──────────────────────────────────────────────
function randomWechatUin() {
  const buf = crypto.randomBytes(4);
  const v = buf.readUInt32BE(0);
  return Buffer.from(String(v), "utf-8").toString("base64");
}

function randomHex(len) {
  return Array.from({ length: len }, () =>
    Math.floor(Math.random() * 16).toString(16)
  ).join("");
}

function generateClientId() {
  return "jc-claw-" + randomHex(12);
}

function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function baseInfo() {
  return { channel_version: CHANNEL_VERSION, bot_agent: "JC-ClawBot" };
}

function buildHeaders(token) {
  return {
    "Content-Type": "application/json",
    AuthorizationType: "ilink_bot_token",
    "X-WECHAT-UIN": randomWechatUin(),
    "iLink-App-Id": ILINK_APP_ID,
    "iLink-App-ClientVersion": String(ILINK_APP_CLIENT_VERSION),
    Authorization: `Bearer ${token}`,
  };
}

async function apiPost(endpoint, body, token, timeoutMs) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs ?? 15_000);
  try {
    const res = await fetch(`${ILINK_BASE}/${endpoint}`, {
      method: "POST",
      headers: buildHeaders(token),
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await res.text();
    if (!res.ok)
      console.warn(`  [HTTP ${res.status}] ${endpoint}: ${text.substring(0, 200)}`);
    return JSON.parse(text);
  } catch (err) {
    if (err.name === "AbortError") {
      console.warn(`  [TIMEOUT] ${endpoint}`);
      return { ret: 0, msgs: [] };
    }
    throw err;
  } finally {
    clearTimeout(t);
  }
}

// ── 读取凭证 ──────────────────────────────────────────────
function loadAccount() {
  const indexFile = path.join(STATE_DIR, "accounts.json");
  if (!existsSync(indexFile)) {
    console.error("未找到 accounts.json，请先在页面扫码绑定");
    process.exit(1);
  }
  const ids = JSON.parse(readFileSync(indexFile, "utf-8"));
  if (!ids.length) {
    console.error("未注册任何账号");
    process.exit(1);
  }
  const id = ids[ids.length - 1];
  const acctFile = path.join(STATE_DIR, "accounts", id + ".json");
  const acct = JSON.parse(readFileSync(acctFile, "utf-8"));
  return { accountId: id, token: acct.token, userId: acct.userId };
}

// ── notifyStart ────────────────────────────────────────────
async function notifyStart(token) {
  console.log("notifyStart...");
  const resp = await apiPost(
    "ilink/bot/msg/notifystart",
    { base_info: baseInfo() },
    token,
    10_000,
  );
  console.log(`  ret=${resp.ret} errmsg=${resp.errmsg ?? ""}`);
}

// ── getUpdates 长轮询 ─────────────────────────────────────
async function getUpdates(token, getUpdatesBuf) {
  const resp = await apiPost(
    "ilink/bot/getupdates",
    { get_updates_buf: getUpdatesBuf, base_info: baseInfo() },
    token,
    POLL_TIMEOUT,
  );
  const errcode = resp.errcode ?? 0;
  if (errcode !== 0) {
    console.warn(`  getupdates 错误: errcode=${errcode} errmsg=${resp.errmsg}`);
    return { nextBuf: getUpdatesBuf, messages: [], error: resp };
  }
  const msgs = resp.msgs ?? [];
  const next = resp.get_updates_buf || getUpdatesBuf;
  if (msgs.length > 0) {
    console.log(`  getupdates: ${msgs.length} 条消息, nextBuf=${next.substring(0, 10)}...`);
  }
  return { nextBuf: next, messages: msgs };
}

// ── sendMessage ────────────────────────────────────────────
async function sendMessage(token, toUserId, text, contextToken) {
  const clientId = generateClientId();
  const req = {
    msg: {
      from_user_id: "",
      to_user_id: toUserId,
      client_id: clientId,
      message_type: 2, // BOT
      message_state: 2, // FINISH
      item_list: [{ type: 1, text_item: { text } }],
      context_token: contextToken || undefined,
      run_id: uuid(),
    },
    base_info: baseInfo(),
  };
  await apiPost("ilink/bot/sendmessage", req, token, 15_000);
  return clientId;
}

// ── typing_ticket 缓存（每用户）────────────────────────────
const typingTicketCache = new Map();

async function fetchTypingTicket(token, userId) {
  if (typingTicketCache.has(userId)) return typingTicketCache.get(userId);
  try {
    const resp = await apiPost(
      "ilink/bot/getconfig",
      { ilink_user_id: userId, base_info: baseInfo() },
      token,
      10_000,
    );
    if (resp.typing_ticket) {
      typingTicketCache.set(userId, resp.typing_ticket);
      return resp.typing_ticket;
    }
  } catch (e) {
    console.warn(`  getConfig failed: ${e.message}`);
  }
  return null;
}

async function sendTyping(token, userId, typingTicket, status) {
  if (!typingTicket) return;
  try {
    await apiPost(
      "ilink/bot/sendtyping",
      { ilink_user_id: userId, typing_ticket: typingTicket, status, base_info: baseInfo() },
      token,
      10_000,
    );
  } catch (e) {
    // typing is best-effort, ignore failures
  }
}

// ── 调用后端 Agent ────────────────────────────────────────
async function callAgent(message, fromUserId) {
  const res = await fetch(AGENT_API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      message,
      taskType: "chat",
      enableTools: true,
      sessionId: `wechat-${fromUserId}`,
    }),
  });
  const data = await res.json();
  return data.message || data.result || "智能体暂无响应";
}

// ── 提取消息文本 ──────────────────────────────────────────
function extractText(msg) {
  const items = msg.item_list || [];
  let text = "";
  for (const item of items) {
    if (item.type === 1 && item.text_item?.text) {
      text += item.text_item.text;
    }
  }
  return text;
}

// ── 处理单条消息 ──────────────────────────────────────────
async function processMessage(account, msg) {
  const fromUserId = msg.from_user_id;
  if (!fromUserId) return;
  const text = extractText(msg);
  if (!text) return;

  console.log(`\n[微信] ${fromUserId}: ${text}`);

  // 获取 typing_ticket 并开始显示"正在输入..."
  const typingTicket = await fetchTypingTicket(account.token, fromUserId);
  await sendTyping(account.token, fromUserId, typingTicket, 1); // 1 = TYPING

  try {
    const reply = await callAgent(text, fromUserId);
    console.log(`[回复] ${reply.substring(0, 80)}...`);
    // 发送回复前先取消 typing
    await sendTyping(account.token, fromUserId, typingTicket, 2); // 2 = CANCEL
    await sendMessage(account.token, fromUserId, reply, msg.context_token);
    console.log("  ✓ 已发送");
  } catch (e) {
    await sendTyping(account.token, fromUserId, typingTicket, 2);
    console.error(`  发送失败: ${e.message}`);
  }
}

// ── 主循环 ────────────────────────────────────────────────
async function main() {
  const account = loadAccount();
  console.log(`账号: ${account.accountId}`);
  console.log("JC-Claw WeChat Bridge 启动\n");

  // 1. 通知服务器
  await notifyStart(account.token);

  // 2. 长轮询
  let buf = "";
  while (true) {
    try {
      const { nextBuf, messages, error } = await getUpdates(account.token, buf);
      buf = nextBuf;

      if (error) {
        // errcode=-14 表示 session 过期, 退避1小时
        if (error.errcode === -14) {
          console.error("Session 过期，需要重新扫码!");
          await new Promise((r) => setTimeout(r, 3600_000));
        }
        await new Promise((r) => setTimeout(r, 2000));
        continue;
      }

      for (const msg of messages) {
        await processMessage(account, msg);
      }
    } catch (e) {
      console.warn(`轮询异常: ${e.message}, 3秒后重试`);
      await new Promise((r) => setTimeout(r, 3000));
    }
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
