import { useState, useEffect, useCallback, useRef } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Bot,
  Star,
  MessageSquare,
  Search,
  Globe,
  PenTool,
  Image as ImageIcon,
  BarChart3,
  Loader2,
  AlertCircle,
  RefreshCw,
} from "lucide-react"
import { useNavigate } from "react-router-dom"
import { cn } from "@/lib/utils"
import { api } from "@/lib/api"

interface AgentDef {
  id: string
  name: string
  description: string
  icon: React.ReactNode
  category: string
  color: string
  gradient: string
  route: string
  /** 对应的后端 operationType */
  typeKey: string
}

/** Agent 静态定义 — 名称、图标、路由等不变的元数据 */
const agentDefs: AgentDef[] = [
  {
    id: "customer-service",
    name: "智能客服 Agent",
    description: "基于产品知识库，自动回答客户询盘与产品咨询，支持多轮对话与上下文理解。",
    icon: <MessageSquare size={22} />,
    category: "对话",
    color: "bg-[#E9F7F5] text-[#087C78]",
    gradient: "from-[#0A8BC4] to-[#0B918C]",
    route: "/agent-chat",
    typeKey: "chat",
  },
  {
    id: "inquiry-scoring",
    name: "询盘评分 Agent",
    description: "多维度智能评分引擎，快速识别高价值询盘，提升销售团队响应效率。",
    icon: <Star size={22} />,
    category: "分析",
    color: "bg-[#EEF7F3] text-[#1F5F53]",
    gradient: "from-[#0A8BC4] to-[#2D9D72]",
    route: "/inquiry",
    typeKey: "inquiry",
  },
  {
    id: "copywriting",
    name: "文案生成 Agent",
    description: "一键生成多平台营销文案、产品描述、询盘回复邮件，支持多语言。",
    icon: <PenTool size={22} />,
    category: "创作",
    color: "bg-emerald-100 text-emerald-600",
    gradient: "from-[#2D9D72] to-[#0B918C]",
    route: "/copywriting",
    typeKey: "copywriting",
  },
  {
    id: "translate",
    name: "多语言翻译 Agent",
    description: "高精度多语言翻译引擎，支持中英日韩德法等 20+ 语种，保留产品术语一致性。",
    icon: <Globe size={22} />,
    category: "翻译",
    color: "bg-amber-100 text-amber-600",
    gradient: "from-[#D89A2B] to-[#2D9D72]",
    route: "/translate",
    typeKey: "translate",
  },
  {
    id: "image-recognition",
    name: "AI 视觉识图 Agent",
    description: "多模态模型驱动，识别产品图片、竞品包装、生产工艺等多场景图像。",
    icon: <ImageIcon size={22} />,
    category: "视觉",
    color: "bg-[#EEF5F1] text-[#273832]",
    gradient: "from-[#17211F] to-[#0A8BC4]",
    route: "/image-recognition",
    typeKey: "image-recognition",
  },
  {
    id: "market-analysis",
    name: "市场分析 Agent",
    description: "实时追踪行业趋势、竞品动态、价格波动，生成专业市场分析报告。",
    icon: <BarChart3 size={22} />,
    category: "分析",
    color: "bg-[#F4F6F5] text-[#74766F]",
    gradient: "from-[#0A8BC4] to-[#17211F]",
    route: "/analysis",
    typeKey: "analysis",
  },
]

const categories = ["全部", "对话", "分析", "创作", "翻译", "视觉"]

const emptyStats: AgentStats = {
  agents: {},
  totalSessions: 0,
  totalRecords: 0,
}

interface AgentStats {
  agents: Record<string, { sessionCount: number }>
  totalSessions: number
  totalRecords: number
}

