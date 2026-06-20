import { useState, useEffect, useRef } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import {
  Database,
  Search,
  FileText,
  Box,
  BookOpen,
  Loader2,
  RefreshCw,
  Upload,
  File,
  X,
  CheckCircle2,
  AlertCircle,
  Trash2,
} from "lucide-react"
import api from "@/lib/api"

const ACCEPTED_TYPES = ".pdf,.docx,.doc,.txt,.md"
const MAX_FILE_SIZE = 10 * 1024 * 1024

export default function KnowledgeBase() {
  const [products, setProducts] = useState<any[]>([])
  const [documents, setDocuments] = useState<any[]>([])
  const [search, setSearch] = useState("")
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState({ productCount: 0, docCount: 0, enabled: false })
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFiles, setUploadFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [uploadResults, setUploadResults] = useState<{ fileName: string; status: "success" | "failed" | "uploading"; error?: string }[]>([])
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)

  useEffect(() => { loadData() }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      const [statusRes, prodRes, docRes] = await Promise.allSettled([
        api.get("/agent/knowledge/status"),
        api.get("/agent/knowledge/products", { params: { size: 100 } }),
        api.get("/agent/knowledge/documents"),
      ])

      if (statusRes.status === "fulfilled") {
        const s = statusRes.value.data
        setStats({
          productCount: s.productCount ?? 0,
          docCount: s.knowledgeDocumentCount ?? 0,
          enabled: s.enabled ?? false,
        })
      }
      if (prodRes.status === "fulfilled") {
        const data = prodRes.value.data
        setProducts(Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : [])
      }
      if (docRes.status === "fulfilled") {
        const data = docRes.value.data
        setDocuments(Array.isArray(data?.documents) ? data.documents : Array.isArray(data) ? data : [])
      }
    } catch {}
    setLoading(false)
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || [])
    addFiles(files)
  }

  const addFiles = (files: File[]) => {
    const valid = files.filter((f) => {
      const ext = f.name.split(".").pop()?.toLowerCase()
      if (!["pdf", "docx", "doc", "txt", "md"].includes(ext || "")) return false
      if (f.size > MAX_FILE_SIZE) return false
      return true
    })
    setUploadFiles((prev) => [...prev, ...valid])
  }

  const removeFile = (idx: number) => {
    setUploadFiles((prev) => prev.filter((_, i) => i !== idx))
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const files = Array.from(e.dataTransfer.files)
    addFiles(files)
  }

  const handleUpload = async () => {
    if (uploadFiles.length === 0) return
    setUploading(true)
    setUploadResults(uploadFiles.map((f) => ({ fileName: f.name, status: "uploading" })))

    let successCount = 0
    for (let i = 0; i < uploadFiles.length; i++) {
      const file = uploadFiles[i]
      try {
        const formData = new FormData()
        formData.append("file", file)
        await api.post("/agent/knowledge/upload", formData, {
          headers: { "Content-Type": "multipart/form-data" },
        })
        setUploadResults((prev) => {
          const next = [...prev]
          next[i] = { fileName: file.name, status: "success" }
          return next
        })
        successCount++
      } catch (e: any) {
        const msg = e.response?.data?.error || e.message || "上传失败"
        setUploadResults((prev) => {
          const next = [...prev]
          next[i] = { fileName: file.name, status: "failed", error: msg }
          return next
        })
      }
    }

    setUploading(false)
    if (successCount > 0) {
      loadData() // 刷新列表
    }
  }

  const handleDeleteDoc = async (id: number) => {
    try {
      await api.delete(`/agent/knowledge/documents/${id}`)
      loadData()
    } catch (e: any) {
      console.error("删除文档失败", e)
    }
  }

  const filteredProducts = products.filter((p) =>
    (p.name || "").toLowerCase().includes(search.toLowerCase()) ||
    (p.sku || "").toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">RAG 知识库</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {loading ? "加载中..." : `${stats.productCount} 款产品 · ${stats.docCount} 篇文档`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
            <DialogTrigger asChild>
              <Button variant="default" size="sm">
                <Upload size={14} className="mr-1" />
                上传文档
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[480px]">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <Upload size={18} /> 上传知识文档
                </DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                {/* 拖拽区域 */}
                <div
                  ref={dropRef}
                  className={`border-2 border-dashed rounded-xl p-6 text-center transition-colors cursor-pointer
                    ${dragOver ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"}`}
                  onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
                  onDragLeave={() => setDragOver(false)}
                  onDrop={handleDrop}
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Upload size={32} className="mx-auto mb-2 text-muted-foreground" />
                  <p className="text-sm text-muted-foreground">
                    拖拽文件到此处，或点击选择
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    支持 PDF、Word (.docx)、TXT、Markdown · 单文件 ≤10MB
                  </p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    accept={ACCEPTED_TYPES}
                    className="hidden"
                    onChange={handleFileSelect}
                  />
                </div>

                {/* 已选文件列表 */}
                {uploadFiles.length > 0 && (
                  <div className="space-y-1.5 max-h-[200px] overflow-y-auto">
                    {uploadFiles.map((file, i) => {
                      const result = uploadResults[i]
                      return (
                        <div
                          key={`${file.name}-${i}`}
                          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-muted/50"
                        >
                          <File size={14} className="text-muted-foreground flex-shrink-0" />
                          <span className="text-sm truncate flex-1">{file.name}</span>
                          <span className="text-xs text-muted-foreground flex-shrink-0">
                            {(file.size / 1024).toFixed(0)}KB
                          </span>
                          {result?.status === "success" ? (
                            <CheckCircle2 size={14} className="text-emerald-500 flex-shrink-0" />
                          ) : result?.status === "failed" ? (
                            <AlertCircle size={14} className="text-red-500 flex-shrink-0" title={result.error} />
                          ) : (
                            <button
                              className="text-muted-foreground hover:text-foreground flex-shrink-0"
                              onClick={(e) => { e.stopPropagation(); removeFile(i) }}
                              disabled={uploading}
                            >
                              <X size={14} />
                            </button>
                          )}
                        </div>
                      )
                    })}
                  </div>
                )}

                {/* 操作按钮 */}
                <div className="flex items-center justify-between">
                  <p className="text-xs text-muted-foreground">
                    {uploadFiles.length > 0
                      ? `已选 ${uploadFiles.length} 个文件，总大小 ${(uploadFiles.reduce((s, f) => s + f.size, 0) / 1024).toFixed(0)}KB`
                      : "请选择要上传的文件"}
                  </p>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => { setUploadOpen(false); setUploadFiles([]); setUploadResults([]) }}>
                      取消
                    </Button>
                    <Button
                      size="sm"
                      onClick={handleUpload}
                      disabled={uploadFiles.length === 0 || uploading}
                    >
                      {uploading ? (
                        <>
                          <Loader2 size={14} className="animate-spin mr-1" />
                          上传中...
                        </>
                      ) : (
                        "开始上传"
                      )}
                    </Button>
                  </div>
                </div>
              </div>
            </DialogContent>
          </Dialog>

          <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            刷新
          </Button>
          <Badge variant={stats.enabled ? "success" : "secondary"} className="text-[11px]">
            <Database size={11} /> {stats.enabled ? "RAG Engine ON" : "RAG OFF"}
          </Badge>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="text-center p-4 rounded-[16px] bg-card border border-border">
          <Box size={20} className="mx-auto mb-1 text-primary" />
          <p className="text-xl font-bold">{loading ? "—" : stats.productCount}</p>
          <p className="text-[11px] text-muted-foreground">产品数据 (MySQL)</p>
        </div>
        <div className="text-center p-4 rounded-[16px] bg-card border border-border">
          <FileText size={20} className="mx-auto mb-1 text-blue-500" />
          <p className="text-xl font-bold">{loading ? "—" : stats.docCount}</p>
          <p className="text-[11px] text-muted-foreground">知识文档 (MySQL)</p>
        </div>
        <div className="text-center p-4 rounded-[16px] bg-card border border-border">
          <BookOpen size={20} className="mx-auto mb-1 text-emerald-500" />
          <p className="text-xl font-bold">{stats.enabled ? "ON" : "OFF"}</p>
          <p className="text-[11px] text-muted-foreground">RAG 引擎状态</p>
        </div>
        <div className="text-center p-4 rounded-[16px] bg-card border border-border">
          <Database size={20} className="mx-auto mb-1 text-violet-500" />
          <p className="text-xl font-bold">MySQL</p>
          <p className="text-[11px] text-muted-foreground">数据存储</p>
        </div>
      </div>

      {/* Products */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base flex items-center gap-2">
              <Box size={16} className="text-primary" /> 产品数据
              <span className="text-xs text-muted-foreground font-normal">
                (显示最近 {products.length} 条，共 {stats.productCount} 条)
              </span>
            </CardTitle>
            <div className="relative w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="搜索产品..."
                className="pl-8 h-8 text-xs"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 size={24} className="animate-spin text-muted-foreground" />
            </div>
          ) : products.length > 0 ? (
            <div className="max-h-[360px] overflow-y-auto px-4 pb-4 space-y-1">
              {filteredProducts.map((p, i) => (
                <div
                  key={p.id || p.sku || i}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-[10px] hover:bg-muted/50 transition-colors"
                >
                  <div className="w-8 h-8 rounded-[8px] bg-muted flex items-center justify-center flex-shrink-0 overflow-hidden">
                    {p.imageUrl ? (
                      <img src={p.imageUrl} alt="" className="w-full h-full object-cover" loading="lazy" />
                    ) : (
                      <Box size={14} className="text-muted-foreground" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{p.name || `Product #${i + 1}`}</p>
                    <div className="flex items-center gap-2">
                      {p.sku && <span className="text-[10px] text-muted-foreground">SKU: {p.sku}</span>}
                      {p.category && (
                        <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4 font-normal">{p.category}</Badge>
                      )}
                    </div>
                  </div>
                  {p.price && (
                    <span className="text-xs font-semibold text-emerald-600 flex-shrink-0">{p.price}</span>
                  )}
                </div>
              ))}
              {filteredProducts.length === 0 && (
                <p className="text-center py-8 text-muted-foreground text-sm">没有找到匹配的产品</p>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center py-12 text-muted-foreground">
              <Box size={40} className="mb-3 opacity-20" />
              <p className="text-sm">暂无产品数据</p>
              <p className="text-xs mt-1">配置 MySQL 后运行爬虫即可自动填充产品数据</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Documents */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <FileText size={16} className="text-blue-500" /> 知识文档
            <span className="text-xs text-muted-foreground font-normal">(共 {stats.docCount} 篇)</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 size={20} className="animate-spin text-muted-foreground" />
            </div>
          ) : documents.length > 0 ? (
            <div className="space-y-2 max-h-[300px] overflow-y-auto">
              {documents.map((d, i) => (
                <div
                  key={d.id || i}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-[10px] hover:bg-muted/50 transition-colors group"
                >
                  <div className="w-8 h-8 rounded-[8px] bg-blue-50 flex items-center justify-center flex-shrink-0">
                    <FileText size={14} className="text-blue-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{d.title || `Document #${i + 1}`}</p>
                    <div className="flex items-center gap-2">
                      {d.category && (
                        <Badge variant="secondary" className="text-[9px] px-1.5 py-0 h-4 font-normal">{d.category}</Badge>
                      )}
                      {d.sourceType && (
                        <Badge
                          variant={d.sourceType === "USER_UPLOAD" ? "success" : "secondary"}
                          className="text-[9px] px-1.5 py-0 h-4 font-normal"
                        >
                          {d.sourceType === "USER_UPLOAD" ? "用户上传" : d.sourceType === "BUILT_IN" ? "内置" : d.sourceType}
                        </Badge>
                      )}
                      {d.fileType && (
                        <span className="text-[10px] text-muted-foreground">{d.fileType}</span>
                      )}
                      <span className="text-[10px] text-muted-foreground">
                        {d.contentLength ? `${d.contentLength} 字符` : ""}
                      </span>
                      {d.fileName && (
                        <span className="text-[10px] text-muted-foreground truncate max-w-[120px]">
                          {d.fileName}
                        </span>
                      )}
                    </div>
                  </div>
                  {d.enabled !== undefined && (
                    <Badge variant={d.enabled ? "success" : "secondary"} className="text-[9px]">
                      {d.enabled ? "启用" : "禁用"}
                    </Badge>
                  )}
                  <button
                    className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded hover:bg-red-50 text-red-500 flex-shrink-0"
                    onClick={() => handleDeleteDoc(d.id)}
                    title="删除文档"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center py-8 text-muted-foreground">
              <FileText size={32} className="mb-2 opacity-20" />
              <p className="text-sm">暂无知识文档</p>
              <p className="text-xs mt-1">点击「上传文档」添加 PDF、Word 或 TXT 文件到知识库</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
