import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Link,
  Code,
  Key,
  Copy,
  Check,
  Globe,
  Shield,
  ExternalLink,
  Server,
} from "lucide-react"
import { useState } from "react"

export default function ApiIntegration() {
  const [copied, setCopied] = useState("")

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text)
    setCopied(key)
    setTimeout(() => setCopied(""), 2000)
  }

  const endpoints = [
    { method: "POST", path: "/api/agent/chat", desc: "AI Agent 对话接口", example: `{
  "sessionId": "optional-session-id",
  "message": "How much for acrylic stands?",
  "enableTools": true
}` },
    { method: "POST", path: "/api/translate", desc: "多语言翻译接口", example: `{
  "text": "亚克力展示架",
  "sourceLanguage": "中文",
  "targetLanguage": "英文",
  "ecommerceLocalization": true
}` },
    { method: "POST", path: "/api/inquiry/score", desc: "询盘价值评分", example: `{
  "customerName": "John Smith",
  "customerCountry": "US",
  "inquiryText": "I need 500 acrylic stands per month..."
}` },
    { method: "POST", path: "/api/copywriting/generate", desc: "文案生成", example: `{
  "productName": "Acrylic Display Stand",
  "sellingPoints": "Durable, affordable",
  "platform": "alibaba",
  "language": "English"
}` },
    { method: "POST", path: "/api/analysis/market", desc: "市场分析", example: `{
  "productName": "display stand",
  "targetCountry": "US"
}` },
    { method: "POST", path: "/api/image/recognize", desc: "AI 智能识图 (form-data)", example: `FormData:
  file: [image file]
  prompt: "Describe this product"` },
  ]

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">API 集成</h1>
          <p className="mt-1 text-sm text-muted-foreground">开放 API 接口文档 · 对接你的系统</p>
        </div>
        <Badge variant="blue" className="text-[11px]">
          <Server size={11} /> REST API
        </Badge>
      </div>

      <Tabs defaultValue="docs">
        <TabsList>
          <TabsTrigger value="docs"><Code size={14} /> API 文档</TabsTrigger>
          <TabsTrigger value="auth"><Key size={14} /> 认证配置</TabsTrigger>
        </TabsList>

        <TabsContent value="docs">
          <div className="space-y-4">
            {/* Overview */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Globe size={16} className="text-primary" /> 接口概览
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-[10px] bg-muted/50 text-sm">
                  <Link size={14} className="text-muted-foreground" />
                  <span className="font-mono text-xs">Base URL: http://localhost:8088/api</span>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => copyToClipboard("http://localhost:8088/api", "base")}
                  >
                    {copied === "base" ? <Check size={12} className="text-emerald-500" /> : <Copy size={12} />}
                  </Button>
                </div>
              </CardContent>
            </Card>

            {/* Endpoints */}
            {endpoints.map((ep, i) => (
              <Card key={ep.path} className="animate-fade-in-up" style={{ animationDelay: `${i * 50}ms` }}>
                <CardHeader className="pb-2">
                  <div className="flex items-center gap-2">
                    <Badge variant={ep.method === "POST" ? "purple" : "blue"} className="text-[10px] font-mono">
                      {ep.method}
                    </Badge>
                    <span className="font-mono text-sm">{ep.path}</span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">{ep.desc}</p>
                </CardHeader>
                <CardContent>
                  <div className="relative">
                    <pre className="rounded-[12px] bg-muted p-4 text-xs font-mono overflow-x-auto">
                      {ep.example}
                    </pre>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      className="absolute top-2 right-2"
                      onClick={() => copyToClipboard(ep.example, ep.path)}
                    >
                      {copied === ep.path ? <Check size={12} className="text-emerald-500" /> : <Copy size={12} />}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </TabsContent>

        <TabsContent value="auth">
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Shield size={16} className="text-primary" /> API 认证
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label>API Key</Label>
                <div className="flex gap-2">
                  <Input value="sk-jc-display-agent-xxxxxxxxxxxxx" readOnly className="font-mono text-xs" />
                  <Button variant="outline" size="icon" onClick={() => copyToClipboard("sk-jc-display-agent-xxxxxxxxxxxxx", "apikey")}>
                    {copied === "apikey" ? <Check size={14} className="text-emerald-500" /> : <Copy size={14} />}
                  </Button>
                </div>
                <p className="text-[10px] text-muted-foreground">在请求头中携带: Authorization: Bearer {'<API_KEY>'}</p>
              </div>

              <div className="p-4 rounded-[14px] bg-accent/50 border border-accent">
                <p className="text-xs text-muted-foreground leading-relaxed">
                  <span className="font-semibold text-foreground">安全建议：</span>
                  API Key 请妥善保管，不要在客户端代码中暴露。
                  建议通过后端代理转发请求，将 API Key 保存在服务器端环境变量中。
                  定期更换 API Key 以增强安全性。
                </p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