export default function AgentSquare() {
  const [search, setSearch] = useState("")
  const [activeCategory, setActiveCategory] = useState("全部")
  const [agentStats, setAgentStats] = useState<AgentStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const navigate = useNavigate()

  const abortRef = useRef<AbortController | null>(null)

  const fetchStats = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError("")
    try {
      const { data } = await api.get("/agent/agent-stats", { signal })
      if (signal?.aborted) return
      setAgentStats(data as AgentStats)
    } catch (e: any) {
      if (signal?.aborted) return
      if (e.response?.status === 403) {
        setAgentStats(emptyStats)
        setError("")
        return
      }
      setError(e.response?.data?.message || e.message || "获取数据失败")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    fetchStats(controller.signal)
    return () => controller.abort()
  }, [fetchStats])

  // 合并静态定义 + 实时统计
  const agents = agentDefs.map((def) => {
    const stats = agentStats?.agents?.[def.typeKey]
    const sessionCount = stats?.sessionCount ?? 0
    const totalSessions = agentStats?.totalSessions ?? 0
    const ratio = totalSessions > 0 ? sessionCount / totalSessions : 0
    const share = Math.round(ratio * 1000) / 10
    return { ...def, tasks: sessionCount, share }
  })

  const filteredAgents = agents.filter((agent) => {
    const matchCategory = activeCategory === "全部" || agent.category === activeCategory
    const matchSearch =
      agent.name.toLowerCase().includes(search.toLowerCase()) ||
      agent.description.toLowerCase().includes(search.toLowerCase())
    return matchCategory && matchSearch
  })

  // ── 加载态 ──
  if (loading && !agentStats) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="animate-spin text-muted-foreground" size={28} />
      </div>
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <div className="page-kicker">GLOBAL TRADE AGENTS</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">智能体广场</h1>
            <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
              选择 AI Agent 开始对话，把询盘、翻译、视觉识别和市场分析组织成可复用的外贸工作台。
            {agentStats && (
              <span className="ml-2 text-[11px] text-muted-foreground/60">
                共 {agentStats.totalSessions} 个会话
              </span>
            )}
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <span className="trade-chip">EXPORT READY</span>
              <span className="trade-chip">SECURE</span>
              <span className="trade-chip">RECYCLABLE</span>
            </div>
          </div>
          <div className="flex w-full flex-col gap-2 sm:w-auto sm:min-w-[320px]">
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" onClick={() => fetchStats()} disabled={loading}>
                <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              </Button>
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="搜索 Agent..."
                  className="pl-9 w-full"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>
            </div>
            <div className="trade-signal-card px-3 py-2 text-[11px] text-muted-foreground">
              AI routing keeps trade tasks, product facts, and customer context aligned.
            </div>
          </div>
        </div>
      </div>

      {/* Error banner */}
      {error && agentStats && (
        <div className="flex items-center gap-2 text-[11px] text-amber-600 bg-amber-50 px-3 py-1.5 rounded-[10px]">
          <AlertCircle size={12} />
          {error} — 显示缓存数据
        </div>
      )}

      {/* Category Filter */}
      <div className="flex gap-2 flex-wrap">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setActiveCategory(cat)}
            className={cn(
              "px-4 py-1.5 rounded-[8px] text-sm font-medium transition-all duration-200",
              activeCategory === cat
                ? "bg-accent text-accent-foreground shadow-sm"
                : "text-muted-foreground hover:bg-muted hover:text-foreground"
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Agent Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredAgents.map((agent, i) => (
          <Card
            key={agent.id}
            className={cn(
              "group cursor-pointer hover:-translate-y-0.5 hover:border-[#BFD8CE] transition-all duration-300",
              "animate-fade-in-up",
              i === 0 ? "stagger-1" : i === 1 ? "stagger-2" : i === 2 ? "stagger-3" : i === 3 ? "stagger-4" : i === 4 ? "stagger-5" : "stagger-6"
            )}
            onClick={() => navigate(agent.route)}
          >
            <CardHeader>
              <div className="flex items-start justify-between">
                <div className={`ai-orbit w-11 h-11 rounded-[8px] bg-gradient-to-br ${agent.gradient} flex items-center justify-center text-white shadow-[0_22px_42px_-30px_rgba(23,33,31,0.6)]`}>
                  {agent.icon}
                </div>
                <div className="rounded-full border border-[#E4E8E5] bg-[#F8FBFA] px-2 py-1 text-[11px] font-black text-[#74766F]">
                  {agent.share}%
                </div>
              </div>
              <CardTitle className="mt-3 text-base group-hover:text-primary transition-colors">
                {agent.name}
              </CardTitle>
              <CardDescription className="line-clamp-2 leading-relaxed">
                {agent.description}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex items-center justify-between pt-0">
              <div className="flex items-center gap-3">
                <Badge variant="secondary" className="text-[11px]">{agent.category}</Badge>
                <span className="text-[11px] text-muted-foreground">
                  {agent.tasks.toLocaleString()} 任务
                </span>
              </div>
              <Button
                size="sm"
                variant="ghost"
                className="opacity-0 group-hover:opacity-100 transition-all duration-200 -mr-2"
              >
                <MessageSquare size={14} />
                开始对话
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Empty State */}
      {filteredAgents.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <Bot size={48} className="mb-4 opacity-30" />
          <p className="text-sm">没有找到匹配的 Agent</p>
          <p className="text-xs mt-1">试试调整搜索关键词或筛选条件</p>
        </div>
      )}
    </div>
  )
}
