import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  MessageCircle,
  Mail,
  Globe,
  MessageSquare,
  Plus,
  Check,
  Settings,
  ExternalLink,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface Channel {
  id: string
  name: string
  icon: React.ReactNode
  description: string
  connected: boolean
  color: string
}

const channels: Channel[] = [
  {
    id: "whatsapp",
    name: "WhatsApp Business",
    icon: <MessageCircle size={20} />,
    description: "即时通讯 · 发送产品信息、报价、跟进客户",
    connected: false,
    color: "from-emerald-500 to-green-600",
  },
  {
    id: "email-smtp",
    name: "SMTP 邮件",
    icon: <Mail size={20} />,
    description: "批量邮件发送 · 询盘回复 · 营销邮件自动化",
    connected: false,
    color: "from-blue-500 to-indigo-600",
  },
  {
    id: "facebook-messenger",
    name: "Facebook Messenger",
    icon: <MessageSquare size={20} />,
    description: "社交渠道 · 自动回复客户咨询",
    connected: false,
    color: "from-sky-500 to-blue-600",
  },
  {
    id: "wechat-work",
    name: "企业微信",
    icon: <Globe size={20} />,
    description: "国内渠道 · 企业内部协作与客户沟通",
    connected: false,
    color: "from-emerald-400 to-teal-500",
  },
]

export default function Channels() {
  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">消息渠道</h1>
          <p className="mt-1 text-sm text-muted-foreground">连接多渠道消息触点 · 统一管理客户沟通</p>
        </div>
        <Badge variant="blue" className="text-[11px]">
          <Settings size={11} /> 渠道管理
        </Badge>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {channels.map((channel, i) => (
          <Card
            key={channel.id}
            className={cn(
              "transition-all duration-300",
              "animate-fade-in-up",
              `stagger-${(i % 4) + 1}`
            )}
          >
            <CardHeader>
              <div className="flex items-start justify-between">
                <div className={`w-10 h-10 rounded-[14px] bg-gradient-to-br ${channel.color} flex items-center justify-center text-white shadow-lg`}>
                  {channel.icon}
                </div>
                <Badge variant={channel.connected ? "success" : "secondary"} className="text-[10px]">
                  {channel.connected ? (
                    <><Check size={10} /> 已连接</>
                  ) : (
                    "未连接"
                  )}
                </Badge>
              </div>
              <CardTitle className="mt-3 text-sm">{channel.name}</CardTitle>
              <p className="text-[12px] text-muted-foreground leading-relaxed">{channel.description}</p>
            </CardHeader>
            <CardContent>
              <Button variant="outline" size="sm" className="w-full">
                <Plus size={13} />
                {channel.connected ? "配置" : "连接渠道"}
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Info */}
      <div className="p-4 rounded-[16px] bg-card border border-border animate-fade-in-up">
        <div className="flex items-center gap-2 mb-2">
          <ExternalLink size={16} className="text-primary" />
          <p className="text-sm font-semibold">渠道配置说明</p>
        </div>
        <p className="text-xs text-muted-foreground leading-relaxed">
          消息渠道模块允许你将 AI Agent 连接到多个通讯通道。配置完成后,Agent 可以自动处理来自不同平台的消息,
          实现统一的客户沟通和自动化回复。支持 WhatsApp、邮件、Messenger、企业微信等主流渠道。
        </p>
      </div>
    </div>
  )
}
