import { useCallback, useEffect, useState } from "react"
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom"
import {
  Check,
  Loader2,
  LogOut,
  MessageSquare,
  Plus,
  Repeat2,
  UserRound,
  X,
} from "lucide-react"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { navigation } from "@/config/navigation"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

interface SessionItem {
  sessionId: string
  title: string
  type: string
  messageCount: number
  updatedAt: string
}

interface UserProfile {
  username?: string
  displayName?: string
  email?: string
  role?: string
  avatarUrl?: string
  createdAt?: string
}

const SIDEBAR_W = 224
const MAX_SESSIONS = 10

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 px-3 py-2.5 rounded-[10px] text-[13px] font-semibold transition-all duration-200",
    isActive
      ? "bg-[#EEF7F3] text-[#1F5F53] ring-1 ring-[#D7E8E0]"
      : "text-[#74766F] hover:bg-[#F6F8F7] hover:text-[#171916]"
  )

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [authAccount, setAuthAccount] = useState(() => localStorage.getItem("jc-display-login-account") || "")
  const [authRole, setAuthRole] = useState(() => localStorage.getItem("jc-display-login-role") || "")
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [profile, setProfile] = useState<UserProfile | null>(null)
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
    } catch {
      setSessions([])
    } finally {
      setLoadingSessions(false)
    }
  }, [])

  const loadProfile = useCallback(async () => {
    try {
      const { data } = await api.get("/auth/me")
      setProfile(data)
      const displayName = data.displayName || data.username || ""
      setAuthAccount(displayName)
      setAuthRole(data.role || "")
      localStorage.setItem("jc-display-login-account", displayName)
      localStorage.setItem("jc-display-login-role", data.role || "")
    } catch {
      setProfile(null)
    }
  }, [])

  useEffect(() => {
    fetchSessions()
  }, [fetchSessions, location.pathname])

  useEffect(() => {
    const syncAuthDisplay = () => {
      setAuthAccount(localStorage.getItem("jc-display-login-account") || "")
      setAuthRole(localStorage.getItem("jc-display-login-role") || "")
      loadProfile()
    }
    syncAuthDisplay()
    window.addEventListener("jc-auth-profile-updated", syncAuthDisplay)
    return () => window.removeEventListener("jc-auth-profile-updated", syncAuthDisplay)
  }, [loadProfile, location.pathname])

  useEffect(() => {
    const close = (event: MouseEvent) => {
      const target = event.target as HTMLElement
      if (!target.closest("[data-user-menu]")) setUserMenuOpen(false)
    }
    window.addEventListener("click", close)
    return () => window.removeEventListener("click", close)
  }, [])

  const currentSessionId = new URLSearchParams(location.search).get("session")
  const flatNav = navigation.flatMap((group) => group.items)
  const currentItem = flatNav.find((item) => location.pathname.startsWith(item.href))
  const currentTitle = currentItem?.label || "AI Agent 对话"
  const profileCreated = profile?.createdAt
    ? new Date(profile.createdAt).toLocaleDateString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" })
    : "暂无记录"

  const timeAgo = (value: string) => {
    if (!value) return ""
    const diff = Date.now() - new Date(value).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return "刚刚"
    if (mins < 60) return `${mins}分钟前`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}小时前`
    return `${Math.floor(hours / 24)}天前`
  }

  const saveTitle = async (sessionId: string) => {
    const nextTitle = editValue.trim()
    if (!nextTitle) return
    try {
      await api.put(`/agent/session/${sessionId}/title`, { title: nextTitle })
      setSessions((prev) => prev.map((item) => item.sessionId === sessionId ? { ...item, title: nextTitle } : item))
    } catch {
      // 保留当前标题，避免接口失败时误导用户。
    } finally {
      setEditingId(null)
    }
  }

  const logout = () => {
    localStorage.removeItem("jc-auth-token")
    localStorage.removeItem("jc-display-login-role")
    localStorage.removeItem("jc-display-login-account")
    setAuthAccount("")
    setAuthRole("")
    navigate("/login", { replace: true })
  }

  const renderNavGroup = (group: (typeof navigation)[number]) => (
    <div key={group.title} className="mb-1">
      <div className="px-3 py-3 pb-2 text-[10px] text-[#6F716B] tracking-[0.08em] font-black uppercase">
        {group.title}
      </div>
      <div className="space-y-0.5">
        {group.items.map((item) => (
          <NavLink key={item.href} to={item.href} className={navLinkClass}>
            <span className="flex-shrink-0">{item.icon}</span>
            <span className="flex-1 truncate">{item.label}</span>
            {item.badge && (
              <Badge variant={item.badgeVariant || "default"} className="h-5 px-1.5 py-0 text-[10px]">
                {item.badge}
              </Badge>
            )}
          </NavLink>
        ))}
      </div>
    </div>
  )

  return (
    <div className="workspace-shell">
      <aside className="workspace-sidebar" style={{ width: SIDEBAR_W }}>
        <div style={{ display: "flex", alignItems: "center", gap: 11, padding: "18px 14px 20px" }}>
          <CompanyLogoMark className="h-8 w-10 flex-shrink-0" />
          <div className="leading-tight">
            <div className="text-[15px] font-black text-[#171916] tracking-tight">JC Display AI</div>
            <div className="text-[10px] text-[#7B7D76] font-semibold">Export Console</div>
          </div>
        </div>

        <nav
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            paddingLeft: 12,
            paddingRight: 12,
          }}
        >
          {navigation.filter((group) => group.title === "API").map(renderNavGroup)}

          <div className="mb-1">
            <div className="flex items-center justify-between px-3 py-3 pb-2">
              <span className="text-[10px] text-[#6F716B] tracking-[0.08em] font-black uppercase">
                历史对话
              </span>
              <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => navigate("/agent-chat")} title="新对话">
                <Plus size={13} className="text-muted-foreground" />
              </Button>
            </div>
            <div className="space-y-0.5">
              {loadingSessions ? (
                <div className="flex justify-center py-4">
                  <Loader2 size={14} className="animate-spin text-muted-foreground" />
                </div>
              ) : sessions.length === 0 ? (
                <p className="py-4 text-center text-[11px] text-muted-foreground/50">暂无历史</p>
              ) : sessions.map((session) => (
                <div key={session.sessionId} className="group relative">
                  {editingId === session.sessionId ? (
                    <div className="flex items-center gap-1 px-2 py-1">
                      <Input
                        autoFocus
                        value={editValue}
                        onChange={(event) => setEditValue(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") saveTitle(session.sessionId)
                          if (event.key === "Escape") setEditingId(null)
                        }}
                        className="h-6 px-2 py-0 text-[11px]"
                      />
                      <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => saveTitle(session.sessionId)}>
                        <Check size={11} />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => setEditingId(null)}>
                        <X size={11} />
                      </Button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => navigate(`/agent-chat?session=${session.sessionId}`)}
                      onDoubleClick={() => {
                        setEditingId(session.sessionId)
                        setEditValue(session.title || "")
                      }}
                      className={cn(
                        "w-full cursor-pointer rounded-lg px-3 py-2 text-left transition-colors",
                        currentSessionId === session.sessionId ? "bg-accent" : "hover:bg-muted"
                      )}
                    >
                      <div className="flex items-center gap-2">
                        <MessageSquare size={12} className="flex-shrink-0 text-muted-foreground" />
                        <span className="flex-1 truncate text-[11px] font-medium">{session.title || "未命名"}</span>
                      </div>
                      <div className="mt-0.5 flex items-center gap-2 pl-5">
                        <span className="text-[10px] text-muted-foreground">{timeAgo(session.updatedAt)}</span>
                      </div>
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {navigation.filter((group) => group.title !== "API").map(renderNavGroup)}
        </nav>

        <div style={{ padding: 12, borderTop: "1px solid #E4E8E5" }}>
          <NavLink to="/profile" className={navLinkClass}>
            <UserRound size={17} />
            <span className="flex-1 truncate">个人信息</span>
          </NavLink>
          <div className="flex items-center gap-2 px-2 py-2 text-[11px] font-semibold text-[#74766F]">
            <span className="workspace-status-dot" />
            系统在线
          </div>
        </div>
      </aside>

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
        <div className="workspace-top-strip">
          <div className="flex min-w-0 flex-col justify-center">
            <span className="text-[10px] font-bold text-[#74766F]">用户控制台</span>
            <span className="truncate text-sm font-black text-[#171916]">{currentTitle}</span>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden items-center gap-2 rounded-full border border-[#E4E8E5] bg-white px-4 py-3 text-sm font-black text-[#171916] lg:flex">
              <span className="workspace-status-dot" />
              网关在线
            </div>
            <div className="relative" data-user-menu>
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation()
                  setUserMenuOpen((open) => !open)
                }}
                className="flex min-h-12 items-center gap-3 rounded-full border border-[#D7E8E0] bg-white py-1.5 pl-1.5 pr-4 text-left transition-colors hover:bg-[#F6F8F7]"
              >
                <CompanyLogoMark className="h-9 w-11" decorative />
                <div className="hidden leading-tight sm:block">
                  <div className="max-w-[160px] truncate text-sm font-black text-[#171916]">
                    {authAccount || "未登录"}
                  </div>
                  <div className="text-[10px] font-semibold text-[#171916]">{authRole === "admin" ? "Admin" : "User"}</div>
                </div>
              </button>

              {userMenuOpen && (
                <div className="workspace-user-menu right-0 top-[58px] w-[330px] rounded-[26px] border border-[#E4E8E5] bg-white p-4 shadow-[0_26px_80px_-54px_rgba(15,23,42,0.34)]">
                  <div className="rounded-[16px] border border-[#E4E8E5] bg-[#F8FBFA] p-3">
                    <div className="flex items-center gap-3">
                      {profile?.avatarUrl ? (
                        <img src={profile.avatarUrl} alt="" className="h-12 w-12 rounded-[14px] object-cover" />
                      ) : (
                        <CompanyLogoMark className="h-12 w-14" decorative />
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-sm font-black text-[#171916]">
                          {profile?.email || profile?.username || authAccount || "未登录"}
                        </div>
                        <div className="truncate text-xs font-medium text-[#74766F]">
                          {profile?.username || authAccount || "未登录"}
                        </div>
                      </div>
                      <Badge variant="success" className="text-[10px]">
                        {authRole === "admin" ? "Admin" : "User"}
                      </Badge>
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-2">
                    <div className="rounded-[14px] border border-[#E4E8E5] bg-white p-3">
                      <div className="text-[11px] font-bold text-[#74766F]">账户角色</div>
                      <div className="mt-2 text-sm font-black text-[#171916]">{authRole === "admin" ? "管理员" : "普通用户"}</div>
                    </div>
                    <div className="rounded-[14px] border border-[#E4E8E5] bg-white p-3">
                      <div className="text-[11px] font-bold text-[#74766F]">注册时间</div>
                      <div className="mt-2 text-sm font-black text-[#171916]">{profileCreated}</div>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setUserMenuOpen(false)
                      navigate("/profile")
                    }}
                    className="mt-4 flex w-full items-center gap-3 rounded-[8px] bg-white px-4 py-3 text-left shadow-[0_18px_42px_-34px_rgba(15,23,42,0.28)] transition-colors hover:bg-[#F8FBFA]"
                  >
                    <UserRound size={18} className="text-[#171916]" />
                    <div>
                      <div className="text-sm font-black text-[#171916]">个人资料</div>
                      <div className="text-xs text-[#74766F]">账户与安全设置</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={logout}
                    className="mt-3 flex w-full items-center gap-3 rounded-[8px] bg-white px-4 py-3 text-left shadow-[0_18px_42px_-34px_rgba(15,23,42,0.28)] transition-colors hover:bg-red-50"
                  >
                    <LogOut size={18} className="text-red-600" />
                    <div>
                      <div className="text-sm font-black text-red-600">退出登录</div>
                      <div className="text-xs text-[#74766F]">结束当前会话</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={logout}
                    className="mt-2 flex w-full items-center gap-2 px-1 py-1 text-xs font-bold text-[#74766F] hover:text-[#171916]"
                  >
                    <Repeat2 size={14} />
                    切换账户
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            padding: "16px 16px 16px 20px",
          }}
          className="workspace-content"
        >
          <Outlet />
        </div>
      </main>
    </div>
  )
}
