import { useState, useEffect, useCallback } from "react"
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom"
import {
  Home,
  MessageSquare,
  Bot,
  Zap,
  Workflow,
  FileText,
  Database,
  Cog,
  Plus,
  Loader2,
  Check,
  X,
} from "lucide-react"
import { cn } from "@/lib/utils"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import api from "@/lib/api"

interface NavItem {
  href: string
  label: string
  icon: React.ReactNode
  badge?: string
  badgeVariant?: "default" | "secondary" | "purple" | "blue" | "success" | "warning" | "outline"
}

interface NavGroup {
  title: string
  items: NavItem[]
}

interface SessionItem {
  sessionId: string
  title: string
  type: string
  messageCount: number
  updatedAt: string
}

const navigation: NavGroup[] = [
  {
    title: "功能",
    items: [
      { href: "/agent-chat", label: "AI Agent 对话", icon: <MessageSquare size={17} />, badge: "AI", badgeVariant: "purple" },
    ],
  },
  {
    title: "展示",
    items: [
      { href: "/agent-square", label: "Agent 编排", icon: <Bot size={17} /> },
      { href: "/templates", label: "Prompt 迭代", icon: <FileText size={17} /> },
      { href: "/workflow", label: "工作流", icon: <Workflow size={17} />, badge: "DAG", badgeVariant: "blue" },
      { href: "/agent-execution", label: "执行监控", icon: <Zap size={17} /> },
      { href: "/dashboard", label: "数据看板", icon: <Home size={17} /> },
      { href: "/knowledge-base", label: "知识库", icon: <Database size={17} />, badge: "RAG", badgeVariant: "secondary" },
      { href: "/jc-claw", label: "JC-CLAW", icon: <Cog size={17} />, badge: "JC", badgeVariant: "success" },
    ],
  },
]

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 px-3 py-2.5 rounded-[12px] text-[13px] font-medium transition-all duration-200",
    isActive
      ? "bg-accent text-accent-foreground"
      : "text-muted-foreground hover:bg-muted hover:text-foreground"
  )

