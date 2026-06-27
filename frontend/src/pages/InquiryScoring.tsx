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

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">INQUIRY SCORING</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">询盘价值评分</h1>
            <p className="mt-2 text-sm text-muted-foreground">AI 多维度智能评分，快速识别高价值询盘、采购阶段和回复优先级。</p>
          </div>
        </div>
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
                  <div className="trade-signal-card inline-flex items-center gap-2 px-5 py-3">
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
                  <div className="trade-signal-card p-4">
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
                <p className="text-xs mt-1">评分结果来自后端模型接口</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
