import { NavLink, Outlet } from "react-router-dom"
import {
  Home,
  MessageSquare,
  Bot,
  Zap,
  Shield,
  Workflow,
  FileText,
  Globe,
  Image,
  Database,
  PenTool,
  Star,
  Link,
  MessageCircle,
  TrendingUp,
  Cog,
  ShoppingBag,
} from "lucide-react"
import { cn } from "@/lib/utils"
import { Badge } from "@/components/ui/badge"

interface NavItem {
  href: string
  label: string
  icon: React.ReactNode
  badge?: string
  badgeVariant?: "default" | "secondary" | "purple" | "blue" | "success" | "warning" | "outline"
}

interface NavGroup {
  title: string
  items: NavItem[]
}

const navigation: NavGroup[] = [
  {
    title: "主菜单",
    items: [
      { href: "/dashboard", label: "数据驾驶舱", icon: <Home size={17} /> },
      { href: "/agent-chat", label: "AI Agent 对话", icon: <MessageSquare size={17} />, badge: "AI", badgeVariant: "purple" },
      { href: "/agent-square", label: "智能体广场", icon: <Bot size={17} /> },
      { href: "/agent-execution", label: "Agent 执行中心", icon: <Zap size={17} /> },
    ],
  },
  {
    title: "智能体",
    items: [
      { href: "/inquiry", label: "询盘价值评分", icon: <Star size={17} /> },
      { href: "/copywriting", label: "文案 & 询盘回复", icon: <PenTool size={17} /> },
      { href: "/translate", label: "多语言翻译", icon: <Globe size={17} /> },
      { href: "/analysis", label: "市场分析", icon: <TrendingUp size={17} /> },
      { href: "/image-recognition", label: "AI 智能识图", icon: <Image size={17} />, badge: "Vision", badgeVariant: "blue" },
      { href: "/product-image", label: "电商产品图", icon: <ShoppingBag size={17} />, badge: "NEW", badgeVariant: "blue" },
      { href: "/knowledge-base", label: "RAG 知识库", icon: <Database size={17} />, badge: "DB", badgeVariant: "secondary" },
    ],
  },
  {
    title: "插件",
    items: [
      { href: "/templates", label: "Prompt 模板", icon: <FileText size={17} /> },
    ],
  },
  {
    title: "自动化",
    items: [
      { href: "/workflow", label: "Workflow Builder", icon: <Workflow size={17} />, badge: "NEW", badgeVariant: "blue" },
      { href: "/channels", label: "消息渠道", icon: <MessageCircle size={17} /> },
      { href: "/api-integration", label: "API 集成", icon: <Link size={17} /> },
      { href: "/auth-center", label: "应用授权中心", icon: <Shield size={17} /> },
      { href: "/jc-claw", label: "JC-CLAW 助手", icon: <Cog size={17} />, badge: "JC-CLAW", badgeVariant: "success" },
    ],
  },
]

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "flex items-center gap-3 px-3 py-2.5 rounded-[12px] text-[13px] font-medium transition-all duration-200",
    isActive
      ? "bg-accent text-accent-foreground"
      : "text-muted-foreground hover:bg-muted hover:text-foreground"
  )

const SIDEBAR_W = 240

export default function Layout() {
  return (
    <div style={{ display: "flex", height: "100vh", overflow: "hidden", background: "#F8F9FB" }}>
      {/* Sidebar */}
      <aside
        style={{
          width: SIDEBAR_W,
          flexShrink: 0,
          display: "flex",
          flexDirection: "column",
          height: "100vh",
          background: "#FFFFFF",
          borderRight: "1px solid #EAECF0",
        }}
      >
        {/* Logo */}
        <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "20px 20px 16px" }}>
          <img src="/logo.png" alt="JC Display" className="w-9 h-9 rounded-[14px] object-cover" />
          <div className="leading-tight">
            <div className="text-[15px] font-bold text-foreground tracking-tight">JC Display AI</div>
            <div className="text-[10px] text-muted-foreground/60 font-medium">B2B Export Agent</div>
          </div>
        </div>

        {/* Navigation - scrollable */}
        <nav
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            paddingLeft: 12,
            paddingRight: 12,
          }}
        >
          {navigation.map((group) => (
            <div key={group.title} className="mb-1">
              <div className="px-3 py-3 pb-2 text-[10px] text-muted-foreground tracking-[1.6px] font-bold uppercase">
                {group.title}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => (
                  <NavLink key={item.href} to={item.href} className={navLinkClass}>
                    <span className="flex-shrink-0">{item.icon}</span>
                    <span className="flex-1 truncate">{item.label}</span>
                    {item.badge && (
                      <Badge variant={item.badgeVariant || "default"} className="text-[10px] px-1.5 py-0 h-5">
                        {item.badge}
                      </Badge>
                    )}
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </nav>

        {/* Footer */}
        <div style={{ padding: 12, borderTop: "1px solid #EAECF0" }}>
          <div className="flex items-center gap-3 px-2 py-2 rounded-[12px]">
            <img src="/logo.png" alt="" className="w-8 h-8 rounded-[10px] object-cover" />
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold text-foreground truncate">JC Display Admin</div>
              <div className="text-[10px] text-muted-foreground truncate">平台管理员</div>
            </div>
            <div className="w-2 h-2 rounded-full bg-emerald-400" />
          </div>
        </div>
      </aside>

      {/* Main Content */}
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
        {/* Page content */}
        <div
          style={{
            flex: "1 1 0",
            minHeight: 0,
            overflowY: "auto",
            padding: "16px 16px 16px 20px",
          }}
        >
          <Outlet />
        </div>

        {/* Copyright Footer */}
        <div
          style={{
            flexShrink: 0,
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            padding: "8px 16px",
            borderTop: "1px solid #EAECF0",
            background: "#FFFFFF",
          }}
        >
          <span className="text-[11px] text-muted-foreground/50">
            &copy; 2026 深圳市杰创包装展示有限公司（Shenzhen JC Display Ltd.）
          </span>
        </div>
      </main>
    </div>
  )
}
