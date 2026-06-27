import { useState, useRef } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Image as ImageIcon,
  Upload,
  Loader2,
  Sparkles,
  Camera,
  X,
} from "lucide-react"
import api from "@/lib/api"

export default function ImageRecognition() {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState("")
  const [prompt, setPrompt] = useState("请详细描述这张图片的内容，包括产品特征、颜色、材质、用途等")
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState("")
  const fileRef = useRef<HTMLInputElement>(null)

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    if (!f) return
    setFile(f)
    setPreview(URL.createObjectURL(f))
    setResult("")
  }

  const clearImage = () => {
    setFile(null)
    setPreview("")
    setResult("")
    if (fileRef.current) fileRef.current.value = ""
  }

  const handleRecognize = async () => {
    if (!file || loading) return
    setLoading(true)
    try {
      const formData = new FormData()
      formData.append("file", file)
      formData.append("prompt", prompt.trim())
      const { data } = await api.post("/image/recognize", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 180000,
      })
      setResult(data.result || data.analysis || "")
    } catch {
      setResult("识别失败，请稍后重试。")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex items-center justify-between gap-4">
          <div>
            <div className="page-kicker">VISION AGENT</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">AI 智能识图</h1>
            <p className="mt-2 text-sm text-muted-foreground">AI 视觉识别，分析产品特征、材质、包装和适用外贸场景。</p>
          </div>
          <Badge variant="purple" className="text-[11px]">
            <Camera size={11} /> Vision AI
          </Badge>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Upload */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <ImageIcon size={16} className="text-primary" /> 图片上传
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              onChange={handleFileChange}
              className="hidden"
              id="image-upload"
            />
            {preview ? (
              <div className="relative">
                <img
                  src={preview}
                  alt="Preview"
                  className="w-full rounded-[8px] object-contain max-h-[300px] bg-muted"
                />
                <button
                  onClick={clearImage}
                  className="absolute top-2 right-2 w-7 h-7 rounded-full bg-background/80 border border-border flex items-center justify-center hover:bg-destructive hover:text-destructive-foreground transition-colors"
                >
                  <X size={13} />
                </button>
              </div>
            ) : (
              <label
                htmlFor="image-upload"
                className="flex flex-col items-center justify-center py-16 rounded-[8px] border-2 border-dashed border-border hover:border-primary/40 hover:bg-accent/10 cursor-pointer transition-all"
              >
                <Upload size={40} className="text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">点击或拖拽上传图片</p>
                <p className="text-[11px] text-muted-foreground/50 mt-1">支持 JPG / PNG / WebP</p>
              </label>
            )}

            <div className="space-y-2">
              <Label>识别提示词 (可选)</Label>
              <Input
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="自定义识别指令..."
              />
            </div>

            <Button
              className="w-full"
              variant="gradient"
              onClick={handleRecognize}
              disabled={loading || !file}
            >
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
              {loading ? "识别中..." : "开始识别"}
            </Button>
          </CardContent>
        </Card>

        {/* Result */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">识别结果</CardTitle>
          </CardHeader>
          <CardContent>
            {result ? (
              <div className="trade-signal-card p-4 text-sm leading-relaxed whitespace-pre-wrap min-h-[300px]">
                {result}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-muted-foreground min-h-[300px]">
                <Camera size={48} className="mb-4 opacity-20" />
                <p className="text-sm">上传图片后开始识别</p>
                <p className="text-xs mt-1">AI Vision Technology</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
