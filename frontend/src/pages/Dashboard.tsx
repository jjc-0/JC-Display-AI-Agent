import { useState, useEffect, useRef } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Database,
  FileText,
  MessageSquare,
  TrendingUp,
  RefreshCw,
  Clock,
  Loader2,
  BarChart3,
  Coins,
  Zap,
} from "lucide-react"
import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts"
import api from "@/lib/api"

interface Stats {
  productCount: number
  docCount: number
  sessionCount: number
  ragEnabled: boolean
}

interface Session {
  sessionId: string
  title: string
  type: string
  modelUsed: string
  updatedAt: string
}

interface TokenStats {
  totalCalls: number
  todayCalls: number
  modelBreakdown: Record<string, number>
  balance: { totalBalance: number; currency: string; isAvailable: boolean }
}

/** 每种 Agent 类型固定一种颜色 */
const AGENT_COLORS: Record<string, string> = {
  chat: "#7C3AED",
  inquiry: "#3B82F6",
  knowledge: "#10B981",
  copywriting: "#F59E0B",
  translate: "#EC4899",
  analysis: "#6366F1",
  "image-recognition": "#8B5CF6",
  auth: "#14B8A6",
}
const FALLBACK_COLOR = "#94A3B8"

/** 模型官方品牌色 */
const MODEL_COLORS: Record<string, string> = {
  "deepseek-chat": "#4D6BFE",
  "deepseek-reasoner": "#4D6BFE",
  deepseek: "#4D6BFE",
  "gpt-4o": "#10A37F",
  "gpt-4": "#10A37F",
  "gpt-4-turbo": "#10A37F",
  "gpt-3.5-turbo": "#10A37F",
  openai: "#10A37F",
  "claude-3": "#D97706",
  "claude-3.5": "#D97706",
  claude: "#D97706",
  "demo-mode": "#94A3B8",
  unknown: "#94A3B8",
}

