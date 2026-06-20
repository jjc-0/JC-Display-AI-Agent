import { useState, useEffect, useCallback } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Progress } from "@/components/ui/progress"
import { api } from "@/lib/api"
import {
  CheckCircle2,
  Clock,
  AlertCircle,
  Loader2,
  Zap,
  Globe,
  FileText,
  Search,
  Cog,
  ExternalLink,
  RefreshCw,
  Inbox,
} from "lucide-react"

interface ActiveAgent {
  name: string
  task: string
  type: string
  sessionId: string
  messageCount: number
  lastActiveAt: string
}

interface ToolCallItem {
  id: string
  tool: string
  status: "completed" | "error" | "running" | "pending"
  startTime: string
  duration?: string
  result?: string
}

interface LogItem {
  id: string
  timestamp: string
  type: "info" | "success" | "error" | "warning"
  agent: string
  message: string
}

interface Stats {
  activeAgents: number
  toolCalls: number
  successRate: number
  avgLatency: string
}

interface ExecutionData {
  activeAgents: ActiveAgent[]
  toolCalls: ToolCallItem[]
  logs: LogItem[]
  stats: Stats
}

const statusConfig = {
  running:  { icon: Loader2,    color: "text-blue-500",        bg: "bg-blue-50 text-blue-700",          label: "运行中", animated: true },
  completed:{ icon: CheckCircle2,color: "text-emerald-500",     bg: "bg-emerald-50 text-emerald-700",    label: "已完成", animated: false },
  pending:  { icon: Clock,      color: "text-muted-foreground", bg: "bg-muted text-muted-foreground",    label: "等待中", animated: false },
  error:    { icon: AlertCircle,color: "text-red-500",          bg: "bg-red-50 text-red-700",            label: "失败",   animated: false },
} as const

const logTypeConfig: Record<string, string> = {
  info:    "bg-slate-50 text-slate-700",
  success: "bg-emerald-50 text-emerald-700",
  error:   "bg-red-50 text-red-700",
  warning: "bg-amber-50 text-amber-700",
}

// 工具名 → 图标映射（在后端返回未知工具时用 Default 图标）
const toolIcons: Record<string, React.ReactNode> = {
  search:         <Search size={14} />,
  translate:      <Globe size={14} />,
  rag_search:     <Search size={14} />,
  knowledge_search: <Search size={14} />,
  scraper:        <ExternalLink size={14} />,
  web_scraper:    <ExternalLink size={14} />,
  currency:       <Cog size={14} />,
  seo:            <Search size={14} />,
  image_analysis: <Cog size={14} />,
}

function getToolIcon(toolName: string): React.ReactNode {
  const key = toolName.toLowerCase().replace(/[^a-z_]/g, "_")
  return toolIcons[key] ?? <Cog size={14} />
}

