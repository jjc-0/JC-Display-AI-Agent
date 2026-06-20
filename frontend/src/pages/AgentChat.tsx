import { useState, useRef, useEffect, useCallback } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Send,
  User,
  Loader2,
  Zap,
  Trash2,
  MessageSquare,
  MessageCircle,
  Plus,
  Sparkles,
  Check,
  X,
} from "lucide-react"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

interface Message {
  id: string
  role: "user" | "assistant" | "system"
  content: string
  timestamp: string
  model?: string
}

interface SessionItem {
  sessionId: string
  title: string
  type: string
  messageCount: number
  lastActive: string
  modelUsed: string
}

export default function AgentChat() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      content: "你好！我是 JC Display AI Agent，可以为你提供智能服务。\n\n你可以问我：产品咨询、市场分析、文案撰写、翻译等任何问题。",
      timestamp: new Date().toLocaleTimeString(),
    },
  ])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [enableTools, setEnableTools] = useState(true)
  const scrollRef = useRef<HTMLDivElement>(null)

  // Session history
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [loadingSessions, setLoadingSessions] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editValue, setEditValue] = useState("")
  const [autoTitleLock, setAutoTitleLock] = useState<Set<string>>(new Set())

  const fetchSessions = useCallback(async () => {
    setLoadingSessions(true)
    try {
      const { data } = await api.get("/agent/sessions")
      setSessions(data.sessions || [])
    } catch {} finally {
      setLoadingSessions(false)
    }
  }, [])

  useEffect(() => { fetchSessions() }, [fetchSessions])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [messages])

  const loadSession = async (sid: string) => {
    try {
      const { data } = await api.get(`/agent/session/${sid}/history`)
      const records = data.records || []
      setMessages(records.map((m: any) => ({
        id: m.timestamp?.toString() || Date.now().toString(),
        role: m.role,
        content: m.content,
        timestamp: m.timestamp ? new Date(m.timestamp).toLocaleTimeString() : "",
      })))
      setSessionId(sid)
    } catch {}
  }

  const sendMessage = async () => {
    if (!input.trim() || loading) return
    const userMsg: Message = {
      id: Date.now().toString(),
      role: "user",
      content: input.trim(),
      timestamp: new Date().toLocaleTimeString(),
    }
    setMessages((prev) => [...prev, userMsg])
    setInput("")
    setLoading(true)

    try {
      const { data } = await api.post("/agent/chat", {
        sessionId,
        message: userMsg.content,
        enableTools,
      })
      setSessionId(data.sessionId)
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          role: "assistant",
          content: data.message || data.result,
          timestamp: new Date().toLocaleTimeString(),
          model: data.modelUsed,
        },
      ])
      // Refresh session list after new message
      fetchSessions()
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          role: "assistant",
          content: "抱歉，请求遇到问题，请稍后重试。",
          timestamp: new Date().toLocaleTimeString(),
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const clearChat = () => {
    setMessages([
      {
        id: "welcome",
        role: "assistant",
        content: "对话已清空，有什么我可以帮你的？",
        timestamp: new Date().toLocaleTimeString(),
      },
    ])
    setSessionId(null)
  }

  const startEdit = (sid: string, current: string) => {
    setEditingId(sid)
    setEditValue(current || "")
  }

  const saveTitle = async (sid: string) => {
    if (!editValue.trim()) return
    try {
      await api.put(`/agent/session/${sid}/title`, { title: editValue.trim() })
      setSessions((prev) => prev.map((s) => s.sessionId === sid ? { ...s, title: editValue.trim() } : s))
    } catch {} finally {
      setEditingId(null)
    }
  }

  const autoTitleSession = async (sid: string) => {
    if (autoTitleLock.has(sid)) return
    setAutoTitleLock((prev) => new Set(prev).add(sid))
    try {
      const { data } = await api.post(`/agent/session/${sid}/auto-title`)
      if (data.success && data.title) {
        setSessions((prev) => prev.map((s) => s.sessionId === sid ? { ...s, title: data.title } : s))
      }
    } catch {} finally {
      setAutoTitleLock((prev) => {
        const next = new Set(prev)
        next.delete(sid)
        return next
      })
    }
  }

  const typeLabel = (t: string) => {
    const map: Record<string, string> = { chat: "对话", inquiry: "询盘", copywriting: "文案", translate: "翻译", analysis: "分析", wechat: "微信" }
    return map[t] || t
  }

  const timeAgo = (d: string) => {
    if (!d) return ""
    const diff = Date.now() - new Date(d).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return "刚刚"
    if (mins < 60) return `${mins}分钟前`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}小时前`
    return `${Math.floor(hours / 24)}天前`
  }

  return (
    <div className="h-full flex gap-3 overflow-hidden">
      {/* Session History Sidebar */}
      <div className="w-60 flex-shrink-0 rounded-xl border border-border bg-white flex flex-col overflow-hidden">
        <div className="px-3 py-3 border-b border-border flex-shrink-0">
          <Button variant="outline" size="sm" className="w-full justify-start gap-2 text-xs" onClick={clearChat}>
            <Plus size={14} />
            新对话
          </Button>
        </div>
        <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden">
            <div className="p-2 space-y-0.5">
              {loadingSessions ? (
                <div className="flex justify-center py-8">
                  <Loader2 size={18} className="animate-spin text-muted-foreground" />
                </div>
              ) : sessions.length === 0 ? (
                <p className="text-center text-xs text-muted-foreground py-8">暂无历史会话</p>
              ) : (
                sessions.map((s) => (
                  <div key={s.sessionId} className="relative group">
                    {editingId === s.sessionId ? (
                      <div className="px-3 py-2 flex items-center gap-1.5">
                        <Input
                          autoFocus
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") saveTitle(s.sessionId)
                            if (e.key === "Escape") setEditingId(null)
                          }}
                          className="h-7 text-xs px-2 py-0"
                        />
                        <Button variant="ghost" size="icon" className="h-6 w-6 flex-shrink-0" onClick={() => saveTitle(s.sessionId)}>
                          <Check size={12} />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-6 w-6 flex-shrink-0" onClick={() => setEditingId(null)}>
                          <X size={12} />
                        </Button>
                      </div>
                    ) : (
                      <div
                        onClick={() => loadSession(s.sessionId)}
                        onDoubleClick={(e) => { e.preventDefault(); startEdit(s.sessionId, s.title) }}
                        className={cn(
                          "w-full text-left px-3 py-2 rounded-lg transition-colors cursor-pointer overflow-hidden",
                          sessionId === s.sessionId
                            ? "bg-accent"
                            : "hover:bg-muted"
                        )}
                      >
                        <div className="flex items-center gap-2 overflow-hidden">
                          <MessageSquare size={13} className="text-muted-foreground flex-shrink-0" />
                          <span className="text-xs font-medium truncate flex-1 min-w-0">
                            {s.title || "未命名会话"}
                          </span>
                          <button
                            className="flex-shrink-0 p-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity hover:bg-border/50"
                            onClick={(e) => { e.stopPropagation(); autoTitleSession(s.sessionId) }}
                            title="AI 自动命名"
                          >
                            {autoTitleLock.has(s.sessionId) ? (
                              <Loader2 size={11} className="animate-spin text-muted-foreground" />
                            ) : (
                              <Sparkles size={11} className="text-amber-500" />
                            )}
                          </button>
                        </div>
                        <div className="flex items-center gap-2 mt-0.5 pl-5 overflow-hidden">
                          {s.type === "wechat" ? (
                            <Badge variant="outline" className="text-[9px] px-1 py-0 h-4 font-normal flex-shrink-0 border-green-300 text-green-700 bg-green-50 gap-0.5">
                              <MessageCircle size={9} />微信
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[9px] px-1 py-0 h-4 font-normal flex-shrink-0">
                              {typeLabel(s.type)}
                            </Badge>
                          )}
                          <span className="text-[10px] text-muted-foreground truncate">
                            {timeAgo(s.lastActive)}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 min-w-0 flex flex-col rounded-xl border border-border bg-white overflow-hidden">
        {/* Slim nav bar — title + controls in one row */}
        <div className="flex-shrink-0 flex items-center justify-between px-5 py-2.5 border-b border-border">
          <div className="flex items-center gap-2 min-w-0">
            <MessageSquare size={15} className="text-muted-foreground flex-shrink-0" />
            <span className="text-sm font-semibold text-foreground truncate">AI Agent 对话</span>
            <span className="text-[10px] text-muted-foreground hidden sm:inline truncate">· 基于 RAG 知识库的智能客服 · 多轮对话 · 工具调用</span>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <Badge
              variant={enableTools ? "success" : "slate"}
              className="cursor-pointer text-[11px] px-2.5 py-0.5"
              onClick={() => setEnableTools(!enableTools)}
            >
              <Zap size={10} />
              {enableTools ? "Tools ON" : "Tools OFF"}
            </Badge>
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={clearChat}>
              <Trash2 size={13} /> 清空
            </Button>
          </div>
        </div>

        {/* Chat card fills remaining */}
        <div className="flex-1 min-h-0 p-3">
          <Card className="h-full flex flex-col overflow-hidden border-0 shadow-none">
            <CardContent className="flex-1 min-h-0 flex flex-col p-0">
              <ScrollArea className="flex-1 min-h-0 px-5 py-4">
                <div className="space-y-4">
                  {messages.map((msg) => (
                    <div
                      key={msg.id}
                      className={cn(
                        "flex gap-3 animate-fade-in-up",
                        msg.role === "user" ? "justify-end" : "justify-start"
                      )}
                    >
                      {msg.role === "assistant" && (
                        <img src="/logo.png" alt="AI" className="w-8 h-8 rounded-[10px] object-cover flex-shrink-0" />
                      )}
                      <div
                        className={cn(
                          "max-w-[75%] rounded-[16px] px-4 py-3 text-sm leading-relaxed",
                          msg.role === "user"
                            ? "bg-muted rounded-br-[6px]"
                            : "bg-muted rounded-bl-[6px]"
                        )}
                      >
                        <p className="whitespace-pre-wrap">{msg.content}</p>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] opacity-50">{msg.timestamp}</span>
                          {msg.model && (
                            <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4 font-normal">
                              {msg.model}
                            </Badge>
                          )}
                        </div>
                      </div>
                      {msg.role === "user" && (
                        <div className="w-8 h-8 rounded-[10px] bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center flex-shrink-0">
                          <User size={15} className="text-white" />
                        </div>
                      )}
                    </div>
                  ))}
                  {loading && (
                    <div className="flex gap-3 items-center animate-fade-in">
                      <img src="/logo.png" alt="AI" className="w-8 h-8 rounded-[10px] object-cover flex-shrink-0" />
                      <div className="flex gap-1.5 px-4 py-3 rounded-[16px] rounded-bl-[6px] bg-muted">
                        <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" style={{ animationDelay: "0ms" }} />
                        <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" style={{ animationDelay: "150ms" }} />
                        <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" style={{ animationDelay: "300ms" }} />
                      </div>
                    </div>
                  )}
                </div>
              </ScrollArea>

              <div className="flex-shrink-0 px-4 py-3 border-t border-border">
                <div className="flex gap-2">
                  <Input
                    placeholder="输入你的问题..."
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && sendMessage()}
                    disabled={loading}
                  />
                  <Button onClick={sendMessage} disabled={loading || !input.trim()} variant="gradient" size="icon">
                    {loading ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
