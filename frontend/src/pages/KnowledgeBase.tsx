import { useState, useEffect, useRef } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
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
  BrainCircuit,
} from "lucide-react"
import api from "@/lib/api"

const ACCEPTED_TYPES = ".pdf,.docx,.doc,.txt,.md"
const MAX_FILE_SIZE = 10 * 1024 * 1024

type IndexProgress = {
  taskId?: string
  state: "idle" | "running" | "completed" | "failed"
  running?: boolean
  trigger?: string
  phase?: string
  progress: number
  processedItems?: number
  totalItems?: number
  changedItems?: number
  removedItems?: number
  message?: string
  startedAt?: string | null
  updatedAt?: string | null
  finishedAt?: string | null
  error?: string | null
}

const phaseLabels: Record<string, string> = {
  idle: "空闲",
  preparing: "准备中",
  loading: "读取数据",
  loading_documents: "读取文档",
  loading_products: "读取产品",
  manifest: "比对清单",
  removing: "清理旧向量",
  embedding_documents: "向量化文档",
  embedding_products: "向量化产品",
  persisting: "保存索引",
  completed: "已完成",
  failed: "失败",
}

export default function KnowledgeBase() {
  const [products, setProducts] = useState<any[]>([])
  const [documents, setDocuments] = useState<any[]>([])
  const [search, setSearch] = useState("")
  const [ragQuery, setRagQuery] = useState("")
  const [ragTopK, setRagTopK] = useState(5)
  const [ragLoading, setRagLoading] = useState(false)
  const [ragResults, setRagResults] = useState<any[]>([])
  const [ragMessage, setRagMessage] = useState("")
  const [reloadingIndex, setReloadingIndex] = useState(false)
  const [indexProgress, setIndexProgress] = useState<IndexProgress>({
    state: "idle",
    progress: 100,
    message: "当前没有索引更新任务",
  })
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState({
    productCount: 0,
    docCount: 0,
    enabled: false,
    autoInject: false,
    retrievalMode: "",
    productVectorIndexEnabled: false,
    maxProductEmbeddings: 0,
    productEmbeddingScope: "",
  })
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFiles, setUploadFiles] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [uploadResults, setUploadResults] = useState<{ fileName: string; status: "success" | "failed" | "uploading"; error?: string }[]>([])
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dropRef = useRef<HTMLDivElement>(null)

  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const { signal } = controller
    loadData(signal)
    loadIndexProgress(signal)
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const shouldPoll = indexProgress.running || indexProgress.state === "running"
    if (!shouldPoll) return

    const timer = window.setInterval(() => {
      loadIndexProgress()
    }, 1500)

    return () => window.clearInterval(timer)
  }, [indexProgress.running, indexProgress.state])

  useEffect(() => {
    setReloadingIndex(indexProgress.running || indexProgress.state === "running")
  }, [indexProgress.running, indexProgress.state])

  const loadData = async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      const [statusRes, prodRes, docRes] = await Promise.allSettled([
        api.get("/agent/knowledge/status", { signal }),
        api.get("/agent/knowledge/products", { params: { size: 100 }, signal }),
        api.get("/agent/knowledge/documents", { signal }),
      ])

      if (signal?.aborted) return

      if (statusRes.status === "fulfilled") {
        const s = statusRes.value.data
        setStats({
          productCount: s.productCount ?? 0,
          docCount: s.knowledgeDocumentCount ?? 0,
          enabled: s.enabled ?? false,
          autoInject: s.autoInject ?? false,
          retrievalMode: s.retrievalMode ?? "",
          productVectorIndexEnabled: s.productVectorIndexEnabled ?? false,
          maxProductEmbeddings: s.maxProductEmbeddings ?? 0,
          productEmbeddingScope: s.productEmbeddingScope ?? "",
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

  const normalizeProgress = (data: any): IndexProgress => ({
    taskId: data?.taskId || "",
    state: data?.state || "idle",
    running: Boolean(data?.running),
    trigger: data?.trigger || "",
    phase: data?.phase || "idle",
    progress: Math.max(0, Math.min(100, Number(data?.progress ?? 0))),
    processedItems: Number(data?.processedItems ?? 0),
    totalItems: Number(data?.totalItems ?? 0),
    changedItems: Number(data?.changedItems ?? 0),
    removedItems: Number(data?.removedItems ?? 0),
    message: data?.message || "",
    startedAt: data?.startedAt || null,
    updatedAt: data?.updatedAt || null,
    finishedAt: data?.finishedAt || null,
    error: data?.error || null,
  })

  const loadIndexProgress = async (signal?: AbortSignal) => {
    try {
      const { data } = await api.get("/agent/knowledge/index-progress", { signal })
      if (signal?.aborted) return
      const next = normalizeProgress(data)
      setIndexProgress(next)
      if (next.state === "completed" && next.progress === 100) {
        loadData()
      }
    } catch (e) {
      if (!signal?.aborted) {
        console.error("加载索引进度失败", e)
      }
    }
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

  const handleRagSearch = async () => {
    if (!ragQuery.trim()) return
    setRagLoading(true)
    setRagMessage("")
    try {
      const { data } = await api.post("/agent/knowledge/search", {
        query: ragQuery.trim(),
        maxResults: ragTopK,
      })
      setRagResults(Array.isArray(data?.results) ? data.results : [])
      setRagMessage(data?.ragUsed ? "已命中知识库，Agent 对话会自动注入这些上下文。" : "未找到足够相关的知识片段。")
    } catch (e: any) {
      setRagMessage(e.response?.data?.message || "检索失败，请检查后端服务")
    } finally {
      setRagLoading(false)
    }
  }

  const handleReloadIndex = async () => {
    setReloadingIndex(true)
    setRagMessage("")
    try {
      const { data } = await api.post("/agent/knowledge/reload")
      const next = normalizeProgress(data)
      setIndexProgress(next)
      setRagMessage(data?.message || "索引更新已启动")
      loadIndexProgress()
    } catch (e: any) {
      setRagMessage(e.response?.data?.message || "重建索引失败")
      setReloadingIndex(false)
      setIndexProgress((prev) => ({
        ...prev,
        state: "failed",
        running: false,
        error: e.response?.data?.message || "重建索引失败",
        message: e.response?.data?.message || "重建索引失败",
      }))
    }
  }

  const formatIndexTime = (value?: string | null) => {
    if (!value) return "—"
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value.replace("T", " ").slice(0, 19)
    return date.toLocaleString("zh-CN", { hour12: false })
  }

  const indexStatusTone = indexProgress.state === "failed"
    ? "destructive"
    : indexProgress.state === "running"
      ? "secondary"
      : indexProgress.state === "completed"
        ? "success"
        : "secondary"

  const filteredProducts = products.filter((p) =>
    (p.name || "").toLowerCase().includes(search.toLowerCase()) ||
    (p.sku || "").toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="page-kicker">PRODUCT KNOWLEDGE</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">RAG 知识库</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              {loading ? "加载中..." : `${stats.productCount} 款产品 · ${stats.docCount} 篇文档`}，为询盘回复和外贸 Agent 提供可追溯上下文。
            </p>
          </div>
        <div className="flex flex-wrap items-center gap-2">
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
                  className={`border-2 border-dashed rounded-[8px] p-6 text-center transition-colors cursor-pointer
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
                          className="flex items-center gap-2 px-3 py-2 rounded-[8px] bg-muted/50"
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

          <Button variant="outline" size="sm" onClick={() => loadData()} disabled={loading}>
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            刷新
          </Button>
          <Button variant="outline" size="sm" onClick={handleReloadIndex} disabled={reloadingIndex}>
            <RefreshCw size={14} className={reloadingIndex ? "animate-spin" : ""} />
            {reloadingIndex ? "更新中" : "重建索引"}
          </Button>
          <Badge variant={stats.enabled ? "success" : "secondary"} className="text-[11px]">
            <Database size={11} /> {stats.enabled ? "RAG Engine ON" : "RAG OFF"}
          </Badge>
        </div>
        </div>
      </div>

      <Card className="overflow-hidden">
        <CardContent className="p-4 sm:p-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={indexStatusTone as any} className="text-[11px]">
                  {phaseLabels[indexProgress.phase || "idle"] || indexProgress.phase || "索引状态"}
                </Badge>
                <span className="text-sm font-semibold text-foreground">
                  RAG 向量索引
                </span>
                <span className="text-xs text-muted-foreground">
                  {indexProgress.message || "当前没有索引更新任务"}
                </span>
              </div>
              <div className="mt-3 flex items-center gap-3">
                <Progress value={indexProgress.progress} className="h-2 flex-1" />
                <span className="w-12 text-right font-mono text-xs text-muted-foreground">
                  {Math.round(indexProgress.progress)}%
                </span>
              </div>
              {indexProgress.error && (
                <p className="mt-2 text-xs text-red-600">{indexProgress.error}</p>
              )}
            </div>
            <div className="grid grid-cols-2 gap-x-5 gap-y-1 text-xs text-muted-foreground sm:grid-cols-4 lg:grid-cols-2 xl:grid-cols-4">
              <span>待更新 {indexProgress.changedItems ?? 0}</span>
              <span>移除 {indexProgress.removedItems ?? 0}</span>
              <span>
                处理 {indexProgress.processedItems ?? 0}
                {indexProgress.totalItems ? ` / ${indexProgress.totalItems}` : ""}
              </span>
              <span>更新 {formatIndexTime(indexProgress.updatedAt)}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="trade-signal-card text-center p-4">
          <Box size={20} className="mx-auto mb-1 text-primary" />
          <p className="text-xl font-bold">{loading ? "—" : stats.productCount}</p>
          <p className="text-[11px] text-muted-foreground">产品数据 (MySQL)</p>
        </div>
        <div className="trade-signal-card text-center p-4">
          <FileText size={20} className="mx-auto mb-1 text-[#516B63]" />
          <p className="text-xl font-bold">{loading ? "—" : stats.docCount}</p>
          <p className="text-[11px] text-muted-foreground">知识文档 (MySQL)</p>
        </div>
        <div className="trade-signal-card text-center p-4">
          <BookOpen size={20} className="mx-auto mb-1 text-emerald-500" />
          <p className="text-xl font-bold">{stats.enabled ? "ON" : "OFF"}</p>
          <p className="text-[11px] text-muted-foreground">RAG 引擎状态</p>
        </div>
        <div className="trade-signal-card text-center p-4">
          <Database size={20} className="mx-auto mb-1 text-[#0B918C]" />
          <p className="text-xl font-bold">MySQL</p>
          <p className="text-[11px] text-muted-foreground">
            {stats.productVectorIndexEnabled
              ? `产品向量 ${stats.productEmbeddingScope === "all" ? "全量" : stats.maxProductEmbeddings}`
              : "数据存储"}
          </p>
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-base flex items-center gap-2">
                <BrainCircuit size={16} className="text-[#1F5F53]" /> RAG 检索验证
              </CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">
                {stats.autoInject ? "Agent 对话已开启自动注入。" : "当前未开启自动注入。"}
                {stats.retrievalMode ? ` ${stats.retrievalMode}` : ""}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min={1}
                max={10}
                value={ragTopK}
                onChange={(e) => setRagTopK(Math.max(1, Math.min(10, Number(e.target.value) || 5)))}
                className="h-9 w-20 text-xs"
              />
              <Button size="sm" onClick={handleRagSearch} disabled={ragLoading || !ragQuery.trim()}>
                {ragLoading ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
                检索
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={ragQuery}
              onChange={(e) => setRagQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleRagSearch() }}
              placeholder="输入一个真实业务问题，例如：展示架出口德国需要注意什么认证？"
              className="h-10 pl-8 text-sm"
            />
          </div>
          {ragMessage && (
            <div className="rounded-[10px] border border-[#E4E8E5] bg-[#F8FBFA] px-3 py-2 text-xs text-muted-foreground">
              {ragMessage}
            </div>
          )}
          {ragResults.length > 0 && (
            <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
              {ragResults.map((item, i) => (
                <div key={`${item.source}-${i}`} className="rounded-[10px] border border-[#E4E8E5] bg-white p-3">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <Badge variant="secondary" className="text-[10px]">
                      #{item.rank || i + 1} {item.source || "knowledge"}
                    </Badge>
                    <span className="font-mono text-[10px] text-muted-foreground">
                      score {Number(item.score || 0).toFixed(3)}
                    </span>
                  </div>
                  <p className="text-xs leading-relaxed text-[#343A35]">{item.snippet}</p>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

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
            <FileText size={16} className="text-[#516B63]" /> 知识文档
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
                  <div className="w-8 h-8 rounded-[8px] bg-[#F4F6F5] flex items-center justify-center flex-shrink-0">
                    <FileText size={14} className="text-[#516B63]" />
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
