import { useCallback, useEffect, useMemo, useState } from "react"
import {
  AlertTriangle,
  KeyRound,
  Loader2,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  UserCog,
  UsersRound,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import api from "@/lib/api"

type Role = "admin" | "user"

interface AdminStats {
  totalUsers: number
  adminUsers: number
  enabledUsers: number
  disabledUsers: number
  totalSessions: number
  totalRecords: number
  todayRecords: number
}

interface AdminUser {
  id: number
  username: string
  role: Role
  email?: string
  companyName?: string
  department?: string
  jobTitle?: string
  phone?: string
  enabled: boolean
  createdAt?: string | null
  updatedAt?: string | null
  lastLoginAt?: string | null
}

const emptyStats: AdminStats = {
  totalUsers: 0,
  adminUsers: 0,
  enabledUsers: 0,
  disabledUsers: 0,
  totalSessions: 0,
  totalRecords: 0,
  todayRecords: 0,
}

export default function AdminUsers() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [stats, setStats] = useState<AdminStats>(emptyStats)
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(true)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [error, setError] = useState("")
  const [passwordDialog, setPasswordDialog] = useState<AdminUser | null>(null)
  const [newPassword, setNewPassword] = useState("")

  const loadUsers = useCallback(async () => {
    setLoading(true)
    setError("")
    try {
      const { data } = await api.get("/admin/users")
      setUsers(data.users || [])
      setStats({ ...emptyStats, ...(data.stats || {}) })
    } catch (err: any) {
      setError(err.response?.data?.message || "无法加载用户管理数据")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadUsers()
  }, [loadUsers])

  const filteredUsers = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return users
    return users.filter((user) =>
      [user.username, user.email, user.companyName, user.department, user.jobTitle, String(user.id)]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    )
  }, [query, users])

  const updateUser = async (user: AdminUser, patch: Partial<AdminUser> & { password?: string }) => {
    setSavingId(user.id)
    setError("")
    try {
      const { data } = await api.put(`/admin/users/${user.id}`, patch)
      setUsers((prev) => prev.map((item) => (item.id === user.id ? { ...item, ...data } : item)))
      await refreshStats()
    } catch (err: any) {
      setError(err.response?.data?.message || "保存失败，请稍后重试")
    } finally {
      setSavingId(null)
    }
  }

  const refreshStats = async () => {
    try {
      const { data } = await api.get("/admin/overview")
      setStats({ ...emptyStats, ...(data || {}) })
    } catch {
      // 用户列表仍然可用时，不打断当前操作。
    }
  }

  const deleteUser = async (user: AdminUser) => {
    if (!window.confirm(`确定删除用户「${user.username}」吗？该操作不可恢复。`)) return
    setSavingId(user.id)
    setError("")
    try {
      await api.delete(`/admin/users/${user.id}`)
      setUsers((prev) => prev.filter((item) => item.id !== user.id))
      await refreshStats()
    } catch (err: any) {
      setError(err.response?.data?.message || "删除失败，请稍后重试")
    } finally {
      setSavingId(null)
    }
  }

  const submitPassword = async () => {
    if (!passwordDialog) return
    await updateUser(passwordDialog, { password: newPassword })
    setPasswordDialog(null)
    setNewPassword("")
  }

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="console-section-bar">
        <div>
          <div className="page-kicker">ACCESS CONTROL</div>
          <h1 className="mt-2 text-xl font-black tracking-tight text-[var(--ui-text)]">用户与权限管理</h1>
          <p className="mt-1 text-sm text-[var(--ui-text-muted)]">
            管理账号状态、角色权限和登录可用性。普通用户只保留功能入口与个人信息，管理员可查看全局数据。
          </p>
        </div>
        <Button variant="outline" onClick={loadUsers} disabled={loading}>
          <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={<UsersRound size={18} />} label="用户总数" value={stats.totalUsers} sub={`${stats.enabledUsers} 个启用`} />
        <MetricCard icon={<ShieldCheck size={18} />} label="管理员" value={stats.adminUsers} sub="拥有全局数据权限" />
        <MetricCard icon={<AlertTriangle size={18} />} label="已禁用账号" value={stats.disabledUsers} sub="无法登录系统" />
        <MetricCard icon={<UserCog size={18} />} label="今日消息" value={stats.todayRecords} sub={`${stats.totalRecords} 条总记录`} />
      </div>

      <Card>
        <CardHeader className="gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <CardTitle>账号列表</CardTitle>
            <CardDescription>角色变更和账号禁用会立即在后端生效。</CardDescription>
          </div>
          <div className="relative w-full sm:w-[320px]">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--ui-text-muted)]" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="pl-9"
              placeholder="搜索用户名、邮箱、公司或 ID"
            />
          </div>
        </CardHeader>
        <CardContent>
          {error && (
            <div className="mb-3 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
              {error}
            </div>
          )}

          {loading ? (
            <div className="flex min-h-[260px] items-center justify-center text-[var(--ui-text-muted)]">
              <Loader2 className="mr-2 animate-spin" size={18} />
              正在加载用户数据
            </div>
          ) : filteredUsers.length === 0 ? (
            <div className="flex min-h-[220px] flex-col items-center justify-center text-center text-[var(--ui-text-muted)]">
              <UsersRound size={36} className="mb-3 opacity-45" />
              <p className="text-sm font-bold">没有匹配的用户</p>
              <p className="mt-1 text-xs">调整搜索关键词后再试。</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>用户</TableHead>
                  <TableHead>角色</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>最近登录</TableHead>
                  <TableHead>注册时间</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredUsers.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex min-w-[220px] items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-[10px] border border-[var(--ui-border-accent)] bg-[var(--ui-accent)] text-sm font-black text-[var(--ui-accent-strong)]">
                          {user.username.slice(0, 1).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <div className="truncate font-black text-[var(--ui-text)]">{user.username}</div>
                          <div className="truncate text-xs text-[var(--ui-text-muted)]">ID {user.id}{user.email ? ` · ${user.email}` : ""}</div>
                          {(user.companyName || user.department || user.jobTitle) && (
                            <div className="mt-1 truncate text-[11px] text-[var(--ui-text-muted)]">
                              {[user.companyName, user.department, user.jobTitle].filter(Boolean).join(" / ")}
                            </div>
                          )}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Select
                        value={user.role}
                        onValueChange={(value) => updateUser(user, { role: value as Role })}
                        disabled={savingId === user.id}
                      >
                        <SelectTrigger className="h-8 w-[112px] rounded-[8px] px-3 text-xs">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="user">普通用户</SelectItem>
                          <SelectItem value="admin">管理员</SelectItem>
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Switch
                          checked={user.enabled}
                          onCheckedChange={(checked) => updateUser(user, { enabled: checked })}
                          disabled={savingId === user.id}
                        />
                        <Badge variant={user.enabled ? "success" : "warning"} className="text-[10px]">
                          {user.enabled ? "启用" : "禁用"}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-[var(--ui-text-muted)]">{formatDateTime(user.lastLoginAt)}</TableCell>
                    <TableCell className="text-xs text-[var(--ui-text-muted)]">{formatDate(user.createdAt)}</TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="outline"
                          size="icon-sm"
                          onClick={() => {
                            setPasswordDialog(user)
                            setNewPassword("")
                          }}
                          title="重置密码"
                          disabled={savingId === user.id}
                        >
                          <KeyRound size={14} />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon-sm"
                          className="text-red-600 hover:text-red-700"
                          onClick={() => deleteUser(user)}
                          title="删除账号"
                          disabled={savingId === user.id}
                        >
                          {savingId === user.id ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={Boolean(passwordDialog)} onOpenChange={(open) => !open && setPasswordDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>重置用户密码</DialogTitle>
            <DialogDescription>
              为 {passwordDialog?.username} 设置新密码，至少 6 位。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <label className="text-sm font-bold text-[var(--ui-text)]" htmlFor="admin-new-password">新密码</label>
            <Input
              id="admin-new-password"
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              placeholder="输入新密码"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPasswordDialog(null)}>取消</Button>
            <Button onClick={submitPassword} disabled={newPassword.length < 6 || savingId === passwordDialog?.id}>
              {savingId === passwordDialog?.id && <Loader2 size={15} className="animate-spin" />}
              保存密码
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function MetricCard({ icon, label, value, sub }: { icon: React.ReactNode; label: string; value: number; sub: string }) {
  return (
    <Card className="console-metric-card">
      <CardContent className="flex items-center gap-3 p-4">
        <div className="console-metric-icon">{icon}</div>
        <div>
          <div className="text-xs font-bold text-[var(--ui-text-muted)]">{label}</div>
          <div className="mt-1 font-mono text-2xl font-black text-[var(--ui-text)]">{Number(value || 0).toLocaleString()}</div>
          <div className="text-[11px] font-semibold text-[var(--ui-text-muted)]">{sub}</div>
        </div>
      </CardContent>
    </Card>
  )
}

function formatDateTime(value?: string | null) {
  if (!value) return "暂无记录"
  return new Date(value).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

function formatDate(value?: string | null) {
  if (!value) return "暂无记录"
  return new Date(value).toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
}
