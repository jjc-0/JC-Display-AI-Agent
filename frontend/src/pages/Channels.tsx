import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  MessageCircle,
  Mail,
  Globe,
  MessageSquare,
  Plus,
  Settings,
  ExternalLink,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface Channel {
  id: string
  name: string
  icon: React.ReactNode
  description: string
  color: string
}

const channels: Channel[] = [
  {
    id: "whatsapp",
    name: "WhatsApp Business",
    icon: <MessageCircle size={20} />,
    description: "即时通讯 · 发送产品信息、报价、跟进客户",
    color: "from-[#2D9D72] to-[#0B918C]",
  },
  {
    id: "email-smtp",
    name: "SMTP 邮件",
    icon: <Mail size={20} />,
    description: "批量邮件发送 · 询盘回复 · 营销邮件自动化",
    color: "from-[#0A8BC4] to-[#17211F]",
  },
  {
    id: "facebook-messenger",
    name: "Facebook Messenger",
    icon: <MessageSquare size={20} />,
    description: "社交渠道 · 自动回复客户咨询",
    color: "from-[#0A8BC4] to-[#0B918C]",
  },
  {
    id: "wechat-work",
    name: "企业微信",
    icon: <Globe size={20} />,
    description: "国内渠道 · 企业内部协作与客户沟通",
    color: "from-[#2D9D72] to-[#0B918C]",
  },
]

export default function Channels() {
  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">OMNI CHANNEL</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">消息渠道</h1>
            <p className="mt-2 text-sm text-muted-foreground">连接多渠道消息触点，统一管理客户沟通和外贸跟进。</p>
          </div>
          <Badge variant="secondary" className="text-[11px]">
            <Settings size={11} /> 暂未接入真实配置
          </Badge>
        </div>
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
                <div className={`ai-orbit w-10 h-10 rounded-[8px] bg-gradient-to-br ${channel.color} flex items-center justify-center text-white shadow-[0_22px_42px_-30px_rgba(23,33,31,0.6)]`}>
                  {channel.icon}
                </div>
                <Badge variant="secondary" className="text-[10px]">
                  未接入
                </Badge>
              </div>
              <CardTitle className="mt-3 text-sm">{channel.name}</CardTitle>
              <p className="text-[12px] text-muted-foreground leading-relaxed">{channel.description}</p>
            </CardHeader>
            <CardContent>
              <Button variant="outline" size="sm" className="w-full" disabled>
                <Plus size={13} />
                等待后端渠道接口
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Info */}
      <div className="trade-signal-card p-4 animate-fade-in-up">
        <div className="flex items-center gap-2 mb-2">
          <ExternalLink size={16} className="text-primary" />
          <p className="text-sm font-semibold">渠道配置说明</p>
        </div>
        <p className="text-xs text-muted-foreground leading-relaxed">
          当前页面只保留渠道规划信息。接入真实配置接口后，应由后端返回渠道状态、凭据有效性、最近同步时间和消息处理结果。
        </p>
      </div>
    </div>
  )
}
