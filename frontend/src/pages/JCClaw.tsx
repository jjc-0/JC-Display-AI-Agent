import { useState, useRef, useEffect } from "react"
import { QRCodeSVG } from "qrcode.react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  MessageCircle,
  Bot,
  QrCode,
  Users,
  MessageSquareOff,
  Check,
  RefreshCw,
  Sparkles,
  Zap,
  ShieldAlert,
  Link2Off,
  Send,
  Loader2,
} from "lucide-react"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

export default function JCClaw() {
  const [qrUrl, setQrUrl] = useState<string | null>(null)
  const [isBinding, setIsBinding] = useState(false)
  const [isBound, setIsBound] = useState(false)
  const [botRunning, setBotRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<any>(null)
  const [conflict, setConflict] = useState(false)
  const [chatInput, setChatInput] = useState("")
  const [chatSessionId, setChatSessionId] = useState<string | null>(null)
  const [chatLoading, setChatLoading] = useState(false)
  const [chatMessages, setChatMessages] = useState<Array<{
    id: string
    role: "user" | "assistant"
    content: string
    toolCalls?: Array<{ toolName: string; status: string; durationMs?: number }>
  }>>([
    {
      id: "welcome",
      role: "assistant",
      content: "这里可以直接指挥 JC claw。绑定微信后，你可以说：给最近联系的客户发一条跟进消息，或者给某个微信用户发送产品链接。",
    },
  ])
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const chatScrollRef = useRef<HTMLDivElement>(null)

  // 页面加载时检测Bot运行状态
  useEffect(() => {
    refreshStatus()
    return () => { if (pollingRef.current) clearInterval(pollingRef.current) }
  }, [])

  useEffect(() => {
    chatScrollRef.current?.scrollTo({ top: chatScrollRef.current.scrollHeight, behavior: "smooth" })
  }, [chatMessages, chatLoading])

  const refreshStatus = async () => {
    try {
      const res = await api.get("/jc-claw/status")
      setStatus(res.data)
      setIsBound(Boolean(res.data?.running || res.data?.connected))
      setBotRunning(Boolean(res.data?.running || res.data?.connected))
    } catch {
      setStatus(null)
    }
  }

  const startBind = async (force = false) => {
    setError(null)
    setConflict(false)
    setIsBinding(true)
    try {
      const res = await api.post(`/jc-claw/bind/start?force=${force}`)
      if (res.data?.conflict) {
        setConflict(true)
        setStatus(res.data)
        setError(res.data?.message || "当前已有活动连接，请确认是否接管。")
        setIsBinding(false)
        return
      }
      if (res.data?.success && res.data.qrUrl) {
        setQrUrl(res.data.qrUrl)
        startPolling()
      } else {
        setError(res.data?.error || "获取二维码失败")
        setIsBinding(false)
      }
    } catch {
      setError("后端服务未启动")
      setIsBinding(false)
    }
  }

  const startPolling = () => {
    if (pollingRef.current) clearInterval(pollingRef.current)
    pollingRef.current = setInterval(async () => {
      try {
        const res = await api.get("/jc-claw/bind/status")
        if (res.data?.connected) {
          clearInterval(pollingRef.current!)
          pollingRef.current = null
          setQrUrl(null)
          setIsBinding(false)
          setIsBound(true)
          setBotRunning(true)
          setStatus(res.data)
        }
      } catch { /* ignore */ }
    }, 2000)
  }

  const cancelBind = () => {
    if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null }
    api.post("/jc-claw/bind/cancel").catch(() => {})
    setIsBinding(false)
    setQrUrl(null)
  }

  const unbind = () => {
    api.post("/jc-claw/disconnect?clearSaved=true").finally(() => {
      setIsBound(false)
      setBotRunning(false)
      setConflict(false)
      refreshStatus()
    })
  }

  const sendClawMessage = async (preset?: string) => {
    const text = (preset || chatInput).trim()
    if (!text || chatLoading) return

    setChatMessages((prev) => [...prev, { id: Date.now().toString(), role: "user", content: text }])
    setChatInput("")
    setChatLoading(true)
    try {
      const { data } = await api.post("/jc-claw/chat", {
        message: text,
        sessionId: chatSessionId,
      })
      setChatSessionId(data.sessionId)
      setChatMessages((prev) => [...prev, {
        id: `${Date.now()}-assistant`,
        role: "assistant",
        content: data.message || "JC claw 已处理。",
        toolCalls: Array.isArray(data.toolCalls) ? data.toolCalls : [],
      }])
      refreshStatus()
    } catch (e: any) {
      setChatMessages((prev) => [...prev, {
        id: `${Date.now()}-error`,
        role: "assistant",
        content: e.response?.data?.message || "JC claw 指令执行失败，请检查后端或微信连接状态。",
      }])
    } finally {
      setChatLoading(false)
    }
  }

  const toolLabel = (name: string) => {
    if (name === "wechat_control") return "JC claw 微信"
    if (name === "product_catalog_search") return "产品库"
    if (name === "knowledge_base_status") return "知识库状态"
    return name
  }

  const activeUserCount = Number(status?.activeUserCount ?? 0)

  return (
    <div className="space-y-5 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">JC claw</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">连接每一个触点，让 AI 无界触达</h1>
            <p className="mt-2 text-sm text-muted-foreground max-w-2xl">
            扫码绑定微信，你的智能体即刻接入社交渠道。文本对话、图片生成，在微信里直接使用网站的 AI 能力。
            </p>
          </div>
          {botRunning && (
            <Badge className="text-[10px] gap-1 bg-[#E9F7F5] text-[#087C78] border-[#BFE2DA]">
              <Zap size={10} />在线
            </Badge>
          )}
        </div>
      </div>

      {/* 微信绑定卡片 */}
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
      <Card className="w-full">
        <CardContent className="p-4 space-y-3">
          {/* 头部 */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="ai-orbit w-8 h-8 rounded-[8px] bg-gradient-to-br from-[#2D9D72] to-[#0B918C] flex items-center justify-center text-white shadow-[0_18px_36px_-26px_rgba(23,33,31,0.55)]">
                <MessageCircle size={16} />
              </div>
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="text-[13px] font-bold">微信 JC claw</span>
                  <Badge variant={isBound ? "success" : isBinding ? "warning" : "secondary"} className="text-[10px] px-1.5 py-0">
                    {isBound ? <><Check size={9} />已关联</> : isBinding ? "绑定中" : "未关联"}
                  </Badge>
                </div>
                <p className="text-[11px] text-muted-foreground">iLink 微信扫码绑定</p>
              </div>
            </div>
          </div>

          {/* 用户数 / 群聊 */}
          <div className="flex gap-2">
            <div className="trade-signal-card flex-1 flex items-center gap-2 p-2.5">
              <div className="w-7 h-7 rounded-[8px] bg-gradient-to-br from-[#0A8BC4] to-[#0B918C] flex items-center justify-center text-white shadow-sm">
                <Users size={14} />
              </div>
              <div>
                <p className="text-[10px] text-muted-foreground leading-none">用户</p>
                <p className="text-[22px] font-bold leading-none mt-0.5">{activeUserCount}</p>
              </div>
            </div>
            <div className="trade-signal-card flex-1 flex items-center gap-2 p-2.5">
              <MessageSquareOff size={14} className="text-slate-400" />
              <p className="text-[11px] text-muted-foreground font-medium">暂不支持群聊</p>
            </div>
          </div>

          {/* 智能体 */}
          <div className={cn("w-full flex items-center gap-2.5 p-2.5 rounded-[8px] border",
            botRunning ? "border-[#BFE2DA] bg-[#E9F7F5]" : "border-primary bg-primary/5 ring-1 ring-primary/15")}>
            <div className="w-7 h-7 rounded-[8px] bg-gradient-to-br from-[#2D9D72] to-[#0B918C] flex items-center justify-center text-white shadow-sm">
              <Bot size={14} />
            </div>
            <span className="text-[12px] font-semibold flex-1">
              {botRunning ? "JC claw 在线" : "JC claw"}
            </span>
            {botRunning ? <Zap size={12} className="text-green-500" /> : <Check size={12} className="text-primary" />}
          </div>

          {/* 错误提示 */}
          {error && (
            <div className="p-2.5 rounded-[8px] bg-red-50 border border-red-200 text-[11px] text-red-600">{error}</div>
          )}

          {conflict && (
            <div className="space-y-2 rounded-[10px] border border-amber-200 bg-amber-50 p-3">
              <div className="flex items-center gap-2 text-xs font-bold text-amber-700">
                <ShieldAlert size={14} /> 检测到已有活动连接
              </div>
              <p className="text-[11px] leading-relaxed text-amber-700">
                为避免多台用户扫码互相覆盖，系统默认阻止新二维码。确认接管后会断开旧连接并重新绑定。
              </p>
              <Button size="sm" className="h-8 w-full text-[11px]" onClick={() => startBind(true)}>
                确认接管并重新扫码
              </Button>
            </div>
          )}

          {/* 二维码区域 */}
          {isBinding && qrUrl && (
            <div className="flex flex-col items-center gap-2 p-3 rounded-[8px] bg-white border border-border animate-fade-in">
              <QRCodeSVG value={qrUrl} size={140} level="M" fgColor="#1A1D2E" bgColor="#FFFFFF" includeMargin />
              <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
                <RefreshCw size={10} className="animate-spin" />
                请用手机微信扫描二维码
              </div>
            </div>
          )}

          {/* 操作按钮 */}
          {isBound ? (
            <Button size="sm" variant="destructive" className="w-full h-8 text-[11px]" onClick={unbind}>
              解绑微信账号
            </Button>
          ) : isBinding ? (
            <Button size="sm" variant="secondary" className="w-full h-8 text-[11px]" onClick={cancelBind}>
              取消绑定
            </Button>
          ) : (
            <Button size="sm" variant="outline" className="w-full h-8 text-[11px]" onClick={() => startBind(false)}>
              <QrCode size={13} />
              扫码绑定
            </Button>
          )}

          <Button
            size="sm"
            className="w-full h-9 rounded-[8px] text-[12px] font-semibold bg-[#17211F] hover:bg-[#22312D] shadow-[0_18px_36px_-26px_rgba(23,33,31,0.55)] active:scale-[0.98] transition-all"
            disabled={!isBound}
          >
            <MessageCircle size={14} />
            {isBound ? "已绑定 · 微信发消息试试" : "请先完成扫码绑定"}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-black text-[#171916]">连接治理</h2>
              <p className="mt-1 text-xs text-muted-foreground">当前版本采用单活动连接策略，适合企业统一微信入口。</p>
            </div>
            <Button size="sm" variant="outline" onClick={refreshStatus}>
              <RefreshCw size={14} />
              刷新状态
            </Button>
          </div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <div className="rounded-[10px] border border-[#E4E8E5] bg-white p-3">
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-muted-foreground">Active Account</p>
              <p className="mt-1 truncate font-mono text-xs text-[#171916]">{status?.accountId || "未连接"}</p>
            </div>
            <div className="rounded-[10px] border border-[#E4E8E5] bg-white p-3">
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-muted-foreground">Saved Accounts</p>
              <p className="mt-1 font-mono text-xs text-[#171916]">{status?.savedAccountCount ?? 0}</p>
            </div>
            <div className="rounded-[10px] border border-[#E4E8E5] bg-white p-3">
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-muted-foreground">Mode</p>
              <p className="mt-1 text-xs text-[#171916]">单活动连接</p>
            </div>
            <div className="rounded-[10px] border border-[#E4E8E5] bg-white p-3">
              <p className="text-[10px] font-bold uppercase tracking-[0.08em] text-muted-foreground">Policy</p>
              <p className="mt-1 text-xs text-[#171916]">新扫码需确认接管</p>
            </div>
          </div>
          <div className="rounded-[10px] border border-[#D7E8E0] bg-[#F8FBFA] p-3 text-xs leading-relaxed text-muted-foreground">
            多用户共用 JC claw 时，请固定一个企业微信入口。若需要按账号隔离多个 Bot，后续应升级为“每用户一连接池 + 会话归属锁”的架构。
          </div>
          <Button size="sm" variant="outline" className="h-9" disabled={!isBound} onClick={unbind}>
            <Link2Off size={14} />
            断开并清除当前凭证
          </Button>
        </CardContent>
      </Card>
      </div>

      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <h2 className="text-sm font-black text-[#171916]">JC claw 对话工作台</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                用自然语言调度网页端能力和微信动作。产品信息会优先查产品库，微信发送会通过 JC claw 执行。
              </p>
            </div>
            <Badge variant={botRunning ? "success" : "secondary"} className="w-fit text-[10px]">
              {botRunning ? "微信可执行" : "微信未连接"}
            </Badge>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_260px]">
            <div className="rounded-[14px] border border-[#E4E8E5] bg-white">
              <div ref={chatScrollRef} className="max-h-[380px] min-h-[260px] overflow-y-auto p-3 space-y-3">
                {chatMessages.map((msg) => (
                  <div key={msg.id} className={cn("flex", msg.role === "user" ? "justify-end" : "justify-start")}>
                    <div className={cn(
                      "max-w-[82%] rounded-[14px] px-3 py-2 text-sm leading-relaxed",
                      msg.role === "user"
                        ? "rounded-br-[5px] border border-[#D7E8E0] bg-[#EEF7F3] text-[#171916]"
                        : "rounded-bl-[5px] border border-[#E4E8E5] bg-[#FFFFFF] text-[#343A35]"
                    )}>
                      <p className="whitespace-pre-wrap">{msg.content}</p>
                      {msg.toolCalls && msg.toolCalls.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5 border-t border-[#E4E8E5] pt-2">
                          {msg.toolCalls.map((tool, index) => (
                            <Badge
                              key={`${tool.toolName}-${index}`}
                              variant={tool.status === "failed" ? "destructive" : "secondary"}
                              className="text-[10px]"
                            >
                              <Zap size={10} />
                              {toolLabel(tool.toolName)}
                              {tool.durationMs !== undefined ? ` ${tool.durationMs}ms` : ""}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                {chatLoading && (
                  <div className="flex justify-start">
                    <div className="rounded-[14px] rounded-bl-[5px] border border-[#E4E8E5] bg-white px-3 py-2">
                      <Loader2 size={14} className="animate-spin text-muted-foreground" />
                    </div>
                  </div>
                )}
              </div>

              <div className="border-t border-[#E4E8E5] p-3">
                <div className="flex gap-2">
                  <Input
                    value={chatInput}
                    onChange={(e) => setChatInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && !e.shiftKey) sendClawMessage()
                    }}
                    placeholder="例如：给最近活跃的微信用户发一条消息，告诉他我们可以提供可回收纸展示架并附上产品链接"
                    className="h-10 text-sm"
                  />
                  <Button className="h-10" onClick={() => sendClawMessage()} disabled={chatLoading || !chatInput.trim()}>
                    {chatLoading ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
                    发送
                  </Button>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              {[
                "列出当前微信活跃用户",
                "给最近活跃的微信用户发：您好，我们可以提供可回收纸展示架，稍后给您产品链接。",
                "查一下台面展示架产品，回复时带上网址",
                "给微信用户发送一条英文跟进消息，内容包含展示架产品链接",
              ].map((prompt) => (
                <button
                  key={prompt}
                  className="w-full rounded-[10px] border border-[#E4E8E5] bg-white px-3 py-2 text-left text-xs leading-relaxed text-[#343A35] transition-colors hover:bg-[#F8FBFA] active:scale-[0.99]"
                  onClick={() => sendClawMessage(prompt)}
                  disabled={chatLoading}
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
