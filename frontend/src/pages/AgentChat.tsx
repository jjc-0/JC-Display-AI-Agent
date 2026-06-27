import { useState, useRef, useEffect } from "react"
import { useSearchParams } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Send, User, Loader2, Trash2, Sparkles,
  Globe, Search, BarChart3, PenTool,
  Wand2, FileText, XCircle, Zap, Paperclip, Camera,
  Database, MessageCircle,
} from "lucide-react"
import api from "@/lib/api"
import { cn } from "@/lib/utils"
import ReactMarkdown from "react-markdown"
import ImageViewer from "@/components/chat/ImageViewer"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"

// ═══════════════════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════════════════

interface ToolCall {
  toolName: string
  input?: string
  output?: string
  status: "success" | "failed"
  durationMs?: number
}

interface RagCitation {
  rank: number
  source: string
  score: number
  snippet: string
}

interface Message {
  id: string
  role: "user" | "assistant" | "system"
  content: string
  timestamp: string
  agentLabel?: string
  toolCalls?: ToolCall[]
  ragUsed?: boolean
  ragCitations?: RagCitation[]
  images?: string[]
  isStreaming?: boolean
}

// ═══════════════════════════════════════════════════════════
// Quick action templates
// ═══════════════════════════════════════════════════════════

const quickActions = [
  { icon: <BarChart3 size={13} />, label: "市场分析", prompt: "分析展示架产品在德国市场的出口机会" },
  { icon: <Globe size={13} />, label: "翻译", prompt: "帮我翻译成英文: 这是一款高品质瓦楞纸展示架，支持定制尺寸和印刷" },
  { icon: <PenTool size={13} />, label: "生成文案", prompt: "帮我生成Floor Display Stand的Alibaba产品详情文案，目标美国市场" },
  { icon: <Search size={13} />, label: "查汇率", prompt: "现在USD兑CNY汇率是多少？1000美金能换多少人民币" },
  { icon: <Wand2 size={13} />, label: "生成产品图", prompt: "生成一张白色纸展示架产品图，超市陈列用，简洁现代设计风格" },
  { icon: <FileText size={13} />, label: "询盘评分", prompt: "分析这个询盘质量: 我们需要每月500个瓦楞纸展示架，FOB报价，长期合作意向" },
  { icon: <Camera size={13} />, label: "识图分析", prompt: "" },
]

// ═══════════════════════════════════════════════════════════
// Tool icons mapping
// ═══════════════════════════════════════════════════════════

const toolMeta: Record<string, { icon: React.ReactNode; label: string; color: string }> = {
  search:             { icon: <Search size={12} />,      label: "搜索",       color: "bg-[#EEF7F3] text-[#1F5F53] border-[#D7E8E0]" },
  search_customer:    { icon: <Search size={12} />,      label: "搜索客户",    color: "bg-[#EEF7F3] text-[#1F5F53] border-[#D7E8E0]" },
  scraper:            { icon: <Globe size={12} />,        label: "网页抓取",    color: "bg-[#EEF7F3] text-[#1F5F53] border-[#D7E8E0]" },
  currency:           { icon: <BarChart3 size={12} />,   label: "汇率查询",    color: "bg-emerald-50 text-emerald-600 border-emerald-200" },
  seo:                { icon: <BarChart3 size={12} />,   label: "SEO分析",    color: "bg-[#F4F6F5] text-[#74766F] border-[#E4E8E5]" },
  analyze_lead:       { icon: <BarChart3 size={12} />,   label: "询盘分析",    color: "bg-[#E9F7F5] text-[#087C78] border-[#BFE2DA]" },
  translate:          { icon: <Globe size={12} />,        label: "翻译",       color: "bg-amber-50 text-amber-600 border-amber-200" },
  generate_email:     { icon: <PenTool size={12} />,     label: "邮件生成",    color: "bg-amber-50 text-amber-700 border-amber-200" },
  update_customer_status: { icon: <FileText size={12} />, label: "CRM更新",  color: "bg-[#EEF7F3] text-[#1F5F53] border-[#D7E8E0]" },
  product_catalog_search: { icon: <Database size={12} />, label: "产品库", color: "bg-[#EEF7F3] text-[#1F5F53] border-[#D7E8E0]" },
  wechat_control:    { icon: <MessageCircle size={12} />, label: "JC claw", color: "bg-[#E9F7F5] text-[#087C78] border-[#BFE2DA]" },
  image_understand:   { icon: <Camera size={12} />,       label: "AI识图",    color: "bg-[#F4F6F5] text-[#343A35] border-[#E4E8E5]" },
  image_generate:     { icon: <Wand2 size={12} />,       label: "图片生成",    color: "bg-[#E9F7F5] text-[#087C78] border-[#BFE2DA]" },
  image_edit:         { icon: <PenTool size={12} />,     label: "图片编辑",    color: "bg-orange-50 text-orange-600 border-orange-200" },
}