export default function AgentExecutionCenter() {
  const [data, setData] = useState<ExecutionData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError("")
    try {
      const { data: resp } = await api.get("/agent/execution-status")
      setData(resp as ExecutionData)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "获取执行状态失败")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // 自动轮询（每 15 秒）
  useEffect(() => {
    const timer = setInterval(fetchData, 15000)
    return () => clearInterval(timer)
  }, [fetchData])

  const activeAgents = data?.activeAgents ?? []
  const toolCalls   = data?.toolCalls ?? []
  const logs        = data?.logs ?? []
  const stats       = data?.stats ?? { activeAgents: 0, toolCalls: 0, successRate: 0, avgLatency: "0ms" }

  // ── 加载态 ──
  if (loading && !data) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="animate-spin text-muted-foreground" size={28} />
      </div>
    )
  }

  if (error && !data) {
    return (
      <div className="flex flex-col items-center justify-center h-64 gap-3">
        <AlertCircle size={32} className="text-red-400" />
        <p className="text-sm text-muted-foreground">{error}</p>
        <Button variant="outline" size="sm" onClick={fetchData}>
          <RefreshCw size={14} /> 重试
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Agent 执行中心</h1>
          <p className="mt-1 text-sm text-muted-foreground">实时监控 Agent 任务执行、工具调用与运行日志</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={fetchData} disabled={loading}>
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            刷新
          </Button>
        </div>
      </div>

      {/* Active Agents Status */}
      {activeAgents.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {activeAgents.slice(0, 6).map((agent, i) => (
            <Card key={agent.sessionId} className={`animate-fade-in-up stagger-${i + 1}`}>
              <CardContent className="p-5">
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2.5">
                    <Loader2 size={15} className="text-blue-500 animate-spin" />
                    <div>
                      <p className="text-sm font-semibold">{agent.name}</p>
                      <p className="text-[11px] text-muted-foreground" title={agent.task}>{agent.task}</p>
                    </div>
                  </div>
                  <Badge variant="blue" className="text-[10px]">活跃</Badge>
                </div>
                <Progress value={Math.min(agent.messageCount * 5, 100)} className="h-1.5" />
                <p className="text-[11px] text-muted-foreground mt-2">{agent.messageCount} 条消息</p>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <Card className="border-dashed">
          <CardContent className="py-16 flex flex-col items-center gap-3 text-muted-foreground">
            <Inbox size={36} />
            <p className="text-sm">暂无活跃的 Agent 会话</p>
            <p className="text-[11px]">去 Agent 对话页面发起一次对话吧</p>
          </CardContent>
        </Card>
      )}

      {/* Main Content: Tools + Logs */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Tool Calls Panel */}
        <Card className="lg:col-span-1 animate-fade-in-up stagger-4">
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <Zap size={16} className="text-amber-500" />
              工具调用
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {toolCalls.length > 0 ? (
              <ScrollArea className="h-[360px]">
                <div className="px-4 space-y-1">
                  {toolCalls.map((call) => {
                    const status = statusConfig[call.status]
                    const StatusIcon = status.icon
                    return (
                      <div
                        key={call.id}
                        className="flex items-center gap-3 py-2.5 px-2 rounded-[10px] hover:bg-muted/50 transition-colors"
                      >
                        <div className="w-8 h-8 rounded-[10px] bg-muted flex items-center justify-center flex-shrink-0">
                          {getToolIcon(call.tool)}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <p className="text-sm font-medium truncate">{call.tool}</p>
                            <StatusIcon
                              size={12}
                              className={`${status.color} flex-shrink-0 ${status.animated ? "animate-spin" : ""}`}
                            />
                          </div>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-[10px] text-muted-foreground">{call.startTime}</span>
                            {call.duration && (
                              <span className="text-[10px] text-muted-foreground">{call.duration}</span>
                            )}
                            {call.result && (
                              <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4">
                                {call.result}
                              </Badge>
                            )}
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </ScrollArea>
            ) : (
              <div className="py-12 flex flex-col items-center gap-2 text-muted-foreground">
                <Inbox size={24} />
                <p className="text-[11px]">暂无工具调用记录</p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Execution Logs */}
        <Card className="lg:col-span-2 animate-fade-in-up stagger-5">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                <FileText size={16} className="text-muted-foreground" />
                执行日志
              </CardTitle>
              <Badge variant="outline" className="text-[10px]">{logs.length} 条</Badge>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            {logs.length > 0 ? (
              <ScrollArea className="h-[360px]">
                <div className="px-4 pb-2">
                  {logs.map((log, i) => (
                    <div key={log.id}>
                      {i > 0 && <Separator className="my-1 ml-10" />}
                      <div className="flex items-start gap-3 py-2 px-2 rounded-[8px] hover:bg-muted/30 transition-colors">
                        <div className={`w-7 h-7 rounded-[10px] flex items-center justify-center flex-shrink-0 ${logTypeConfig[log.type] || "bg-slate-50 text-slate-700"}`}>
                          {log.type === "error" ? (
                            <AlertCircle size={13} />
                          ) : log.type === "warning" ? (
                            <AlertCircle size={13} />
                          ) : log.type === "success" ? (
                            <CheckCircle2 size={13} />
                          ) : (
                            <span className="w-1.5 h-1.5 rounded-full bg-current" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 font-normal">
                              {log.agent}
                            </Badge>
                            <span className="text-[10px] text-muted-foreground">{log.timestamp}</span>
                          </div>
                          <p className="text-[13px] mt-0.5 leading-relaxed">{log.message}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </ScrollArea>
            ) : (
              <div className="py-12 flex flex-col items-center gap-2 text-muted-foreground">
                <Inbox size={24} />
                <p className="text-[11px]">近 24 小时暂无执行日志</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 animate-fade-in-up">
        {[
          { label: "活跃 Agent", value: String(stats.activeAgents), color: "text-blue-600" },
          { label: "工具调用", value: String(stats.toolCalls), color: "text-amber-600" },
          { label: "成功率", value: stats.successRate + "%", color: "text-emerald-600" },
          { label: "平均延迟", value: stats.avgLatency, color: "text-violet-600" },
        ].map((stat) => (
          <div key={stat.label} className="text-center p-3 rounded-[16px] bg-card border border-border">
            <p className={`text-xl font-bold ${stat.color}`}>{stat.value}</p>
            <p className="text-[11px] text-muted-foreground mt-0.5">{stat.label}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
