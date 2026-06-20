import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Shield,
  Check,
  ExternalLink,
  Lock,
  Unlock,
  ShoppingCart,
  Globe,
  Mail,
  CalendarDays,
  Truck,
  CreditCard,
  Database,
  RefreshCw,
  AlertCircle,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface AuthApp {
  id: string
  name: string
  icon: React.ReactNode
  category: string
  connected: boolean
  lastSync?: string
  scopes: string[]
  color: string
}

const apps: AuthApp[] = [
  {
    id: "alibaba",
    name: "Alibaba 国际站",
    icon: <ShoppingCart size={20} />,
    category: "电商平台",
    connected: true,
    lastSync: "2 min ago",
    scopes: ["产品读取", "订单查询", "询盘管理"],
    color: "from-orange-500 to-red-500",
  },
  {
    id: "shopify",
    name: "Shopify",
    icon: <ShoppingCart size={20} />,
    category: "电商平台",
    connected: true,
    lastSync: "15 min ago",
    scopes: ["产品管理", "订单管理", "客户数据"],
    color: "from-emerald-500 to-green-600",
  },
  {
    id: "gmail",
    name: "Gmail",
    icon: <Mail size={20} />,
    category: "邮件服务",
    connected: true,
    lastSync: "1 min ago",
    scopes: ["邮件读取", "邮件发送", "标签管理"],
    color: "from-red-500 to-rose-600",
  },
  {
    id: "outlook",
    name: "Microsoft Outlook",
    icon: <Mail size={20} />,
    category: "邮件服务",
    connected: false,
    scopes: ["邮件读取", "邮件发送", "日历访问"],
    color: "from-blue-500 to-indigo-600",
  },
  {
    id: "google-calendar",
    name: "Google Calendar",
    icon: <CalendarDays size={20} />,
    category: "日程管理",
    connected: false,
    scopes: ["日历读取", "事件创建", "提醒管理"],
    color: "from-sky-500 to-blue-600",
  },
  {
    id: "fedex",
    name: "FedEx API",
    icon: <Truck size={20} />,
    category: "物流服务",
    connected: false,
    scopes: ["运单查询", "运费计算", "地址验证"],
    color: "from-purple-500 to-violet-600",
  },
  {
    id: "stripe",
    name: "Stripe",
    icon: <CreditCard size={20} />,
    category: "支付服务",
    connected: true,
    lastSync: "5 min ago",
    scopes: ["交易查询", "退款管理", "客户信息"],
    color: "from-indigo-500 to-blue-700",
  },
  {
    id: "airtable",
    name: "Airtable",
    icon: <Database size={20} />,
    category: "数据管理",
    connected: false,
    scopes: ["数据读取", "数据写入", "结构管理"],
    color: "from-amber-500 to-yellow-600",
  },
]

const categories = ["全部", "电商平台", "邮件服务", "日程管理", "物流服务", "支付服务", "数据管理"]

export default function AuthCenter() {
  const [filter, setFilter] = useState("全部")
  const [authApps, setAuthApps] = useState(apps)

  const filteredApps = authApps.filter(
    (app) => filter === "全部" || app.category === filter
  )

  const toggleConnection = (id: string) => {
    setAuthApps((prev) =>
      prev.map((app) =>
        app.id === id
          ? {
              ...app,
              connected: !app.connected,
              lastSync: !app.connected ? "just now" : undefined,
            }
          : app
      )
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">应用授权中心</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            管理和监控第三方应用 OAuth 授权 · 安全连接你的业务生态
          </p>
        </div>
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-[10px] bg-card border border-border">
          <Shield size={15} className="text-emerald-500" />
          <span className="text-xs font-medium text-muted-foreground">
            {authApps.filter((a) => a.connected).length} / {authApps.length} 已连接
          </span>
        </div>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: "已授权应用", value: authApps.filter((a) => a.connected).length, color: "text-emerald-600" },
          { label: "待连接", value: authApps.filter((a) => !a.connected).length, color: "text-amber-600" },
          { label: "活跃 Token", value: authApps.filter((a) => a.connected).length, color: "text-blue-600" },
          { label: "今日调用", value: "2,847", color: "text-violet-600" },
        ].map((stat) => (
          <div
            key={stat.label}
            className="p-4 rounded-[16px] bg-card border border-border animate-fade-in-up"
          >
            <p className={`text-2xl font-bold ${stat.color}`}>{stat.value}</p>
            <p className="text-[12px] text-muted-foreground mt-0.5">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Category Filter */}
      <div className="flex gap-2 flex-wrap">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilter(cat)}
            className={cn(
              "px-4 py-1.5 rounded-[10px] text-sm font-medium transition-all duration-200",
              filter === cat
                ? "bg-accent text-accent-foreground shadow-sm"
                : "text-muted-foreground hover:bg-muted hover:text-foreground"
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* App Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {filteredApps.map((app, i) => (
          <Card
            key={app.id}
            className={cn(
              "group transition-all duration-300",
              app.connected
                ? "border-emerald-200 bg-gradient-to-b from-white to-emerald-50/30"
                : "hover:border-primary/20",
              "animate-fade-in-up",
              `stagger-${(i % 5) + 1}`
            )}
          >
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between">
                <div
                  className={`w-10 h-10 rounded-[14px] bg-gradient-to-br ${app.color} flex items-center justify-center text-white shadow-lg`}
                >
                  {app.icon}
                </div>
                <div
                  className={cn(
                    "flex items-center gap-1 px-2 py-0.5 rounded-[8px] text-[10px] font-semibold",
                    app.connected
                      ? "bg-emerald-100 text-emerald-700"
                      : "bg-muted text-muted-foreground"
                  )}
                >
                  {app.connected ? (
                    <>
                      <Check size={11} />
                      已连接
                    </>
                  ) : (
                    <>
                      <Lock size={11} />
                      未授权
                    </>
                  )}
                </div>
              </div>
              <CardTitle className="mt-3 text-sm">{app.name}</CardTitle>
              <CardDescription className="text-[11px]">{app.category}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {/* Scopes */}
              <div className="flex flex-wrap gap-1">
                {app.scopes.map((scope) => (
                  <Badge key={scope} variant="secondary" className="text-[9px] px-1.5 py-0 h-4 font-normal">
                    {scope}
                  </Badge>
                ))}
              </div>

              {/* Last Sync */}
              {app.connected && app.lastSync && (
                <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
                  <RefreshCw size={10} className="text-emerald-500" />
                  最后同步: {app.lastSync}
                </div>
              )}

              {/* Action Button */}
              <Button
                variant={app.connected ? "outline" : "gradient"}
                size="sm"
                className="w-full mt-1"
                onClick={() => toggleConnection(app.id)}
              >
                {app.connected ? (
                  <>
                    <Unlock size={13} />
                    断开连接
                  </>
                ) : (
                  <>
                    <ExternalLink size={13} />
                    授权连接
                  </>
                )}
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Security Notice */}
      {filteredApps.length > 0 && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-[16px] bg-card border border-border animate-fade-in-up">
          <AlertCircle size={18} className="text-amber-500 flex-shrink-0" />
          <div>
            <p className="text-xs text-muted-foreground">
              <span className="font-semibold text-foreground">安全说明：</span>
              所有授权通过 OAuth 2.0 协议，Token 加密存储，可随时撤销。
              我们不存储你的密码，仅获取必要权限。
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
