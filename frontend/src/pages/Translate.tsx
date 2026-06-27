import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import {
  Globe,
  Loader2,
  Copy,
  ArrowLeftRight,
  Sparkles,
} from "lucide-react"
import api from "@/lib/api"

const languages = [
  { value: "中文", label: "中文" },
  { value: "英文", label: "English" },
  { value: "日文", label: "日本語" },
  { value: "韩文", label: "한국어" },
  { value: "法文", label: "Français" },
  { value: "德文", label: "Deutsch" },
  { value: "西班牙文", label: "Español" },
  { value: "俄文", label: "Русский" },
  { value: "阿拉伯文", label: "العربية" },
]

export default function Translate() {
  const [text, setText] = useState("")
  const [sourceLang, setSourceLang] = useState("中文")
  const [targetLang, setTargetLang] = useState("英文")
  const [ecommerceLocalization, setEcommerceLocalization] = useState(true)
  const [result, setResult] = useState("")
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  const translate = async () => {
    if (!text.trim() || loading) return
    setLoading(true)
    try {
      const { data } = await api.post("/translate", {
        text: text.trim(),
        sourceLanguage: sourceLang,
        targetLanguage: targetLang,
        ecommerceLocalization,
        context: "B2B export display stands product",
      })
      setResult(data.result || data.translation || "")
    } catch {
      setResult("翻译失败，请稍后重试。")
    } finally {
      setLoading(false)
    }
  }

  const swapLanguages = () => {
    const temp = sourceLang
    setSourceLang(targetLang)
    setTargetLang(temp)
    if (result) {
      setText(result)
      setResult("")
    }
  }

  const copyResult = async () => {
    await navigator.clipboard.writeText(result)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">LOCALIZATION AGENT</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">多语言翻译</h1>
            <p className="mt-2 text-sm text-muted-foreground">高精度 AI 翻译，保留电商术语、产品参数和外贸表达习惯。</p>
          </div>
          <Badge variant="blue" className="text-[11px]">
            <Globe size={11} /> 20+ Languages
          </Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Source */}
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <Select value={sourceLang} onValueChange={setSourceLang}>
                <SelectTrigger className="w-[140px] h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {languages.map((l) => (
                    <SelectItem key={l.value} value={l.value}>{l.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button variant="ghost" size="icon-sm" onClick={swapLanguages} className="h-8 w-8">
                <ArrowLeftRight size={14} />
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <Textarea
              placeholder="输入要翻译的文本..."
              className="min-h-[260px]"
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            <div className="flex items-center justify-between mt-3">
              <div className="flex items-center gap-2">
                <Switch
                  checked={ecommerceLocalization}
                  onCheckedChange={setEcommerceLocalization}
                  id="localization"
                />
                <Label htmlFor="localization" className="text-xs text-muted-foreground cursor-pointer">
                  电商本地化
                </Label>
              </div>
              <Button size="sm" variant="gradient" onClick={translate} disabled={loading || !text.trim()}>
                {loading ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
                翻译
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Target */}
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <Select value={targetLang} onValueChange={setTargetLang}>
                <SelectTrigger className="w-[140px] h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {languages.map((l) => (
                    <SelectItem key={l.value} value={l.value}>{l.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {result && (
                <Button variant="ghost" size="sm" onClick={copyResult}>
                  <Copy size={14} />
                  {copied ? "已复制" : "复制"}
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {result ? (
              <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[260px]">
                {result}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[260px]">
                <Globe size={48} className="mb-4 opacity-20" />
                <p className="text-sm">翻译结果将显示在这里</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