// ═══════════════════════════════════════════════════════════
// Component
// ═══════════════════════════════════════════════════════════

const WELCOME_MSG: Message = {
  id: "welcome",
  role: "assistant",
  content: `👋 你好！我是 **JC Display AI Agent**，你的全能外贸助手。

你可以直接告诉我你想要什么，我会自动调用合适的工具：

| 能力 | 只要说... |
|------|----------|
| 🔍 市场分析 | "分析展示架在德国市场怎么样" |
| 🌐 多语言翻译 | "帮我把这段话翻成日文" |
| ✍️ B2B文案 | "帮我生成展示架的Alibaba详情页" |
| 📊 询盘评分 | "分析一下这个客户询盘质量" |
| 📧 回复邮件 | "给客户生成一封报价邮件" |
| 🖼️ 产品图生成 | "生成一张白色展示架产品图" |
| 👁️ AI识图 | 上传图片让我分析 |
| 💱 汇率换算 | "1000美金换多少人民币" |
| 🔎 客户搜索 | "搜索美国的展示架客户" |

**直接拖拽或粘贴图片到输入框，我就能识别分析！**`,
  timestamp: new Date().toLocaleTimeString(),
}

export default function AgentChat() {
  const [searchParams, setSearchParams] = useSearchParams()
  const sessionFromUrl = searchParams.get("session")
  const promptFromUrl = searchParams.get("prompt")

  const [messages, setMessages] = useState<Message[]>([{ ...WELCOME_MSG }])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState<string | null>(sessionFromUrl)
  const [attachedImages, setAttachedImages] = useState<File[]>([])
  const [imagePreviews, setImagePreviews] = useState<string[]>([])
  const scrollRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  // 防止 setSearchParams 触发重复加载
  const lastLoadedSession = useRef<string | null>(null)
  // 防止旧请求响应污染新会话
  const activeSessionRef = useRef<string | null>(null)
  // 防止 StrictMode / 组件重新挂载时重复调用 loadSession
  const abortControllerRef = useRef<AbortController | null>(null)

  // 图片查看器状态
  const [lightbox, setLightbox] = useState<{ src: string; prompt: string } | null>(null)

  // ═══════════════════════════════════════════════════════
  // Effects
  // ═══════════════════════════════════════════════════════

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" })
  }, [messages])

  useEffect(() => {
    if (!promptFromUrl || sessionFromUrl) return
    setInput(promptFromUrl)
    setSearchParams({}, { replace: true })
    inputRef.current?.focus()
  }, [promptFromUrl, sessionFromUrl, setSearchParams])

  // Load session from URL param — 仅在真正切换会话时加载
  useEffect(() => {
    // 取消上一次未完成的请求，防止 StrictMode 或快速切换导致重复调用
    abortControllerRef.current?.abort()
    const controller = new AbortController()
    abortControllerRef.current = controller
    const { signal } = controller

    if (sessionFromUrl) {
      if (lastLoadedSession.current !== sessionFromUrl) {
        lastLoadedSession.current = sessionFromUrl
        setLoading(false)
        loadSession(sessionFromUrl, signal)
      }
    } else if (lastLoadedSession.current !== null) {
      lastLoadedSession.current = null
      setLoading(false)
      setMessages([{ ...WELCOME_MSG }])
      setSessionId(null)
      setInput("")
      setAttachedImages([])
      setImagePreviews([])
    }

    return () => {
      controller.abort()
    }
  }, [sessionFromUrl])


  // ═══════════════════════════════════════════════════════
  // Session management
  // ═══════════════════════════════════════════════════════

  const loadSession = async (sid: string, signal?: AbortSignal) => {
    activeSessionRef.current = sid
    try {
      const { data } = await api.get(`/agent/session/${sid}/history`, { signal })
      // 请求已被取消，不再更新状态
      if (signal?.aborted) return
      const records = data.records || []
      if (records.length > 0) {
        setMessages(records.map((m: any) => ({
          id: m.timestamp?.toString() || Date.now().toString(),
          role: m.role,
          content: m.content,
          timestamp: m.timestamp ? new Date(m.timestamp).toLocaleTimeString() : "",
        })))
      } else {
        // 空会话：重置到初始状态
        setMessages([{ ...WELCOME_MSG }])
      }
      setSessionId(sid)
      setInput("")
      setAttachedImages([])
      setImagePreviews([])
    } catch {
      // 请求被取消属于正常行为，不需要处理
    }
  }

  const clearChat = () => {
    setMessages([{ id: "welcome", role: "assistant", content: "对话已清空。告诉我你想做什么？", timestamp: new Date().toLocaleTimeString() }])
    setSessionId(null)
    setSearchParams({})
    setAttachedImages([])
    setImagePreviews([])
  }

  // ═══════════════════════════════════════════════════════
  // Image handling
  // ═══════════════════════════════════════════════════════

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || [])
    addImages(files)
  }

  const addImages = (files: File[]) => {
    const imageFiles = files.filter(f => f.type.startsWith("image/") && f.size < 10 * 1024 * 1024)
    setAttachedImages(prev => [...prev, ...imageFiles])
    const previews = imageFiles.map(f => URL.createObjectURL(f))
    setImagePreviews(prev => [...prev, ...previews])
  }

  const removeImage = (idx: number) => {
    setAttachedImages(prev => prev.filter((_, i) => i !== idx))
    setImagePreviews(prev => prev.filter((_, i) => i !== idx))
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    const items = Array.from(e.clipboardData.items)
    const files = items.filter(i => i.type.startsWith("image/")).map(i => i.getAsFile()).filter(Boolean) as File[]
    if (files.length > 0) {
      e.preventDefault()
      addImages(files)
    }
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const files = Array.from(e.dataTransfer.files).filter(f => f.type.startsWith("image/"))
    if (files.length > 0) addImages(files)
  }

  // ═══════════════════════════════════════════════════════
  // Send message
  // ═══════════════════════════════════════════════════════

  const sendMessage = async (overrideText?: string) => {
    const text = (overrideText || input).trim()
    if ((!text && attachedImages.length === 0) || loading) return

    // 记录发起请求时的会话ID，防止响应错位
    const requestSession = sessionId
    activeSessionRef.current = requestSession

    let displayContent = text
    // 有图片无文字时，不强行写"请分析这张图片"，让LLM根据图片自行判断
    if (attachedImages.length > 0 && !text) displayContent = "[图片]"

    const userMsg: Message = {
      id: Date.now().toString(),
      role: "user",
      content: displayContent,
      timestamp: new Date().toLocaleTimeString(),
      images: imagePreviews.length > 0 ? [...imagePreviews] : undefined,
    }
    setMessages(prev => [...prev, userMsg])
    setInput("")
    setLoading(true)
    // 立即清除缩略图（图片已在用户消息中显示）
    setAttachedImages([])
    setImagePreviews([])

    try {
      if (attachedImages.length > 0) {
        await handleImageMessage(text, attachedImages, requestSession)
      } else {
        await handleTextMessage(text, requestSession)
      }
    } catch {
      if (activeSessionRef.current !== requestSession && activeSessionRef.current !== null) return // 已切换会话
      setMessages(prev => [...prev, {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content: "抱歉，请求遇到问题，请稍后重试。",
        timestamp: new Date().toLocaleTimeString(),
      }])
    } finally {
      if (activeSessionRef.current === requestSession || activeSessionRef.current === null) {
        setLoading(false)
      }
    }
  }

  const handleTextMessage = async (text: string, requestSession: string | null) => {
    const { data } = await api.post("/v2/agent/run", {
      message: text,
      sessionId: requestSession,
      enableTools: true,
    })

    // 响应到达时已切换到其他会话，丢弃
    if (activeSessionRef.current !== requestSession && activeSessionRef.current !== null) return

    setSessionId(data.sessionId)
    // Update URL without reload
    setSearchParams(data.sessionId ? { session: data.sessionId } : {})
    lastLoadedSession.current = data.sessionId // 防止 setSearchParams 触发重新加载

    const toolCalls: ToolCall[] = data.toolCalls?.map((tc: any) => ({
      toolName: tc.toolName,
      output: tc.output,
      status: tc.status,
      durationMs: tc.durationMs,
    })) || []

    setMessages(prev => [...prev, {
      id: (Date.now() + 1).toString(),
      role: "assistant",
      content: data.message,
      timestamp: new Date().toLocaleTimeString(),
      agentLabel: "JC agent",
      toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
      ragUsed: Boolean(data.ragUsed),
      ragCitations: Array.isArray(data.ragCitations) ? data.ragCitations : undefined,
    }])
  }

  /**
   * 图片缩放 + Base64 编码（限制最长边，减少 API 传输量）
   */
  const resizeAndEncode = (file: File, maxPixels: number): Promise<string> => {
    return new Promise((resolve) => {
      const img = new Image()
      img.onload = () => {
        const scale = Math.min(1, maxPixels / Math.max(img.width, img.height))
        if (scale >= 1) {
          // 原图已经够小，直接读
          const r = new FileReader()
          r.onload = () => resolve(r.result as string)
          r.readAsDataURL(file)
          return
        }
        const canvas = document.createElement("canvas")
        canvas.width = Math.round(img.width * scale)
        canvas.height = Math.round(img.height * scale)
        const ctx = canvas.getContext("2d")!
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL("image/jpeg", 0.8))
      }
      img.src = URL.createObjectURL(file)
    })
  }

  const handleImageMessage = async (text: string, files: File[], requestSession: string | null) => {
    const base64Images: string[] = []
    for (const file of files) {
      const base64 = await resizeAndEncode(file, 1024)
      base64Images.push(base64)
    }

    const prompt = text || ""

    const { data } = await api.post("/v2/agent/run", {
      message: prompt,
      sessionId: requestSession,
      enableTools: true,
      parameters: { _images: base64Images },
    })

    // 响应到达时已切换到其他会话，丢弃
    if (activeSessionRef.current !== requestSession && activeSessionRef.current !== null) return

    setSessionId(data.sessionId)
    setSearchParams(data.sessionId ? { session: data.sessionId } : {})
    lastLoadedSession.current = data.sessionId

    const toolCalls: ToolCall[] = data.toolCalls?.map((tc: any) => ({
      toolName: tc.toolName,
      output: tc.output,
      status: tc.status,
      durationMs: tc.durationMs,
    })) || []

    setMessages(prev => [...prev, {
      id: (Date.now() + 1).toString(),
      role: "assistant",
      content: data.message,
      timestamp: new Date().toLocaleTimeString(),
      agentLabel: "JC agent",
      toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
      ragUsed: Boolean(data.ragUsed),
      ragCitations: Array.isArray(data.ragCitations) ? data.ragCitations : undefined,
    }])
  }

  // ═══════════════════════════════════════════════════════
  // Image viewer actions
  // ═══════════════════════════════════════════════════════

  const handleRegenerate = (prompt: string) => {
    setInput(prompt)
    setTimeout(() => sendMessage(prompt), 150)
  }

  const handleEditImage = (editPrompt: string, action: string) => {
    const msg = `对刚才生成的图片进行局部修改：${editPrompt}`
    setInput(msg)
    setTimeout(() => sendMessage(msg), 150)
  }

  // ═══════════════════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════════════════

  return (
    <div
      className="h-full flex overflow-hidden"
      onDrop={handleDrop}
      onDragOver={e => e.preventDefault()}
    >
      {/* ══ Main Chat ══ */}
      <div className="flex-1 min-w-0 flex flex-col rounded-[16px] border border-[#E4E8E5] bg-[#FFFFFF] overflow-hidden shadow-[0_10px_28px_-26px_rgba(15,23,42,0.16)]">
        {/* Header */}
        <div className="flex-shrink-0 flex items-center justify-between px-5 py-3 border-b border-[#E4E8E5] bg-[#FFFFFF]">
          <div className="flex items-center gap-2 min-w-0">
            <Sparkles size={15} className="text-[#1F5F53] flex-shrink-0" />
            <span className="text-sm font-black text-[#171916] truncate">AI Agent</span>
            <span className="text-[10px] text-[#74766F] hidden sm:inline truncate">· 一句话驱动所有 AI 能力</span>
          </div>
          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={clearChat}>
            <Trash2 size={13} /> 清空
          </Button>
        </div>

        {/* Messages */}
        <div ref={scrollRef} className="flex-1 min-h-0 overflow-y-auto px-4 py-4 space-y-4">
          {messages.map((msg) => (
            <div key={msg.id} className={cn("flex gap-3 animate-fade-in-up", msg.role === "user" ? "justify-end" : "justify-start")}>
              {msg.role === "assistant" && (
                <CompanyLogoMark className="mt-1 h-8 w-10 flex-shrink-0" decorative />
              )}
              <div className={cn("max-w-[80%] min-w-0", msg.role === "user" ? "order-[-1]" : "")}>
                {msg.role === "user" && (
                  <div className="rounded-[14px] rounded-br-[5px] border border-[#D7E8E0] bg-[#EEF7F3] px-4 py-3 text-sm text-[#171916]">
                    {msg.images && msg.images.length > 0 && (
                      <div className="flex gap-2 mb-2 flex-wrap">
                        {msg.images.map((src, i) => (
                          <img key={i} src={src} alt={`upload-${i}`} className="w-20 h-20 object-cover rounded-[10px]" />
                        ))}
                      </div>
                    )}
                    <p className="whitespace-pre-wrap">{msg.content}</p>
                  </div>
                )}

                {msg.role === "assistant" && (
                  <div className="rounded-[14px] rounded-bl-[5px] border border-[#E4E8E5] bg-[#FFFFFF] px-4 py-3 text-sm shadow-none">
                    <div className="prose prose-sm max-w-none">
                      <ReactMarkdown
                        components={{
                          img: ({ src, alt }) => (
                            <img
                              src={src}
                              alt={alt || ""}
                              className="rounded-[10px] max-w-full max-h-[400px] object-contain my-2 cursor-zoom-in hover:opacity-90 transition-opacity border border-border/50"
                              loading="lazy"
                              onClick={() => setLightbox({ src: src || "", prompt: alt || "" })}
                            />
                          ),
                          table: ({ children }) => (
                            <div className="overflow-x-auto my-2"><table className="min-w-full text-xs border-collapse">{children}</table></div>
                          ),
                          th: ({ children }) => <th className="border border-border px-2 py-1 bg-muted text-left font-medium">{children}</th>,
                          td: ({ children }) => <td className="border border-border px-2 py-1">{children}</td>,
                        }}
                      >
                        {msg.content}
                      </ReactMarkdown>
                    </div>

                    {msg.toolCalls && msg.toolCalls.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border/50">
                        <div className="text-[10px] text-muted-foreground mb-1.5 flex items-center gap-1">
                          <Zap size={10} /> 工具调用 ({msg.toolCalls.length})
                        </div>
                        <div className="space-y-1">
                          {msg.toolCalls.map((tc, i) => {
                            const meta = toolMeta[tc.toolName] || { icon: <Zap size={12} />, label: tc.toolName, color: "bg-[#F4F6F5] text-[#74766F] border-[#E4E8E5]" }
                            return (
                              <div key={i} className={cn("flex items-center gap-2 px-2.5 py-1.5 rounded-[10px] border text-[11px]", meta.color)}>
                                {meta.icon}
                                <span className="font-medium">{meta.label}</span>
                                {tc.durationMs !== undefined && <span className="text-[10px] opacity-60 ml-auto">{tc.durationMs}ms</span>}
                                {tc.status === "failed" && <XCircle size={12} className="text-red-500" />}
                              </div>
                            )
                          })}
                        </div>
                      </div>
                    )}

                    {msg.ragUsed && msg.ragCitations && msg.ragCitations.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border/50">
                        <div className="mb-1.5 flex items-center gap-1 text-[10px] text-muted-foreground">
                          <Database size={10} /> 知识库引用 ({msg.ragCitations.length})
                        </div>
                        <div className="space-y-1.5">
                          {msg.ragCitations.slice(0, 4).map((citation) => (
                            <div
                              key={`${citation.rank}-${citation.source}`}
                              className="rounded-[10px] border border-[#D7E8E0] bg-[#F8FBFA] px-2.5 py-2 text-[11px] text-[#343A35]"
                            >
                              <div className="mb-1 flex items-center justify-between gap-2">
                                <span className="font-bold text-[#1F5F53]">
                                  #{citation.rank} {citation.source}
                                </span>
                                <span className="font-mono text-[10px] text-[#74766F]">
                                  {(Number(citation.score || 0) * 100).toFixed(0)}%
                                </span>
                              </div>
                              <p className="line-clamp-2 text-[#74766F]">{citation.snippet}</p>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-[10px] opacity-40">{msg.timestamp}</span>
                      {msg.agentLabel && <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4">{msg.agentLabel}</Badge>}
                    </div>
                  </div>
                )}
              </div>
              {msg.role === "user" && (
                <div className="w-8 h-8 rounded-[10px] bg-[#171916] flex items-center justify-center flex-shrink-0 mt-1 shadow-none">
                  <User size={15} className="text-white" />
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className="flex gap-3 animate-fade-in">
              <CompanyLogoMark className="mt-1 h-8 w-10 flex-shrink-0" decorative />
              <div className="rounded-[14px] rounded-bl-[5px] border border-[#E4E8E5] bg-[#FFFFFF] px-4 py-3">
                <div className="flex gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" />
                  <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" style={{ animationDelay: "150ms" }} />
                  <span className="w-2 h-2 rounded-full bg-muted-foreground/40 animate-bounce" style={{ animationDelay: "300ms" }} />
                </div>
              </div>
            </div>
          )}
        </div>

        {messages.length <= 1 && !loading && (
          <div className="flex-shrink-0 px-4 pb-2">
            <div className="flex flex-wrap gap-1.5">
              {quickActions.map((action, i) => (
                <button
                  key={i}
                  onClick={() => {
                    if (action.label === "识图分析") {
                      fileInputRef.current?.click()
                    } else {
                      setInput(action.prompt)
                      setTimeout(() => sendMessage(action.prompt), 100)
                    }
                  }}
                  className="inline-flex items-center gap-1.5 rounded-full border border-[#E4E8E5] bg-[#FFFFFF] px-3 py-1.5 text-[11px] font-bold text-[#74766F] transition-colors hover:bg-[#F4F6F5] hover:text-[#171916]"
                >
                  {action.icon}
                  {action.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {imagePreviews.length > 0 && (
          <div className="flex-shrink-0 px-4 pb-2 flex gap-2 flex-wrap">
            {imagePreviews.map((src, i) => (
              <div key={i} className="relative w-14 h-14 rounded-[10px] overflow-hidden border border-border">
                <img src={src} alt={`preview-${i}`} className="w-full h-full object-cover" />
                <button onClick={() => removeImage(i)} className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-background border border-border flex items-center justify-center hover:bg-destructive hover:text-white transition-colors">
                  <XCircle size={10} />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="flex-shrink-0 px-4 pb-4 pt-3 border-t border-[#E4E8E5] bg-[#FFFFFF]">
          <div className="flex gap-2">
            <input ref={fileInputRef} type="file" accept="image/*" multiple className="hidden" onChange={handleImageSelect} />
            <Button variant="ghost" size="icon" className="h-10 w-10 flex-shrink-0" onClick={() => fileInputRef.current?.click()}>
              <Paperclip size={18} className="text-muted-foreground" />
            </Button>
            <Input
              ref={inputRef}
              placeholder="告诉我你想要什么... 例如: 生成一款展示架的英文产品文案"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => { if (e.key === "Enter" && !e.shiftKey) sendMessage() }}
              onPaste={handlePaste}
              disabled={loading}
              className="flex-1 h-10 text-sm"
            />
            <Button onClick={() => sendMessage()} disabled={loading || (!input.trim() && attachedImages.length === 0)} variant="gradient" size="icon" className="h-10 w-10 flex-shrink-0">
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            </Button>
          </div>
          <p className="text-[10px] text-muted-foreground/50 mt-1.5 text-center">
            可直接拖拽/粘贴图片 · Agent 自动选择工具 · Enter 发送
          </p>
        </div>
      </div>

      {/* ══ 图片查看器 ══ */}
      <ImageViewer
        src={lightbox?.src || null}
        prompt={lightbox?.prompt}
        onClose={() => setLightbox(null)}
        onRegenerate={handleRegenerate}
        onEdit={handleEditImage}
      />
    </div>
  )
}