const SIDEBAR_W = 256
const MAX_SESSIONS = 10

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()

  // Session history state
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [loadingSessions, setLoadingSessions] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editValue, setEditValue] = useState("")

  const fetchSessions = useCallback(async () => {
    setLoadingSessions(true)
    try {
      const { data } = await api.get("/agent/sessions")
      const list: SessionItem[] = data.sessions || []
      setSessions(list.slice(0, MAX_SESSIONS))
    } catch {} finally {
      setLoadingSessions(false)
    }
  }, [])

  useEffect(() => { fetchSessions() }, [fetchSessions, location.pathname])

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

  const startEdit = (sid: string, current: string) => {
    setEditingId(sid)
    setEditValue(current || "")
  }

  const saveTitle = async (sid: string) => {
    if (!editValue.trim()) return
    try {
      await api.put(`/agent/session/${sid}/title`, { title: editValue.trim() })
      setSessions(prev => prev.map(s => s.sessionId === sid ? { ...s, title: editValue.trim() } : s))
    } catch {} finally {
      setEditingId(null)
    }
  }

  const newChat = () => navigate("/agent-chat")

  const currentSessionId = new URLSearchParams(location.search).get("session")

  return (
    <div style={{ display: "flex", height: "100vh", overflow: "hidden", background: "#F8F9FB" }}>
      {/* Sidebar */}
      <aside
        style={{
          width: SIDEBAR_W,
          flexShrink: 0,
          display: "flex",
          flexDirection: "column",
          height: "100vh",
          background: "#FFFFFF",
          borderRight: "1px solid #EAECF0",
        }}
      >
        {/* Logo */}
        <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "20px 20px 16px" }}>
          <img src="/logo.png" alt="JC Display" className="w-9 h-9 rounded-[14px] object-cover" />
          <div className="leading-tight">
            <div className="text-[15px] font-bold text-foreground tracking-tight">JC Display AI</div>
            <div className="text-[10px] text-muted-foreground/60 font-medium">B2B Export Agent</div>
          </div>
        </div>

        {/* Navigation - scrollable */}
        <nav
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            paddingLeft: 12,
            paddingRight: 12,
          }}
        >
          {/* ══ 功能导航 ══ */}
          {navigation.filter(g => g.title === "功能").map((group) => (
            <div key={group.title} className="mb-1">
              <div className="px-3 py-3 pb-2 text-[10px] text-muted-foreground tracking-[1.6px] font-bold uppercase">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => (
                  <NavLink key={item.href} to={item.href} className={navLinkClass}>
                    <span className="flex-shrink-0">{item.icon}</span>
                    <span className="flex-1 truncate">{item.label}</span>
                    {item.badge && (
                      <Badge variant={item.badgeVariant || "default"} className="text-[10px] px-1.5 py-0 h-5">
                        {item.badge}
                      </Badge>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}

          {/* ══ 历史对话 (最多10条) ══ */}
          <div className="mb-1">
            <div className="flex items-center justify-between px-3 py-3 pb-2">
              <span className="text-[10px] text-muted-foreground tracking-[1.6px] font-bold uppercase">
                历史对话
              </span>
              <Button variant="ghost" size="icon" className="h-5 w-5" onClick={newChat} title="新对话">
                <Plus size={13} className="text-muted-foreground" />
              </Button>
            </div>
            <div className="space-y-0.5">
              {loadingSessions ? (
                <div className="flex justify-center py-4">
                  <Loader2 size={14} className="animate-spin text-muted-foreground" />
                </div>
              ) : sessions.length === 0 ? (
                <p className="text-center text-[11px] text-muted-foreground/50 py-4">暂无历史</p>
              ) : sessions.map(s => (
                <div key={s.sessionId} className="group relative">
                  {editingId === s.sessionId ? (
                    <div className="px-2 py-1 flex items-center gap-1">
                      <Input autoFocus value={editValue} onChange={e => setEditValue(e.target.value)}
                        onKeyDown={e => { if (e.key === "Enter") saveTitle(s.sessionId); if (e.key === "Escape") setEditingId(null) }}
                        className="h-6 text-[11px] px-2 py-0" />
                      <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => saveTitle(s.sessionId)}><Check size={11} /></Button>
                      <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => setEditingId(null)}><X size={11} /></Button>
                    </div>
                  ) : (
                    <div
                      onClick={() => navigate(`/agent-chat?session=${s.sessionId}`)}
                      onDoubleClick={e => { e.preventDefault(); startEdit(s.sessionId, s.title) }}
                      className={cn(
                        "w-full text-left px-3 py-2 rounded-lg transition-colors cursor-pointer",
                        currentSessionId === s.sessionId ? "bg-accent" : "hover:bg-muted"
                      )}
                    >
                      <div className="flex items-center gap-2">
                        <MessageSquare size={12} className="text-muted-foreground flex-shrink-0" />
                        <span className="text-[11px] font-medium truncate flex-1">{s.title || "未命名"}</span>
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 pl-5">
                        <span className="text-[10px] text-muted-foreground">{timeAgo(s.updatedAt)}</span>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* ══ 展示导航 ══ */}
          {navigation.filter(g => g.title === "展示").map((group) => (
            <div key={group.title} className="mb-1">
              <div className="px-3 py-3 pb-2 text-[10px] text-muted-foreground tracking-[1.6px] font-bold uppercase">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => (
                  <NavLink key={item.href} to={item.href} className={navLinkClass}>
                    <span className="flex-shrink-0">{item.icon}</span>
                    <span className="flex-1 truncate">{item.label}</span>
                    {item.badge && (
                      <Badge variant={item.badgeVariant || "default"} className="text-[10px] px-1.5 py-0 h-5">
                        {item.badge}
                      </Badge>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </nav>

        {/* Footer */}
        <div style={{ padding: 12, borderTop: "1px solid #EAECF0" }}>
          <div className="flex items-center gap-3 px-2 py-2 rounded-[12px]">
            <img src="/logo.png" alt="" className="w-8 h-8 rounded-[10px] object-cover" />
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold text-foreground truncate">JC Display Admin</div>
              <div className="text-[10px] text-muted-foreground truncate">平台管理员</div>
            </div>
            <div className="w-2 h-2 rounded-full bg-emerald-400" />
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main
        style={{
          flex: "1 1 0",
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          height: "100vh",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            padding: "16px 16px 16px 20px",
          }}
        >
          <Outlet />
        </div>
      </main>
    </div>
  )
}