export default function Dashboard() {
  const [stats, setStats] = useState<Stats>({
    productCount: 0,
    docCount: 0,
    sessionCount: 0,
    ragEnabled: false,
  })
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)
  const [sessionTypeChart, setSessionTypeChart] = useState<{ name: string; count: number }[]>([])
  const [tokenStats, setTokenStats] = useState<TokenStats>({
    totalCalls: 0,
    todayCalls: 0,
    modelBreakdown: {},
    balance: { totalBalance: 0, currency: "CNY", isAvailable: false },
  })

  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const { signal } = controller
    loadAll(signal)
    return () => controller.abort()
  }, [])

  const loadAll = async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      const [statusRes, sessionsRes, usageRes] = await Promise.allSettled([
        api.get("/agent/knowledge/status", { signal }),
        api.get("/agent/sessions", { signal }),
        api.get("/deepseek/usage", { signal }),
      ])

      if (signal?.aborted) return

      if (statusRes.status === "fulfilled") {
        const s = statusRes.value.data
        setStats({
          productCount: s.productCount ?? 0,
          docCount: s.knowledgeDocumentCount ?? 0,
          sessionCount: stats.sessionCount,
          ragEnabled: s.enabled ?? false,
        })
      }

      if (sessionsRes.status === "fulfilled") {
        const data = sessionsRes.value.data
        const sessionList: Session[] = Array.isArray(data?.sessions)
          ? data.sessions
          : Array.isArray(data) ? data : []

        setSessions(sessionList.slice(0, 50))
        setStats((prev) => ({ ...prev, sessionCount: sessionList.length }))

        const typeMap: Record<string, number> = {}
        for (const sess of sessionList) {
          const t = sess.type || "agent"
          typeMap[t] = (typeMap[t] || 0) + 1
        }
        setSessionTypeChart(
          Object.entries(typeMap)
            .sort((a, b) => b[1] - a[1])
            .map(([name, count]) => ({ name, count }))
        )
      }

      if (usageRes.status === "fulfilled") {
        const d = usageRes.value.data
        setTokenStats({
          totalCalls: d.usage?.totalCalls ?? 0,
          todayCalls: d.usage?.todayCalls ?? 0,
          modelBreakdown: d.usage?.modelBreakdown ?? {},
          balance: {
            totalBalance: d.balance?.totalBalance ?? 0,
            currency: d.balance?.currency ?? "CNY",
            isAvailable: d.balance?.isAvailable ?? false,
          },
        })
      }
    } catch {}
    setLoading(false)
  }

  const tokenChart = Object.entries(tokenStats.modelBreakdown)
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count }))

  return (
    <div className="flex flex-col gap-4 animate-fade-in">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">数据驾驶舱</h1>
          <p className="mt-1 text-sm text-muted-foreground">AI Agent 平台运营数据总览</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => loadAll()} disabled={loading}>
          <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          {
            label: "知识库产品",
            value: loading ? "—" : stats.productCount.toLocaleString(),
            icon: Database,
            color: "bg-violet-100 text-violet-600",
            source: "MySQL",
          },
          {
            label: "知识文档",
            value: loading ? "—" : stats.docCount.toLocaleString(),
            icon: FileText,
            color: "bg-blue-100 text-blue-600",
            source: "MySQL",
          },
          {
            label: "API 调用次数",
            value: loading ? "—" : tokenStats.totalCalls.toLocaleString(),
            icon: Zap,
            color: "bg-emerald-100 text-emerald-600",
            source: "今日 " + (loading ? "—" : tokenStats.todayCalls.toLocaleString()) + " 次",
          },
          {
            label: "DeepSeek 余额",
            value: loading ? "—" : `¥${tokenStats.balance.isAvailable ? tokenStats.balance.totalBalance.toFixed(2) : "—"}`,
            icon: Coins,
            color: "bg-amber-100 text-amber-600",
            source: tokenStats.balance.isAvailable ? "实时余额" : "未配置 API Key",
          },
        ].map((stat, i) => (
          <Card key={stat.label} className={`animate-fade-in-up stagger-${i + 1}`}>
            <CardContent className="p-5">
              <div className="flex items-start justify-between">
                <div className="space-y-2 min-w-0">
                  <p className="text-xs text-muted-foreground font-medium">{stat.label}</p>
                  {loading ? (
                    <div className="h-8 w-20 bg-muted rounded-[8px] animate-pulse" />
                  ) : (
                    <p className="text-2xl font-bold tracking-tight truncate">{stat.value}</p>
                  )}
                  <p className="text-[10px] text-muted-foreground">{stat.source}</p>
                </div>
                <div className={`w-10 h-10 rounded-[14px] ${stat.color} flex items-center justify-center flex-shrink-0`}>
                  <stat.icon size={20} />
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Main content — two cards + remaining fills screen */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Left column: Agents distribution (short) + Token usage (fills rest) */}
        <div className="flex flex-col gap-4 min-h-0">
          {/* Agents 会话分布 — short, fixed height */}
          <Card className="flex-shrink-0 animate-fade-in-up overflow-hidden">
            <CardHeader className="pb-2">
              <CardTitle className="text-base">Agents 会话分布</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="flex items-center justify-center h-[180px]">
                  <Loader2 size={24} className="animate-spin text-muted-foreground" />
                </div>
              ) : sessionTypeChart.length > 0 ? (
                <>
                  <ResponsiveContainer width="100%" height={160}>
                    <BarChart data={sessionTypeChart} margin={{ top: 2, right: 4, left: -12, bottom: 2 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#EAECF0" vertical={false} />
                      <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: "#6E7280" }} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: "#6E7280" }} allowDecimals={false} />
                      <Tooltip contentStyle={{ borderRadius: "10px", border: "1px solid #EAECF0", fontSize: "12px" }} />
                      <Bar dataKey="count" radius={[6, 6, 0, 0]} maxBarSize={60}>
                        {sessionTypeChart.map((entry) => (
                          <Cell key={entry.name} fill={AGENT_COLORS[entry.name] || FALLBACK_COLOR} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                  <div className="flex flex-wrap gap-3 mt-2 justify-center">
                    {sessionTypeChart.map((t) => (
                      <div key={t.name} className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                        <div className="w-2 h-2 rounded-full" style={{ backgroundColor: AGENT_COLORS[t.name] || FALLBACK_COLOR }} />
                        {t.name} ({t.count})
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <div className="flex flex-col items-center justify-center py-8 text-muted-foreground h-[180px]">
                  <BarChart3 size={32} className="mb-2 opacity-15" />
                  <p className="text-xs">暂无会话数据</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Token / Model usage — fills remaining space */}
          <Card className="flex-1 min-h-0 flex flex-col animate-fade-in-up overflow-hidden">
            <CardHeader className="pb-2 flex-shrink-0">
              <CardTitle className="text-base flex items-center gap-2">
                <Coins size={15} className="text-amber-500" />
                Token 用量 · 模型分布
              </CardTitle>
            </CardHeader>
            <CardContent className="flex-1 min-h-0 flex flex-col p-0">
              {loading ? (
                <div className="flex-1 flex items-center justify-center">
                  <Loader2 size={24} className="animate-spin text-muted-foreground" />
                </div>
              ) : tokenChart.length > 0 ? (
                <>
                  <div className="flex-1 min-h-0 px-4">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={tokenChart}
                        layout="vertical"
                        margin={{ top: 4, right: 16, left: -4, bottom: 4 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="#EAECF0" horizontal={false} />
                        <XAxis type="number" axisLine={false} tickLine={false} tick={{ fontSize: 10, fill: "#6E7280" }} allowDecimals={false} />
                        <YAxis type="category" dataKey="name" axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: "#6E7280" }} width={100} />
                        <Tooltip
                          contentStyle={{ borderRadius: "10px", border: "1px solid #EAECF0", fontSize: "12px" }}
                          formatter={(value: number) => [`${value.toLocaleString()} 次调用`, "API 调用"]}
                        />
                        <Legend
                          iconType="circle"
                          wrapperStyle={{ fontSize: "10px", paddingTop: 8 }}
                          formatter={(value) => <span style={{ color: "#6E7280" }}>{value}</span>}
                        />
                        <Bar dataKey="count" name="API Calls" radius={[0, 6, 6, 0]} maxBarSize={32}>
                          {tokenChart.map((entry) => (
                            <Cell key={entry.name} fill={MODEL_COLORS[entry.name] || FALLBACK_COLOR} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                  {/* Summary row */}
                  <div className="flex-shrink-0 px-4 pb-3 pt-1 flex items-center gap-4 text-[11px] text-muted-foreground border-t border-border mt-2 pt-3">
                    <span>总调用: <strong className="text-foreground">{tokenStats.totalCalls.toLocaleString()}</strong></span>
                    <span>今日: <strong className="text-foreground">{tokenStats.todayCalls.toLocaleString()}</strong></span>
                    {tokenStats.balance.isAvailable && (
                      <span className="ml-auto">
                        余额: <strong className="text-emerald-600">{tokenStats.balance.totalBalance.toFixed(2)} {tokenStats.balance.currency}</strong>
                      </span>
                    )}
                  </div>
                </>
              ) : (
                <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
                  <Coins size={36} className="mb-3 opacity-15" />
                  <p className="text-xs">暂无 API 调用记录</p>
                  <p className="text-[10px] mt-1">发送消息后模型用量将在此显示</p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right column: Recent Sessions — fills full height */}
        <Card className="flex flex-col animate-fade-in-up overflow-hidden">
          <CardHeader className="pb-2 flex-shrink-0">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                <Clock size={15} />
                最近会话
              </CardTitle>
              <span className="text-[11px] text-muted-foreground">
                {sessions.length > 0 ? `${sessions.length} 条` : ""}
              </span>
            </div>
          </CardHeader>
          <CardContent className="flex-1 min-h-0 p-0">
            {loading ? (
              <div className="h-full flex items-center justify-center">
                <Loader2 size={24} className="animate-spin text-muted-foreground" />
              </div>
            ) : sessions.length > 0 ? (
              <div className="h-full overflow-y-auto">
                <div className="divide-y divide-border">
                  {sessions.map((sess) => (
                    <div
                      key={sess.sessionId}
                      className="flex items-center gap-3 px-5 py-3 hover:bg-muted/40 transition-colors"
                    >
                      <div
                        className="w-2 h-2 rounded-full flex-shrink-0"
                        style={{ backgroundColor: AGENT_COLORS[sess.type] || FALLBACK_COLOR }}
                      />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium truncate">
                          {sess.title || `Session ${sess.sessionId?.slice(0, 8)}`}
                        </p>
                        <div className="flex items-center gap-2 mt-0.5">
                          <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4 font-normal">
                            {sess.type || "chat"}
                          </Badge>
                          {sess.modelUsed && (
                            <span className="text-[10px] text-muted-foreground">{sess.modelUsed}</span>
                          )}
                        </div>
                      </div>
                      {sess.updatedAt && (
                        <span className="text-[10px] text-muted-foreground flex-shrink-0">
                          {sess.updatedAt.slice(0, 10)}
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-muted-foreground">
                <MessageSquare size={36} className="mb-3 opacity-20" />
                <p className="text-sm">暂无会话记录</p>
                <p className="text-xs mt-1">去 AI Agent 对话页面发起对话</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
