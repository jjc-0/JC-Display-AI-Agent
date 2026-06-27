import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sparkles, Loader2, Wand2, Download, ImagePlus, Paintbrush } from "lucide-react"
import api from "@/lib/api"

const sizes = ["1024*1024", "720*1280", "1280*720"]

export default function ProductImage() {
  const [prompt, setPrompt] = useState("")
  const [size, setSize] = useState("1024*1024")
  const [loading, setLoading] = useState(false)
  const [images, setImages] = useState<string[]>([])
  const [error, setError] = useState("")

  const handleGenerate = async () => {
    if (!prompt.trim() || loading) return
    setLoading(true)
    setError("")
    setImages([])
    try {
      const { data } = await api.post("/product-image/generate", {
        prompt: prompt.trim(),
        size,
      })
      if (data.success) {
        setImages(data.images || [])
      } else {
        setError(data.error || "生成失败")
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "请求失败，请稍后重试")
    } finally {
      setLoading(false)
    }
  }

  const downloadImage = async (src: string, index: number) => {
    try {
      const res = await fetch(src)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `product-image-${index + 1}.png`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      window.open(src, "_blank")
    }
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">PRODUCT VISUALS</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">电商产品图生成</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              输入产品描述，AI 自动生成适合展示架、包装和外贸详情页的产品图。
            </p>
          </div>
          <Badge variant="success" className="text-[11px]">
            <Wand2 size={11} /> AI Image Gen
          </Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Input Area */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <Paintbrush size={16} />
              产品图描述
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Textarea
              placeholder="描述你想要生成的产品图，例如：&#10;&#10;一款白色纸展示架，用于超市陈列零食产品，简洁现代的设计风格，白色背景，专业产品摄影，高分辨率...&#10;&#10;A white cardboard display stand for supermarket snack products, clean modern design, white background, professional product photography..."
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              className="min-h-[200px] resize-none text-sm"
            />

            <div className="space-y-1.5">
              <span className="text-[11px] text-muted-foreground">图片尺寸</span>
              <Select value={size} onValueChange={setSize}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {sizes.map((s) => (
                    <SelectItem key={s} value={s}>{s}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button
              className="w-full"
              onClick={handleGenerate}
              disabled={!prompt.trim() || loading}
              variant="gradient"
            >
              {loading ? (
                <><Loader2 size={15} className="animate-spin mr-2" /> AI 生成中...</>
              ) : (
                <><Sparkles size={15} className="mr-2" /> 生成产品图</>
              )}
            </Button>

            {error && (
              <div className="p-3 rounded-[8px] bg-destructive/10 text-destructive text-xs">{error}</div>
            )}
          </CardContent>
        </Card>

        {/* Result Area */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <ImagePlus size={16} />
              生成结果
            </CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <Loader2 size={32} className="animate-spin mb-4 opacity-30" />
                <p className="text-sm">AI 正在根据你的描述生成产品图...</p>
                <p className="text-xs mt-1 opacity-60">这可能需要 10-30 秒</p>
              </div>
            ) : images.length > 0 ? (
              <div className="space-y-4">
                {images.map((src, i) => (
                  <div key={i} className="space-y-2">
                    <div className="relative group rounded-[8px] overflow-hidden border border-border bg-muted/30">
                      <img
                        src={src}
                        alt={`生成图 ${i + 1}`}
                        className="w-full object-contain"
                        style={{ maxHeight: 420 }}
                      />
                      <button
                        onClick={() => downloadImage(src, i)}
                        className="absolute top-3 right-3 p-2 rounded-[8px] bg-background/80 backdrop-blur-sm border border-border opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        <Download size={15} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <Wand2 size={40} className="mb-3 opacity-20" />
                <p className="text-sm">在左侧输入产品图描述</p>
                <p className="text-xs mt-1">AI 将根据描述生成电商产品图</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
