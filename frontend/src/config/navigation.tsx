import {
  Bot,
  Cog,
  Database,
  FileText,
  Home,
  Landmark,
  MessageSquare,
  PackageCheck,
  Zap,
} from "lucide-react"
import type { ReactNode } from "react"

export interface NavItem {
  href: string
  label: string
  icon: ReactNode
  badge?: string
  badgeVariant?: "default" | "secondary" | "purple" | "blue" | "success" | "warning" | "outline"
}

export interface NavGroup {
  title: string
  items: NavItem[]
}

export const navigation: NavGroup[] = [
  {
    title: "API",
    items: [
      { href: "/dashboard", label: "仪表盘", icon: <Home size={17} /> },
      { href: "/agent-chat", label: "AI Agent 对话", icon: <MessageSquare size={17} />, badge: "AI", badgeVariant: "purple" },
      { href: "/trade-workspace", label: "外贸作战台", icon: <Landmark size={17} />, badge: "B2B", badgeVariant: "success" },
      { href: "/agent-square", label: "模型广场", icon: <Bot size={17} /> },
      { href: "/agent-execution", label: "使用记录", icon: <Zap size={17} /> },
    ],
  },
  {
    title: "业务",
    items: [
      { href: "/templates", label: "Prompt 迭代", icon: <FileText size={17} /> },
      { href: "/workflow", label: "工作流", icon: <PackageCheck size={17} />, badge: "DAG", badgeVariant: "blue" },
      { href: "/knowledge-base", label: "知识库", icon: <Database size={17} />, badge: "RAG", badgeVariant: "secondary" },
      { href: "/jc-claw", label: "JC claw", icon: <Cog size={17} />, badge: "JC", badgeVariant: "success" },
    ],
  },
]
