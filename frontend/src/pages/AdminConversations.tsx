import { useCallback, useEffect, useMemo, useState } from "react"
import { ClipboardList, Loader2, MessageSquare, RefreshCw, Search, UserRound, Wrench } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

const RECORDS_PER_PAGE = 20

interface AuditUser {
  id: number
  username: string
  role: string
  enabled: boolean
}

interface AuditSession {
  sessionId: string
  title: string
  operationType: string
  messageCount: number
  userId: string
  username: string
  createdAt?: string | null
  updatedAt?: string | null
}

interface AuditRecord {
  id: number
  sessionId: string
  role: string
  operationType: string
  userId: string
  username: string
  content: string
  toolName: string
  toolResult: string
  modelUsed: string
  processingTimeMs?: number | null
  createdAt?: string | null
}

interface AuditStats {
  totalSessions: number
  totalRecords: number
  toolCalls: number
}

export default function AdminConversations() {
  const [users, setUsers] = useState<AuditUser[]>([])
  const [sessions, setSessions] = useState<AuditSession[]>([])
  const [records, setRecords] = useState<AuditRecord[]>([])
  const [stats, setStats] = useState<AuditStats>({ totalSessions: 0, totalRecords: 0, toolCalls: 0 })
  const [selectedUserId, setSelectedUserId] = useState("all")
  const [query, setQuery] = useState("")
  const [recordPage, setRecordPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const loadAudit = useCallback(async (nextUserId = selectedUserId) => {
    setLoading(true)
    setError("")
    try {
      const params = nextUserId && nextUserId !== "all" ? { userId: nextUserId } : undefined
      const { data } = await api.get("/admin/conversations", { params })
      setUsers(data.users || [])
      setSessions(data.sessions || [])
      setRecords(data.records || [])
      setStats({
        totalSessions: data.stats?.totalSessions || 0,
        totalRecords: data.stats?.totalRecords || 0,
        toolCalls: data.stats?.toolCalls || 0,
      })
    } catch (err: any) {
      setError(err.response?.data?.message || "无法加载对话审计数据")
    } finally {
      setLoading(false)
    }
  }, [selectedUserId])

  useEffect(() => {
    loadAudit(selectedUserId)
  }, [loadAudit, selectedUserId])

  useEffect(() => {
    setRecordPage(1)
  }, [query, selectedUserId])

  const filteredRecords = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return records
    return records.filter((record) =>
      [record.username, record.userId, record.content, record.toolName, record.toolResult, record.sessionId, record.operationType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    )
  }, [query, records])

  const selectedUser = users.find((user) => String(user.id) === selectedUserId)
  const recentSessions = sessions.slice(0, 8)
  const totalRecordPages = Math.max(1, Math.ceil(filteredRecords.length / RECORDS_PER_PAGE))
  const currentRecordPage = Math.min(recordPage, totalRecordPages)
  const pagedRecords = filteredRecords.slice(
    (currentRecordPage - 1) * RECORDS_PER_PAGE,
    currentRecordPage * RECORDS_PER_PAGE
  )
  const recordStart = filteredRecords.length === 0 ? 0 : (currentRecordPage - 1) * RECORDS_PER_PAGE + 1
  const recordEnd = Math.min(currentRecordPage * RECORDS_PER_PAGE, filteredRecords.length)

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="console-section-bar">
        <div>
          <div className="page-kicker">CONVERSATION AUDIT</div>
          <h1 className="mt-2 text-xl font-black tracking-tight text-[var(--ui-text)]">员工对话审计</h1>
          <p className="mt-1 text-sm text-[var(--ui-text-muted)]">
            按稳定账号 ID 查看每位员工的会话、请求内容、工具调用和模型响应，用户名只用于展示，不再决定归属。
          </p>
        </div>
        <Button variant="outline" onClick={() => loadAudit(selectedUserId)} disabled={loading}>
          <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <MetricCard icon={<MessageSquare size={18} />} label="会话数" value={stats.totalSessions} />
        <MetricCard icon={<ClipboardList size={18} />} label="请求记录" value={stats.totalRecords} />
        <MetricCard icon={<Wrench size={18} />} label="工具调用" value={stats.toolCalls} />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[340px_minmax(0,1fr)]">
        <Card>
          <CardHeader>
            <CardTitle>员工筛选</CardTitle>
            <CardDescription>筛选条件使用账号 ID，用户改名后历史会话仍然归属到同一个人。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Select value={selectedUserId} onValueChange={setSelectedUserId}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部员工</SelectItem>
                {users.map((user) => (
                  <SelectItem key={user.id} value={String(user.id)}>
                    {user.username}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="space-y-2">
              {users.map((user) => (
                <button
                  key={user.id}
                  type="button"
                  onClick={() => setSelectedUserId(String(user.id))}
                  className={cn(
                    "flex w-full items-center gap-3 rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-3 py-2 text-left transition-colors hover:bg-[var(--ui-muted)]",
                    selectedUserId === String(user.id) && "border-[var(--ui-border-accent)] bg-[var(--ui-accent)]"
                  )}
                >
                  <div className="flex h-8 w-8 items-center justify-center rounded-[8px] bg-[var(--ui-muted)] text-[var(--ui-text)]">
                    <UserRound size={15} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-black text-[var(--ui-text)]">{user.username}</div>
                    <div className="text-[11px] text-[var(--ui-text-muted)]">ID {user.id}</div>
                  </div>
                  <Badge variant={user.role === "admin" ? "warning" : "success"} className="text-[10px]">
                    {user.role === "admin" ? "管理员" : "员工"}
                  </Badge>
                </button>
              ))}
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader className="gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <CardTitle>{selectedUser ? `${selectedUser.username} 的最近会话` : "最近会话"}</CardTitle>
                <CardDescription>每条会话都绑定账号 ID，历史展示会保留当时的用户名快照。</CardDescription>
              </div>
              <div className="relative w-full sm:w-[320px]">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--ui-text-muted)]" />
                <Input value={query} onChange={(event) => setQuery(event.target.value)} className="pl-9" placeholder="搜索请求、工具、员工或会话" />
              </div>
            </CardHeader>
            <CardContent>
              {error && (
                <div className="mb-3 rounded-[8px] border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
                  {error}
                </div>
              )}

              {loading ? (
                <LoadingState />
              ) : recentSessions.length === 0 ? (
                <EmptyState text="暂无会话记录" />
              ) : (
                <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
                  {recentSessions.map((session) => (
                    <div key={session.sessionId} className="rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="truncate text-sm font-black text-[var(--ui-text)]">{session.title || "未命名会话"}</div>
                          <div className="mt-1 text-[11px] text-[var(--ui-text-muted)]">
                            {session.username || "未归属"} · {session.userId ? `ID ${session.userId}` : "无账号 ID"} · {formatDateTime(session.updatedAt)}
                          </div>
                        </div>
                        <Badge variant="secondary" className="text-[10px]">{session.operationType || "chat"}</Badge>
                      </div>
                      <div className="mt-3 text-[11px] font-semibold text-[var(--ui-text-muted)]">
                        {session.messageCount || 0} 条消息 · {session.sessionId.slice(0, 8)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>请求明细</CardTitle>
              <CardDescription>展示最近 300 条消息和工具调用，管理员可追踪每个员工的实际使用情况。</CardDescription>
            </CardHeader>
            <CardContent>
              {loading ? (
                <LoadingState />
              ) : filteredRecords.length === 0 ? (
                <EmptyState text="没有匹配的请求记录" />
              ) : (
                <div className="space-y-3">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>时间</TableHead>
                        <TableHead>员工</TableHead>
                        <TableHead>角色</TableHead>
                        <TableHead>内容</TableHead>
                        <TableHead>工具/模型</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {pagedRecords.map((record) => (
                        <TableRow key={record.id}>
                          <TableCell className="whitespace-nowrap text-xs text-[var(--ui-text-muted)]">{formatDateTime(record.createdAt)}</TableCell>
                          <TableCell className="text-xs font-bold text-[var(--ui-text)]">
                            <div>{record.username || "未归属"}</div>
                            <div className="font-medium text-[var(--ui-text-muted)]">{record.userId ? `ID ${record.userId}` : "无账号 ID"}</div>
                          </TableCell>
                          <TableCell>
                            <Badge variant={record.role === "user" ? "success" : "secondary"} className="text-[10px]">
                              {record.role === "user" ? "员工请求" : "AI 回复"}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-[520px]">
                            <div className="line-clamp-3 whitespace-pre-wrap text-xs leading-relaxed text-[var(--ui-text-soft)]">
                              {record.content || record.toolResult || "空内容"}
                            </div>
                          </TableCell>
                          <TableCell className="text-xs text-[var(--ui-text-muted)]">
                            {record.toolName ? (
                              <span className="font-bold text-[var(--ui-accent-strong)]">{record.toolName}</span>
                            ) : (
                              record.modelUsed || "-"
                            )}
                            {record.processingTimeMs ? <div>{record.processingTimeMs}ms</div> : null}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>

                  <div className="flex flex-col gap-2 border-t border-[var(--ui-border)] pt-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="text-xs font-semibold text-[var(--ui-text-muted)]">
                      第 {recordStart}-{recordEnd} 条，共 {filteredRecords.length} 条
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setRecordPage((page) => Math.max(1, page - 1))}
                        disabled={currentRecordPage <= 1}
                      >
                        上一页
                      </Button>
                      <span className="min-w-16 text-center text-xs font-black text-[var(--ui-text)]">
                        {currentRecordPage} / {totalRecordPages}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setRecordPage((page) => Math.min(totalRecordPages, page + 1))}
                        disabled={currentRecordPage >= totalRecordPages}
                      >
                        下一页
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

function MetricCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: number }) {
  return (
    <Card className="console-metric-card">
      <CardContent className="flex items-center gap-3 p-4">
        <div className="console-metric-icon">{icon}</div>
        <div>
          <div className="text-xs font-bold text-[var(--ui-text-muted)]">{label}</div>
          <div className="mt-1 font-mono text-2xl font-black text-[var(--ui-text)]">{Number(value || 0).toLocaleString()}</div>
        </div>
      </CardContent>
    </Card>
  )
}

function LoadingState() {
  return (
    <div className="flex min-h-[180px] items-center justify-center text-[var(--ui-text-muted)]">
      <Loader2 className="mr-2 animate-spin" size={18} />
      正在加载审计数据
    </div>
  )
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="flex min-h-[160px] flex-col items-center justify-center text-center text-[var(--ui-text-muted)]">
      <ClipboardList size={34} className="mb-3 opacity-45" />
      <p className="text-sm font-bold">{text}</p>
    </div>
  )
}

function formatDateTime(value?: string | null) {
  if (!value) return "暂无记录"
  return new Date(value).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}
