import { useCallback, useEffect, useState } from "react"
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom"
import {
  Check,
  ChevronsLeft,
  ChevronsRight,
  Loader2,
  LogOut,
  MessageSquare,
  Moon,
  Plus,
  Repeat2,
  SunMedium,
  UserRound,
  X,
} from "lucide-react"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { navigation } from "@/config/navigation"
import { AUTH_SPLASH_SEEN_KEY } from "@/features/auth/AuthShell"
import { useThemeMode } from "@/hooks/useThemeMode"
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
const SIDEBAR_COLLAPSED_W = 76
const MAX_SESSIONS = 10

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 rounded-[10px] px-3 py-2.5 text-[13px] font-semibold transition-all duration-200",
    isActive
      ? "bg-[var(--ui-accent)] text-[var(--ui-accent-strong)] ring-1 ring-[var(--ui-border-accent)]"
      : "text-[var(--ui-text-muted)] hover:bg-[var(--ui-muted)] hover:text-[var(--ui-text)]"
  )

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { isDark, toggleTheme } = useThemeMode()
  const [authAccount, setAuthAccount] = useState(() => localStorage.getItem("jc-display-login-account") || "")
  const [authRole, setAuthRole] = useState(() => localStorage.getItem("jc-display-login-role") || "")
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => localStorage.getItem("jc-sidebar-collapsed") === "1")
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

  useEffect(() => {
    localStorage.setItem("jc-sidebar-collapsed", sidebarCollapsed ? "1" : "0")
  }, [sidebarCollapsed])

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
    if (mins < 60) return `${mins} 分钟前`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours} 小时前`
    return `${Math.floor(hours / 24)} 天前`
  }

  const saveTitle = async (sessionId: string) => {
    const nextTitle = editValue.trim()
    if (!nextTitle) return
    try {
      await api.put(`/agent/session/${sessionId}/title`, { title: nextTitle })
      setSessions((prev) => prev.map((item) => item.sessionId === sessionId ? { ...item, title: nextTitle } : item))
    } finally {
      setEditingId(null)
    }
  }

  const logout = () => {
    localStorage.removeItem("jc-auth-token")
    localStorage.removeItem("jc-display-login-role")
    localStorage.removeItem("jc-display-login-account")
    sessionStorage.removeItem(AUTH_SPLASH_SEEN_KEY)
    setAuthAccount("")
    setAuthRole("")
    navigate("/login", { replace: true })
  }

  const renderNavGroup = (group: (typeof navigation)[number]) => (
    <div key={group.title} className="mb-1">
      <div className={cn("px-3 pb-2 pt-3 text-[10px] font-black uppercase tracking-[0.08em] text-[var(--ui-text-muted)]", sidebarCollapsed && "sr-only")}>
        {group.title}
      </div>
      <div className="space-y-0.5">
        {group.items.map((item) => (
          <NavLink key={item.href} to={item.href} className={navLinkClass} title={sidebarCollapsed ? item.label : undefined}>
            <span className="flex-shrink-0">{item.icon}</span>
            <span className={cn("flex-1 truncate", sidebarCollapsed && "hidden")}>{item.label}</span>
            {item.badge && !sidebarCollapsed && (
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
    <div className={cn("workspace-shell", sidebarCollapsed && "workspace-shell--sidebar-collapsed")}>
      <aside className="workspace-sidebar" style={{ width: sidebarCollapsed ? SIDEBAR_COLLAPSED_W : SIDEBAR_W }}>
        <div className={cn("flex items-center gap-3 px-3 pb-5 pt-4", sidebarCollapsed && "justify-center px-2")}>
          <CompanyLogoMark className="h-8 w-10 flex-shrink-0" />
          <div className={cn("leading-tight", sidebarCollapsed && "hidden")}>
            <div className="text-[15px] font-black tracking-tight text-[var(--ui-text)]">JC Display AI</div>
            <div className="text-[10px] font-semibold text-[var(--ui-text-muted)]">Export Console</div>
          </div>
        </div>

        <nav className={cn("min-h-0 flex-1 overflow-y-auto", sidebarCollapsed ? "px-2" : "px-3")}>
          {navigation.filter((group) => group.title === "API").map(renderNavGroup)}

          <div className="mb-1">
            <div className={cn("flex items-center justify-between px-3 pb-2 pt-3", sidebarCollapsed && "justify-center px-2")}>
              <span className={cn("text-[10px] font-black uppercase tracking-[0.08em] text-[var(--ui-text-muted)]", sidebarCollapsed && "sr-only")}>
                历史对话
              </span>
              {!sidebarCollapsed && (
                <Button variant="ghost" size="icon" className="h-5 w-5" onClick={() => navigate("/agent-chat")} title="新对话">
                  <Plus size={13} />
                </Button>
              )}
            </div>

            <div className="space-y-0.5">
              {loadingSessions ? (
                <div className="flex justify-center py-4">
                  <Loader2 size={14} className="animate-spin text-[var(--ui-text-muted)]" />
                </div>
              ) : sessions.length === 0 ? (
                !sidebarCollapsed && <p className="py-4 text-center text-[11px] text-[var(--ui-text-muted)]/60">暂无历史</p>
              ) : sessions.map((session) => (
                <div key={session.sessionId} className="group relative">
                  {editingId === session.sessionId && !sidebarCollapsed ? (
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
                        if (!sidebarCollapsed) {
                          setEditingId(session.sessionId)
                          setEditValue(session.title || "")
                        }
                      }}
                      className={cn(
                        "w-full cursor-pointer rounded-[10px] px-3 py-2 text-left text-[var(--ui-text-muted)] transition-colors hover:bg-[var(--ui-muted)] hover:text-[var(--ui-text)]",
                        sidebarCollapsed && "flex justify-center px-2",
                        currentSessionId === session.sessionId && "bg-[var(--ui-accent)] text-[var(--ui-accent-strong)]"
                      )}
                      title={sidebarCollapsed ? session.title || "未命名" : undefined}
                    >
                      <div className="flex items-center gap-2">
                        <MessageSquare size={12} className="flex-shrink-0" />
                        <span className={cn("flex-1 truncate text-[11px] font-medium", sidebarCollapsed && "hidden")}>{session.title || "未命名"}</span>
                      </div>
                      <div className={cn("mt-0.5 pl-5 text-[10px]", sidebarCollapsed && "hidden")}>
                        {timeAgo(session.updatedAt)}
                      </div>
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          {navigation.filter((group) => group.title !== "API").map(renderNavGroup)}
        </nav>

        <div className="border-t border-[var(--ui-border)] p-3">
          <NavLink to="/profile" className={navLinkClass} title={sidebarCollapsed ? "个人信息" : undefined}>
            <UserRound size={17} />
            <span className={cn("flex-1 truncate", sidebarCollapsed && "hidden")}>个人信息</span>
          </NavLink>
          <button
            type="button"
            onClick={toggleTheme}
            className={cn(
              "mt-1 flex w-full items-center gap-3 rounded-[10px] px-3 py-2.5 text-[13px] font-semibold text-[var(--ui-text-muted)] transition-all duration-200 hover:bg-[var(--ui-muted)] hover:text-[var(--ui-text)]",
              isDark && "bg-[var(--ui-accent)] text-[var(--ui-accent-strong)] ring-1 ring-[var(--ui-border-accent)]",
              sidebarCollapsed && "justify-center px-2"
            )}
            title={isDark ? "切换浅色主题" : "切换黑色主题"}
          >
            {isDark ? <SunMedium size={17} /> : <Moon size={17} />}
            <span className={cn("flex-1 truncate text-left", sidebarCollapsed && "hidden")}>{isDark ? "浅色主题" : "黑色主题"}</span>
          </button>
          <div className={cn("flex items-center gap-2 px-2 py-2 text-[11px] font-semibold text-[var(--ui-text-muted)]", sidebarCollapsed && "justify-center")}>
            <span className="workspace-status-dot" />
            <span className={cn(sidebarCollapsed && "hidden")}>系统在线</span>
          </div>
          <button
            type="button"
            onClick={() => setSidebarCollapsed((value) => !value)}
            className={cn(
              "mt-1 flex w-full items-center gap-3 rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-3 py-2.5 text-[13px] font-black text-[var(--ui-text)] transition-all duration-200 hover:bg-[var(--ui-muted)] active:translate-y-px",
              sidebarCollapsed && "justify-center px-2"
            )}
            title={sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}
          >
            {sidebarCollapsed ? <ChevronsRight size={17} /> : <ChevronsLeft size={17} />}
            <span className={cn("flex-1 truncate text-left", sidebarCollapsed && "hidden")}>{sidebarCollapsed ? "展开侧边栏" : "收起侧边栏"}</span>
          </button>
        </div>
      </aside>

      <main className="flex h-screen min-w-0 flex-1 flex-col overflow-hidden">
        <div className="workspace-top-strip">
          <div className="flex min-w-0 flex-col justify-center">
            <span className="text-[10px] font-bold text-[var(--ui-text-muted)]">用户控制台</span>
            <span className="truncate text-sm font-black text-[var(--ui-text)]">{currentTitle}</span>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden items-center gap-2 rounded-full border border-[var(--ui-border)] bg-[var(--ui-surface)] px-4 py-3 text-sm font-black text-[var(--ui-text)] lg:flex">
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
                className="flex min-h-12 items-center gap-3 rounded-full border border-[var(--ui-border-accent)] bg-[var(--ui-surface)] py-1.5 pl-1.5 pr-4 text-left transition-colors hover:bg-[var(--ui-muted)]"
              >
                <CompanyLogoMark className="h-9 w-11" decorative />
                <div className="hidden leading-tight sm:block">
                  <div className="max-w-[160px] truncate text-sm font-black text-[var(--ui-text)]">{authAccount || "未登录"}</div>
                  <div className="text-[10px] font-semibold text-[var(--ui-text-muted)]">{authRole === "admin" ? "Admin" : "User"}</div>
                </div>
              </button>

              {userMenuOpen && (
                <div className="workspace-user-menu right-0 top-[58px] w-[330px] rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-4 shadow-[var(--ui-shadow-panel)]">
                  <div className="rounded-[14px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3">
                    <div className="flex items-center gap-3">
                      {profile?.avatarUrl ? (
                        <img src={profile.avatarUrl} alt="" className="h-12 w-12 rounded-[14px] object-cover" />
                      ) : (
                        <CompanyLogoMark className="h-12 w-14" decorative />
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-sm font-black text-[var(--ui-text)]">{profile?.email || profile?.username || authAccount || "未登录"}</div>
                        <div className="truncate text-xs font-medium text-[var(--ui-text-muted)]">{profile?.username || authAccount || "未登录"}</div>
                      </div>
                      <Badge variant="success" className="text-[10px]">{authRole === "admin" ? "Admin" : "User"}</Badge>
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-2">
                    <MenuMetric label="账号角色" value={authRole === "admin" ? "管理员" : "普通用户"} />
                    <MenuMetric label="注册时间" value={profileCreated} />
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setUserMenuOpen(false)
                      navigate("/profile")
                    }}
                    className="mt-4 flex w-full items-center gap-3 rounded-[8px] bg-[var(--ui-surface)] px-4 py-3 text-left shadow-[var(--ui-shadow-card)] transition-colors hover:bg-[var(--ui-muted)]"
                  >
                    <UserRound size={18} className="text-[var(--ui-text)]" />
                    <div>
                      <div className="text-sm font-black text-[var(--ui-text)]">个人资料</div>
                      <div className="text-xs text-[var(--ui-text-muted)]">账号与安全设置</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={logout}
                    className="mt-3 flex w-full items-center gap-3 rounded-[8px] bg-[var(--ui-surface)] px-4 py-3 text-left shadow-[var(--ui-shadow-card)] transition-colors hover:bg-red-50"
                  >
                    <LogOut size={18} className="text-red-600" />
                    <div>
                      <div className="text-sm font-black text-red-600">退出登录</div>
                      <div className="text-xs text-[var(--ui-text-muted)]">结束当前会话</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={logout}
                    className="mt-2 flex w-full items-center gap-2 px-1 py-1 text-xs font-bold text-[var(--ui-text-muted)] hover:text-[var(--ui-text)]"
                  >
                    <Repeat2 size={14} />
                    切换账号
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="workspace-content min-h-0 flex-1 overflow-y-auto px-4 py-4 pl-5">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

function MenuMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[14px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-3">
      <div className="text-[11px] font-bold text-[var(--ui-text-muted)]">{label}</div>
      <div className="mt-2 text-sm font-black text-[var(--ui-text)]">{value}</div>
    </div>
  )
}
