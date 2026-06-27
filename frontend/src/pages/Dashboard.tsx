import { useEffect, useRef, useState, type ReactNode } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import {
  ArrowUpRight,
  BarChart3,
  BookOpenText,
  CreditCard,
  Database,
  Gauge,
  MessageSquare,
  PackageSearch,
  RefreshCw,
  Timer,
} from "lucide-react"
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

interface RecentCall {
  id?: number
  time: string
  model: string
  role: string
  operationType?: string
  latency?: number | null
}

interface UsageStats {
  totalCalls: number
  todayCalls: number
  avgLatencyMs?: number | null
  modelBreakdown: Record<string, number>
  recentCalls: RecentCall[]
  balance: { totalBalance: number; currency: string; isAvailable: boolean }
}

const MODEL_COLORS = ["#2F6B5F", "#0B918C", "#516B63", "#93A9B8", "#A8ABA2"]

export default function Dashboard() {
  const [stats, setStats] = useState<Stats>({
    productCount: 0,
    docCount: 0,
    sessionCount: 0,
    ragEnabled: false,
  })
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)
  const [usage, setUsage] = useState<UsageStats>({
    totalCalls: 0,
    todayCalls: 0,
    avgLatencyMs: null,
    modelBreakdown: {},
    recentCalls: [],
    balance: { totalBalance: 0, currency: "CNY", isAvailable: false },
  })

  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    loadAll(controller.signal)
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
        setStats((prev) => ({
          ...prev,
          productCount: s.productCount ?? 0,
          docCount: s.knowledgeDocumentCount ?? 0,
          ragEnabled: Boolean(s.enabled),
        }))
      }

      if (sessionsRes.status === "fulfilled") {
        const data = sessionsRes.value.data
        const sessionList: Session[] = Array.isArray(data?.sessions)
          ? data.sessions
          : Array.isArray(data) ? data : []
        setSessions(sessionList.slice(0, 50))
        setStats((prev) => ({ ...prev, sessionCount: sessionList.length }))
      }

      if (usageRes.status === "fulfilled") {
        const d = usageRes.value.data
        setUsage({
          totalCalls: d.usage?.totalCalls ?? 0,
          todayCalls: d.usage?.todayCalls ?? 0,
          avgLatencyMs: d.usage?.avgLatencyMs ?? null,
          modelBreakdown: d.usage?.modelBreakdown ?? {},
          recentCalls: Array.isArray(d.recentCalls) ? d.recentCalls : [],
          balance: {
            totalBalance: d.balance?.totalBalance ?? 0,
            currency: d.balance?.currency ?? "CNY",
            isAvailable: Boolean(d.balance?.isAvailable),
          },
        })
      }
    } catch {
      // 单个接口失败不阻塞仪表盘，其余接口会通过 Promise.allSettled 正常展示。
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }

  const modelChart = Object.entries(usage.modelBreakdown)
    .reduce<Record<string, number>>((acc, [, count]) => {
      acc["JC agent"] = (acc["JC agent"] || 0) + count
      return acc
    }, {})

  const modelRows = Object.entries(modelChart)
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count }))

  const totalModelCalls = modelRows.reduce((sum, item) => sum + item.count, 0)
  const balanceText = usage.balance.isAvailable
    ? `${currencyPrefix(usage.balance.currency)}${usage.balance.totalBalance.toFixed(2)}`
    : "未配置"

  const metricCards = [
    { label: "账户余额", hint: "来自模型服务商余额接口", value: balanceText, icon: CreditCard, tone: "gold" },
    { label: "今日请求", hint: "今日写入数据库的调用记录", value: usage.todayCalls.toLocaleString(), icon: ArrowUpRight, tone: "green" },
    { label: "累计请求", hint: "conversation_messages 记录总数", value: usage.totalCalls.toLocaleString(), icon: BarChart3, tone: "slate" },
    { label: "平均延迟", hint: "按已记录处理耗时计算", value: formatLatency(usage.avgLatencyMs), icon: Timer, tone: "slate" },
    { label: "产品数量", hint: "RAG 产品库真实商品数", value: stats.productCount.toLocaleString(), icon: PackageSearch, tone: "green" },
    { label: "知识文档", hint: "已纳入知识库的文档数", value: stats.docCount.toLocaleString(), icon: BookOpenText, tone: "slate" },
    { label: "会话数量", hint: "当前系统记录的会话数", value: stats.sessionCount.toLocaleString(), icon: MessageSquare, tone: "slate" },
    { label: "RAG 状态", hint: "知识检索服务开关状态", value: stats.ragEnabled ? "启用" : "未启用", icon: Gauge, tone: stats.ragEnabled ? "green" : "gold" },
  ]

  return (
    <div className="flex flex-col gap-3 animate-fade-in">
      <div className="console-hero">
        <div className="flex min-w-0 flex-1 flex-col justify-center px-4 py-4">
          <div className="text-[10px] font-black uppercase tracking-[0.08em] text-[#74766F]">CONSOLE</div>
          <h1 className="mt-1 text-[28px] font-black leading-none tracking-tight text-[#171916]">Developer</h1>
          <p className="mt-2 text-[12px] font-medium text-[#74766F]">只展示已接入后端或数据库可验证的数据。</p>
        </div>
        <div className="console-hero-stat">
          <span>今日请求</span>
          <strong>{usage.todayCalls.toLocaleString()}</strong>
          <small>累计 {usage.totalCalls.toLocaleString()} 次</small>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
        {metricCards.map((stat, i) => (
          <Card key={stat.label} className={`console-metric-card console-metric-card--${stat.tone} animate-fade-in-up stagger-${(i % 6) + 1}`}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-[12px] font-black text-[#171916]">{stat.label}</p>
                  <p className="mt-1 text-[11px] font-medium text-[#74766F]">{stat.hint}</p>
                </div>
                <div className="console-metric-icon">
                  <stat.icon size={15} />
                </div>
              </div>
              {loading ? (
                <div className="mt-4 h-8 w-24 rounded-[8px] bg-[#F4F6F5] animate-pulse" />
              ) : (
                <p className="mt-4 font-mono text-[30px] font-black leading-none tracking-tight text-[#171916]">{stat.value}</p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="console-section-bar">
        <div>
          <div className="text-[10px] font-black uppercase tracking-[0.08em] text-[#74766F]">REAL DATA</div>
          <h2 className="text-[17px] font-black leading-tight text-[#171916]">调用与知识库概览</h2>
          <p className="text-[12px] text-[#74766F]">已移除无真实来源的费用、Token 趋势和本地演示数值。</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => loadAll()} disabled={loading}>
          <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Card className="overflow-hidden">
          <CardHeader className="border-b border-[#E4E8E5] pb-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-[11px] font-black uppercase tracking-[0.08em] text-[#74766F]">Traffic</p>
                <CardTitle>最近调用</CardTitle>
              </div>
              <span className="text-[11px] font-bold text-[#74766F]">{usage.recentCalls.length} 条</span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {usage.recentCalls.length > 0 ? (
              <div className="divide-y divide-[#E4E8E5]">
                {usage.recentCalls.slice(0, 8).map((call, i) => (
                  <div key={call.id || `${call.time}-${i}`} className="grid grid-cols-[minmax(0,1fr)_100px_90px] items-center gap-3 px-4 py-3 text-[12px]">
                    <div className="min-w-0">
                      <p className="truncate font-black text-[#171916]">{call.operationType || call.role || "agent"}</p>
                      <p className="text-[11px] text-[#74766F]">{formatTime(call.time)}</p>
                    </div>
                    <span className="text-right font-semibold text-[#74766F]">JC agent</span>
                    <span className="text-right font-mono font-black text-[#171916]">{formatLatency(call.latency)}</span>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState icon={<MessageSquare size={28} />} title="暂无调用记录" text="当 Agent 产生对话或工具调用后，这里会显示数据库中的真实记录。" />
            )}
          </CardContent>
        </Card>

        <Card className="overflow-hidden">
          <CardHeader className="border-b border-[#E4E8E5] pb-3">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-[11px] font-bold text-[#74766F]">Agent Mix</p>
                <CardTitle>调用占比</CardTitle>
              </div>
              <span className="text-[11px] font-black text-[#171916]">{modelRows.length} 类</span>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {modelRows.length > 0 ? (
              <>
                <div className="grid grid-cols-2 border-b border-[#E4E8E5] bg-[#F7F9F8]">
                  <div className="border-r border-[#E4E8E5] px-4 py-3">
                    <p className="text-[11px] text-[#74766F]">请求总量</p>
                    <p className="font-mono text-[19px] font-black text-[#171916]">{totalModelCalls.toLocaleString()}</p>
                  </div>
                  <div className="px-4 py-3">
                    <p className="text-[11px] text-[#74766F]">当前标识</p>
                    <p className="font-mono text-[19px] font-black text-[#171916]">JC agent</p>
                  </div>
                </div>
                {modelRows.map((model, i) => {
                  const percent = totalModelCalls > 0 ? Math.round(model.count / totalModelCalls * 1000) / 10 : 0
                  return (
                    <div key={model.name} className="border-b border-[#E4E8E5] px-4 py-3 last:border-b-0">
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <span className="flex h-5 w-5 items-center justify-center rounded-full border border-[#D3DAD6] text-[10px] font-black text-[#74766F]">{String(i + 1).padStart(2, "0")}</span>
                          <div className="min-w-0">
                            <p className="truncate text-[13px] font-black text-[#171916]">{model.name}</p>
                            <p className="text-[11px] text-[#74766F]">{model.count.toLocaleString()} 请求</p>
                          </div>
                        </div>
                        <span className="font-mono text-[11px] font-black text-[#171916]">{percent}%</span>
                      </div>
                      <div className="console-progress mt-2">
                        <span style={{ width: `${percent}%`, background: MODEL_COLORS[i % MODEL_COLORS.length] }} />
                      </div>
                    </div>
                  )
                })}
              </>
            ) : (
              <EmptyState icon={<Database size={28} />} title="暂无占比数据" text="模型调用记录入库后会自动生成真实占比。" />
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
        <Card className="overflow-hidden">
          <CardHeader className="border-b border-[#E4E8E5] pb-3">
            <div>
              <p className="text-[11px] font-black uppercase tracking-[0.08em] text-[#74766F]">RAG</p>
              <CardTitle>知识库状态</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-3">
            <StatusBlock label="产品库" value={`${stats.productCount.toLocaleString()} 个产品`} />
            <StatusBlock label="文档库" value={`${stats.docCount.toLocaleString()} 份文档`} />
            <StatusBlock label="检索状态" value={stats.ragEnabled ? "已启用" : "未启用"} />
          </CardContent>
        </Card>

        <Card className="overflow-hidden">
          <CardHeader className="border-b border-[#E4E8E5] pb-3">
            <div>
              <p className="text-[11px] font-black uppercase tracking-[0.08em] text-[#74766F]">Sessions</p>
              <CardTitle>最近会话</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {sessions.length > 0 ? (
              <div className="divide-y divide-[#E4E8E5]">
                {sessions.slice(0, 5).map((session) => (
                  <div key={session.sessionId} className="grid grid-cols-[minmax(0,1fr)_92px] gap-3 px-4 py-3 text-[12px]">
                    <div className="min-w-0">
                      <p className="truncate font-black text-[#171916]">{session.title || "未命名会话"}</p>
                      <p className="text-[11px] text-[#74766F]">{session.type || "agent"}</p>
                    </div>
                    <span className="text-right text-[11px] text-[#74766F]">{formatTime(session.updatedAt)}</span>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState icon={<MessageSquare size={28} />} title="暂无会话" text="新建对话后会在这里显示真实会话记录。" />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function StatusBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[12px] border border-[#E4E8E5] bg-[#F8FBFA] p-4">
      <p className="text-[11px] font-bold text-[#74766F]">{label}</p>
      <p className="mt-2 font-mono text-[16px] font-black text-[#171916]">{value}</p>
    </div>
  )
}

function EmptyState({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center text-[#74766F]">
      <div className="mb-3 opacity-25">{icon}</div>
      <p className="text-sm font-black text-[#171916]">{title}</p>
      <p className="mt-2 max-w-[320px] text-xs leading-relaxed">{text}</p>
    </div>
  )
}

function currencyPrefix(currency: string) {
  if (currency === "CNY") return "¥"
  if (currency === "USD") return "$"
  return `${currency} `
}

function formatLatency(value?: number | null) {
  if (value == null) return "—"
  if (value < 1000) return `${value}ms`
  return `${(value / 1000).toFixed(2)}s`
}

function formatTime(value?: string) {
  if (!value) return "—"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}
