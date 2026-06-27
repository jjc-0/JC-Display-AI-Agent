import { type ReactNode, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  AlertCircle,
  CalendarDays,
  CreditCard,
  Database,
  Lock,
  Mail,
  Shield,
  ShoppingCart,
  Truck,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface AuthApp {
  id: string
  name: string
  icon: ReactNode
  category: string
  scopes: string[]
  color: string
}

const apps: AuthApp[] = [
  {
    id: "alibaba",
    name: "Alibaba 国际站",
    icon: <ShoppingCart size={20} />,
    category: "电商平台",
    scopes: ["产品读取", "订单查询", "询盘管理"],
    color: "from-[#D89A2B] to-[#2D9D72]",
  },
  {
    id: "shopify",
    name: "Shopify",
    icon: <ShoppingCart size={20} />,
    category: "电商平台",
    scopes: ["产品管理", "订单管理", "客户数据"],
    color: "from-[#2D9D72] to-[#0B918C]",
  },
  {
    id: "gmail",
    name: "Gmail",
    icon: <Mail size={20} />,
    category: "邮件服务",
    scopes: ["邮件读取", "邮件发送", "标签管理"],
    color: "from-[#17211F] to-[#D89A2B]",
  },
  {
    id: "outlook",
    name: "Microsoft Outlook",
    icon: <Mail size={20} />,
    category: "邮件服务",
    scopes: ["邮件读取", "邮件发送", "日历访问"],
    color: "from-[#0A8BC4] to-[#17211F]",
  },
  {
    id: "google-calendar",
    name: "Google Calendar",
    icon: <CalendarDays size={20} />,
    category: "日程管理",
    scopes: ["日历读取", "事件创建", "提醒管理"],
    color: "from-[#0A8BC4] to-[#0B918C]",
  },
  {
    id: "fedex",
    name: "FedEx API",
    icon: <Truck size={20} />,
    category: "物流服务",
    scopes: ["运单查询", "运费计算", "地址验证"],
    color: "from-[#477260] to-[#17211F]",
  },
  {
    id: "stripe",
    name: "Stripe",
    icon: <CreditCard size={20} />,
    category: "支付服务",
    scopes: ["交易查询", "退款管理", "客户信息"],
    color: "from-[#17211F] to-[#0A8BC4]",
  },
  {
    id: "airtable",
    name: "Airtable",
    icon: <Database size={20} />,
    category: "数据管理",
    scopes: ["数据读取", "数据写入", "结构管理"],
    color: "from-[#D89A2B] to-[#0B918C]",
  },
]

const categories = ["全部", "电商平台", "邮件服务", "日程管理", "物流服务", "支付服务", "数据管理"]

export default function AuthCenter() {
  const [filter, setFilter] = useState("全部")
  const filteredApps = apps.filter((app) => filter === "全部" || app.category === filter)

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="page-kicker">SECURE INTEGRATIONS</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">应用授权中心</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              当前系统尚未接入真实 OAuth 授权接口，以下仅作为企业集成规划清单展示。
            </p>
          </div>
          <div className="trade-signal-card flex items-center gap-2 px-3 py-2">
            <Shield size={15} className="text-emerald-500" />
            <span className="text-xs font-medium text-muted-foreground">0 个真实授权连接</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { label: "真实授权", value: 0, color: "text-[#1F5F53]" },
          { label: "规划应用", value: apps.length, color: "text-[#0B918C]" },
          { label: "活跃 Token", value: 0, color: "text-[#74766F]" },
          { label: "可用接口", value: 0, color: "text-[#74766F]" },
        ].map((stat) => (
          <div key={stat.label} className="trade-signal-card p-4 animate-fade-in-up">
            <p className={`text-2xl font-bold ${stat.color}`}>{stat.value}</p>
            <p className="mt-0.5 text-[12px] text-muted-foreground">{stat.label}</p>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilter(cat)}
            className={cn(
              "rounded-[8px] px-4 py-1.5 text-sm font-medium transition-all duration-200",
              filter === cat
                ? "bg-accent text-accent-foreground shadow-sm"
                : "text-muted-foreground hover:bg-muted hover:text-foreground"
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        {filteredApps.map((app, i) => (
          <Card key={app.id} className={cn("group transition-all duration-300 animate-fade-in-up", `stagger-${(i % 5) + 1}`)}>
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between">
                <div className={`ai-orbit flex h-10 w-10 items-center justify-center rounded-[8px] bg-gradient-to-br ${app.color} text-white shadow-[0_22px_42px_-30px_rgba(23,33,31,0.6)]`}>
                  {app.icon}
                </div>
                <div className="flex items-center gap-1 rounded-[8px] bg-muted px-2 py-0.5 text-[10px] font-semibold text-muted-foreground">
                  <Lock size={11} />
                  未接入
                </div>
              </div>
              <CardTitle className="mt-3 text-sm">{app.name}</CardTitle>
              <CardDescription className="text-[11px]">{app.category}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex flex-wrap gap-1">
                {app.scopes.map((scope) => (
                  <Badge key={scope} variant="secondary" className="h-4 px-1.5 py-0 text-[9px] font-normal">
                    {scope}
                  </Badge>
                ))}
              </div>
              <Button variant="outline" size="sm" className="mt-1 w-full" disabled>
                等待后端授权接口
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="trade-signal-card flex items-center gap-3 px-4 py-3 animate-fade-in-up">
        <AlertCircle size={18} className="flex-shrink-0 text-amber-500" />
        <p className="text-xs text-muted-foreground">
          接入真实 OAuth 后，这里应由后端返回授权状态、Token 健康度、最近同步时间和调用量。
        </p>
      </div>
    </div>
  )
}
