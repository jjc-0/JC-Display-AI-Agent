import {
  Bot,
  ClipboardList,
  Cog,
  Database,
  FileText,
  FolderSearch,
  Home,
  Landmark,
  MessageSquare,
  PackageCheck,
  UsersRound,
  Zap,
} from "lucide-react"
import type { ReactNode } from "react"
import type { UserRole } from "@/config/access"

export interface NavItem {
  href: string
  label: string
  icon: ReactNode
  roles?: UserRole[]
  badge?: string
  badgeVariant?: "default" | "secondary" | "purple" | "blue" | "success" | "warning" | "outline"
}

export interface NavGroup {
  title: string
  items: NavItem[]
}

export const navigation: NavGroup[] = [
  {
    title: "功能",
    items: [
      { href: "/agent-chat", label: "AI Agent 对话", icon: <MessageSquare size={17} />, badge: "AI", badgeVariant: "purple" },
      { href: "/trade-workspace", label: "外贸工作台", icon: <Landmark size={17} />, badge: "B2B", badgeVariant: "success" },
      { href: "/inquiry-review", label: "询盘资料审查", icon: <FolderSearch size={17} />, badge: "2.0", badgeVariant: "success" },
      { href: "/agent-square", label: "智能体广场", icon: <Bot size={17} /> },
      { href: "/templates", label: "Prompt 模板", icon: <FileText size={17} /> },
      { href: "/workflow", label: "工作流", icon: <PackageCheck size={17} />, badge: "DAG", badgeVariant: "blue" },
      { href: "/jc-claw", label: "JC claw", icon: <Cog size={17} />, badge: "JC", badgeVariant: "success" },
    ],
  },
  {
    title: "管理",
    items: [
      { href: "/dashboard", label: "数据仪表盘", icon: <Home size={17} />, roles: ["admin"] },
      { href: "/agent-execution", label: "执行状态", icon: <Zap size={17} />, roles: ["admin"] },
      { href: "/admin/conversations", label: "对话审计", icon: <ClipboardList size={17} />, badge: "Audit", badgeVariant: "warning", roles: ["admin"] },
      { href: "/knowledge-base", label: "知识库", icon: <Database size={17} />, badge: "RAG", badgeVariant: "secondary", roles: ["admin"] },
      { href: "/admin/users", label: "用户管理", icon: <UsersRound size={17} />, badge: "Admin", badgeVariant: "warning", roles: ["admin"] },
    ],
  },
]
