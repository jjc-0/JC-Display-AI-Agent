import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import {
  Star,
  Loader2,
  DollarSign,
  TrendingUp,
  Globe,
  AlertTriangle,
  CheckCircle2,
  Send,
} from "lucide-react"
import api from "@/lib/api"

interface ScoreResult {
  score: number
  intent: string
  buyerStage: string
  quantity: string
  urgency: string
  reason: string
  suggestedReply: string
}

export default function InquiryScoring() {
  const [customerName, setCustomerName] = useState("")
  const [customerCountry, setCustomerCountry] = useState("")
  const [inquiryText, setInquiryText] = useState("")
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ScoreResult | null>(null)

  const handleScore = async () => {
    if (!inquiryText.trim() || loading) return
    setLoading(true)
    setResult(null)
    try {
      const { data } = await api.post("/inquiry/score", {
        customerName: customerName || "匿名客户",
        customerCountry,
        inquiryText: inquiryText.trim(),
      })
      setResult({
        score: data.score ?? 0,
        intent: data.intent || "产品咨询",
        buyerStage: data.buyerStage || "未知",
        quantity: data.quantity || "未明确",
        urgency: data.urgency || "中",
        reason: data.reason || "",
        suggestedReply: data.suggestedReply || "",
      })
    } catch {
      // Keep empty on error
    } finally {
      setLoading(false)
    }
  }

  const demoScore = () => {
    setCustomerName("John Smith")
    setCustomerCountry("United States")
    setInquiryText("Hi, I'm interested in your acrylic display stands for our retail stores. We need about 500 units per month. Can you provide FOB pricing and MOQ? We're looking for a long-term supplier partnership.")
    setResult({
      score: 87,
      intent: "批量采购",
      buyerStage: "决策阶段",
      quantity: "500件/月",
      urgency: "高",
      reason: "该客户来自美国零售市场，有明确采购数量和长期合作意愿。询盘质量高，包含产品规格询问、价格谈判和长期合作意向。建议24小时内回复，提供阶梯报价。",
      suggestedReply: "感谢您的询盘！我们Acrylic Display Stands非常适合零售场景。关于500件/月需求量，我们可以提供阶梯报价。FOB价格和MOQ请见附件产品目录。期待与您建立长期合作。",
    })
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">询盘价值评分</h1>
          <p className="mt-1 text-sm text-muted-foreground">AI 多维度智能评分 · 快速识别高价值询盘</p>
        </div>
        <Button variant="outline" size="sm" onClick={demoScore}>
          <Star size={14} /> 演示数据
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Input Card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">询盘信息</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>客户名称</Label>
              <Input
                placeholder="例如：John Smith"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>客户国家</Label>
              <Input
                placeholder="例如：United States"
                value={customerCountry}
                onChange={(e) => setCustomerCountry(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>询盘内容</Label>
              <Textarea
                placeholder="粘贴客户询盘原文..."
                className="min-h-[160px]"
                value={inquiryText}
                onChange={(e) => setInquiryText(e.target.value)}
              />
            </div>
            <Button
              className="w-full"
              variant="gradient"
              onClick={handleScore}
              disabled={loading || !inquiryText.trim()}
            >
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
              {loading ? "分析中..." : "开始评分"}
            </Button>
          </CardContent>
        </Card>

        {/* Result Card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">评分结果</CardTitle>
          </CardHeader>
          <CardContent>
            {result ? (
              <div className="space-y-5">
                {/* Score */}
                <div className="text-center">
                  <div className="inline-flex items-center gap-2 px-5 py-3 rounded-[16px] bg-gradient-to-r from-violet-50 to-indigo-50 border border-violet-100">
                    <span className="text-4xl font-extrabold text-primary">{result.score}</span>
                    <span className="text-sm text-muted-foreground">/100</span>
                  </div>
                  <div className="flex items-center justify-center gap-2 mt-3">
                    <Badge variant="purple">{result.intent}</Badge>
                    <Badge variant={result.score >= 80 ? "success" : result.score >= 60 ? "warning" : "destructive"}>
                      {result.score >= 80 ? "高价值" : result.score >= 60 ? "中等价值" : "低价值"}
                    </Badge>
                  </div>
                </div>

                <Progress value={result.score} className="h-2" />

                {/* Key Details */}
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <p className="text-[10px] text-muted-foreground uppercase tracking-wider">客户阶段</p>
                    <Badge variant="blue" className="text-[11px]">{result.buyerStage}</Badge>
                  </div>
                  <div className="space-y-1">
                    <p className="text-[10px] text-muted-foreground uppercase tracking-wider">预估数量</p>
                    <Badge variant="secondary" className="text-[11px]">{result.quantity}</Badge>
                  </div>
                  <div className="space-y-1">
                    <p className="text-[10px] text-muted-foreground uppercase tracking-wider">紧急度</p>
                    <Badge variant={result.urgency === "高" ? "destructive" : result.urgency === "中" ? "warning" : "success"} className="text-[11px]">{result.urgency}</Badge>
                  </div>
                  <div className="space-y-1">
                    <p className="text-[10px] text-muted-foreground uppercase tracking-wider">意向类型</p>
                    <Badge variant="secondary" className="text-[11px]">{result.intent}</Badge>
                  </div>
                </div>

                {/* Reason */}
                {result.reason && (
                  <div>
                    <p className="text-xs font-semibold mb-2 flex items-center gap-1">
                      <CheckCircle2 size={13} className="text-emerald-500" /> 评分理由
                    </p>
                    <p className="text-[13px] text-muted-foreground leading-relaxed">{result.reason}</p>
                  </div>
                )}

                {/* Suggested Reply */}
                {result.suggestedReply && (
                  <div className="p-4 rounded-[14px] bg-accent/50 border border-accent">
                    <p className="text-xs font-semibold mb-1 flex items-center gap-1">
                      <Send size={13} className="text-primary" /> 回复建议
                    </p>
                    <p className="text-[13px] leading-relaxed">{result.suggestedReply}</p>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
                <Star size={48} className="mb-4 opacity-20" />
                <p className="text-sm">输入询盘内容后开始评分</p>
                <p className="text-xs mt-1">或点击「演示数据」查看示例</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
