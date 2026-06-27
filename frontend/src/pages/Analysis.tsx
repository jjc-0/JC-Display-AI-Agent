import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  BarChart3,
  Loader2,
  Search,
  TrendingUp,
  Globe,
  AlertTriangle,
  CheckCircle2,
  Zap,
} from "lucide-react"
import api from "@/lib/api"

export default function Analysis() {
  // Market Analysis
  const [productName, setProductName] = useState("")
  const [targetCountry, setTargetCountry] = useState("US")
  const [marketResult, setMarketResult] = useState("")
  const [marketLoading, setMarketLoading] = useState(false)

  // SEO Analysis
  const [seoUrl, setSeoUrl] = useState("")
  const [seoResult, setSeoResult] = useState("")
  const [seoLoading, setSeoLoading] = useState(false)

  // Competitor Analysis
  const [competitor, setCompetitor] = useState("")
  const [competitorResult, setCompetitorResult] = useState("")
  const [competitorLoading, setCompetitorLoading] = useState(false)

  const runMarketAnalysis = async () => {
    if (!productName.trim() || marketLoading) return
    setMarketLoading(true)
    try {
      const { data } = await api.post("/analysis/market", {
        productName: productName.trim(),
        targetCountry,
      })
      setMarketResult(data.result || data.analysis || "")
    } catch {
      setMarketResult("分析失败，请稍后重试。")
    } finally {
      setMarketLoading(false)
    }
  }

  const runSEO = async () => {
    if (!seoUrl.trim() || seoLoading) return
    setSeoLoading(true)
    try {
      const { data } = await api.post("/analysis/seo", { url: seoUrl.trim() })
      setSeoResult(data.result || data.analysis || "")
    } catch {
      setSeoResult("分析失败，请稍后重试。")
    } finally {
      setSeoLoading(false)
    }
  }

  const runCompetitor = async () => {
    if (!competitor.trim() || competitorLoading) return
    setCompetitorLoading(true)
    try {
      const { data } = await api.post("/analysis/competitor", { query: competitor.trim() })
      setCompetitorResult(data.result || data.analysis || "")
    } catch {
      setCompetitorResult("分析失败，请稍后重试。")
    } finally {
      setCompetitorLoading(false)
    }
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">MARKET SIGNALS</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">市场分析</h1>
            <p className="mt-2 text-sm text-muted-foreground">市场机会、SEO 审计与竞品情报，帮助外贸团队快速判断下一步动作。</p>
          </div>
          <Badge variant="purple" className="text-[11px]">
            <BarChart3 size={11} /> AI 分析引擎
          </Badge>
        </div>
      </div>

      <Tabs defaultValue="market" className="w-full">
        <TabsList>
          <TabsTrigger value="market">
            <Globe size={14} /> 市场分析
          </TabsTrigger>
          <TabsTrigger value="seo">
            <Search size={14} /> SEO 审计
          </TabsTrigger>
          <TabsTrigger value="competitor">
            <TrendingUp size={14} /> 竞品分析
          </TabsTrigger>
        </TabsList>

        {/* Market Analysis */}
        <TabsContent value="market">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">市场分析配置</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>产品名称</Label>
                  <Input
                    placeholder="例如：Acrylic Display Stand"
                    value={productName}
                    onChange={(e) => setProductName(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>目标市场</Label>
                  <Select value={targetCountry} onValueChange={setTargetCountry}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="US">美国</SelectItem>
                      <SelectItem value="UK">英国</SelectItem>
                      <SelectItem value="DE">德国</SelectItem>
                      <SelectItem value="JP">日本</SelectItem>
                      <SelectItem value="AU">澳大利亚</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button className="w-full" variant="gradient" onClick={runMarketAnalysis} disabled={marketLoading || !productName.trim()}>
                  {marketLoading ? <Loader2 size={16} className="animate-spin" /> : <Zap size={16} />}
                  {marketLoading ? "分析中..." : "开始分析"}
                </Button>
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle className="text-base">分析报告</CardTitle></CardHeader>
              <CardContent>
                {marketResult ? (
                  <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[300px]">
                    {marketResult}
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[300px]">
                    <Globe size={48} className="mb-4 opacity-20" />
                    <p className="text-sm">输入产品信息获取市场分析</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* SEO */}
        <TabsContent value="seo">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">SEO 审计配置</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>网站 URL</Label>
                  <Input
                    placeholder="https://www.example.com/product"
                    value={seoUrl}
                    onChange={(e) => setSeoUrl(e.target.value)}
                  />
                </div>
                <Button className="w-full" variant="gradient" onClick={runSEO} disabled={seoLoading || !seoUrl.trim()}>
                  {seoLoading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
                  {seoLoading ? "审计中..." : "开始审计"}
                </Button>
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle className="text-base">审计结果</CardTitle></CardHeader>
              <CardContent>
                {seoResult ? (
                  <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[300px]">
                    {seoResult}
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[300px]">
                    <Search size={48} className="mb-4 opacity-20" />
                    <p className="text-sm">输入URL获取SEO审计报告</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Competitor */}
        <TabsContent value="competitor">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">竞品分析配置</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>竞品或关键词</Label>
                  <Input
                    placeholder="例如：display stand competitor XYZ"
                    value={competitor}
                    onChange={(e) => setCompetitor(e.target.value)}
                  />
                </div>
                <Button className="w-full" variant="gradient" onClick={runCompetitor} disabled={competitorLoading || !competitor.trim()}>
                  {competitorLoading ? <Loader2 size={16} className="animate-spin" /> : <TrendingUp size={16} />}
                  {competitorLoading ? "分析中..." : "开始分析"}
                </Button>
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle className="text-base">竞品报告</CardTitle></CardHeader>
              <CardContent>
                {competitorResult ? (
                  <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[300px]">
                    {competitorResult}
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[300px]">
                    <TrendingUp size={48} className="mb-4 opacity-20" />
                    <p className="text-sm">输入竞品信息获取分析</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
