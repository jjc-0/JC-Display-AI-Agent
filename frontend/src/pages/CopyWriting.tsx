import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { PenTool, Loader2, Copy, Sparkles } from "lucide-react"
import api from "@/lib/api"

const platforms = [
  { value: "alibaba", label: "Alibaba 详情页" },
  { value: "amazon", label: "Amazon Listing" },
  { value: "linkedin", label: "LinkedIn 营销帖" },
  { value: "email", label: "邮件询盘回复" },
  { value: "website", label: "官网产品页" },
]

const styles = ["专业且有吸引力", "简洁明了", "营销导向", "技术详细", "SEO 优化"]

export default function CopyWriting() {
  const [productName, setProductName] = useState("")
  const [sellingPoints, setSellingPoints] = useState("")
  const [targetCountry, setTargetCountry] = useState("US")
  const [platform, setPlatform] = useState("alibaba")
  const [style, setStyle] = useState("专业且有吸引力")
  const [language, setLanguage] = useState("English")
  const [result, setResult] = useState("")
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  const generate = async () => {
    if (!productName.trim() || loading) return
    setLoading(true)
    try {
      const { data } = await api.post("/copywriting/generate", {
        productName: productName.trim(),
        sellingPoints: sellingPoints.trim(),
        targetCountry,
        platform,
        style,
        language,
      })
      setResult(data.result || "")
    } catch {
      setResult("生成失败，请稍后重试。")
    } finally {
      setLoading(false)
    }
  }

  const copyText = async () => {
    await navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">EXPORT COPY AGENT</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">文案 & 询盘回复</h1>
            <p className="mt-2 text-sm text-muted-foreground">AI 多平台文案生成，面向 Alibaba、官网和邮件场景快速生成外贸表达。</p>
          </div>
          <Badge variant="purple" className="text-[11px]">
            <Sparkles size={11} /> AI 写作
          </Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Config */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <PenTool size={16} className="text-primary" /> 文案配置
            </CardTitle>
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
              <Label>核心卖点</Label>
              <Textarea
                placeholder="输入产品核心卖点，每行一个..."
                className="min-h-[80px]"
                value={sellingPoints}
                onChange={(e) => setSellingPoints(e.target.value)}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>目标市场</Label>
                <Select value={targetCountry} onValueChange={setTargetCountry}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="US">美国 (US)</SelectItem>
                    <SelectItem value="UK">英国 (UK)</SelectItem>
                    <SelectItem value="DE">德国 (DE)</SelectItem>
                    <SelectItem value="JP">日本 (JP)</SelectItem>
                    <SelectItem value="AU">澳大利亚 (AU)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>输出语言</Label>
                <Select value={language} onValueChange={setLanguage}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="English">English</SelectItem>
                    <SelectItem value="Chinese">中文</SelectItem>
                    <SelectItem value="Japanese">日本語</SelectItem>
                    <SelectItem value="German">Deutsch</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label>目标平台</Label>
              <Select value={platform} onValueChange={setPlatform}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {platforms.map((p) => (
                    <SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>文案风格</Label>
              <div className="flex flex-wrap gap-1.5">
                {styles.map((s) => (
                  <button
                    key={s}
                    onClick={() => setStyle(s)}
                    className={`px-3 py-1 rounded-[8px] text-xs font-medium transition-all ${
                      style === s
                        ? "bg-accent text-accent-foreground"
                        : "bg-muted text-muted-foreground hover:bg-accent/50"
                    }`}
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>

            <Button className="w-full" variant="gradient" onClick={generate} disabled={loading || !productName.trim()}>
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
              {loading ? "生成中..." : "生成文案"}
            </Button>
          </CardContent>
        </Card>

        {/* Result */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base">生成结果</CardTitle>
            {result && (
              <Button variant="ghost" size="sm" onClick={copyText}>
                <Copy size={14} />
                {copied ? "已复制" : "复制"}
              </Button>
            )}
          </CardHeader>
          <CardContent>
            {result ? (
              <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[300px]">
                {result}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[300px]">
                <PenTool size={48} className="mb-4 opacity-20" />
                <p className="text-sm">填写产品信息后生成文案</p>
                <p className="text-xs mt-1">支持多平台、多语言、多风格</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
