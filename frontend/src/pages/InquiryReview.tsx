import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  AlertCircle,
  CalendarClock,
  ClipboardCheck,
  Copy,
  Check,
  CheckCircle2,
  Pencil,
  FileSpreadsheet,
  FileText,
  Inbox,
  Loader2,
  MailPlus,
  PackageCheck,
  Plus,
  RefreshCw,
  Save,
  Send,
  Trash2,
  Upload,
  X,
  Zap,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

interface InquiryCase {
  id: number
  caseNo: string
  title: string
  customerName: string
  contactName: string
  contactEmail: string
  country: string
  source: string
  status: string
  score?: number | null
  summary: string
  updatedAt: string
}

interface InquiryArtifact {
  id: number
  fileName: string
  fileType: string
  sourceType: string
  contentPreview: string
  rawText: string
  parseStatus: string
  parseError: string
}

interface Requirement {
  id: number
  fieldKey?: string
  fieldLabel: string
  fieldValue: string
  sourceEvidence?: string
  status: string
}

interface MissingField {
  id: number
  fieldKey: string
  reason: string
  questionEn: string
  priority: string
}

interface RiskFlag {
  id: number
  riskType?: string
  level: string
  title: string
  description: string
  suggestion: string
}

interface QuoteTaskDraft {
  id: number
  taskTitle: string
  knownInfo: string
  missingInfo: string
  riskSummary: string
  quoteAssumptions: string
  productSummary: string
  moq: string
  sampleFee: string
  sampleLeadTime: string
  massProductionLeadTime: string
  tradeTerm: string
  destinationPort: string
  paymentTerm: string
  packagingRequirement: string
  followUpPlan: string
  nextFollowUpAt: string
  quoteReadiness?: number
  assigneeRole: string
  emailDraft: string
}

interface CaseDetail {
  case: InquiryCase
  artifacts: InquiryArtifact[]
  requirements: Requirement[]
  missingFields: MissingField[]
  risks: RiskFlag[]
  quoteTaskDraft: QuoteTaskDraft | null
}

interface WorkspaceTodo {
  caseId: number
  caseNo: string
  caseTitle: string
  customerName: string
  status: string
  type: string
  priority: string
  title: string
  action: string
  updatedAt: string
}

interface WorkspaceSummary {
  funnel: Record<string, number>
  todos: WorkspaceTodo[]
  totalCases: number
  openCases: number
  highPriorityTodos: number
}

const statusLabel: Record<string, string> = {
  DRAFT: "草稿",
  REVIEWING: "审查中",
  WAITING_CUSTOMER: "待客户确认",
  READY_TO_QUOTE: "可报价",
  CLOSED: "已关闭",
}

const fieldGroups = ["产品类型", "尺寸", "数量", "材料", "颜色", "包装", "贸易条款", "目的港", "交期", "认证"]

const buildTaskDraftState = (draft?: QuoteTaskDraft | null) => ({
  taskTitle: draft?.taskTitle || "内部报价任务",
  knownInfo: draft?.knownInfo || "",
  missingInfo: draft?.missingInfo || "",
  riskSummary: draft?.riskSummary || "",
  quoteAssumptions: draft?.quoteAssumptions || "",
  productSummary: draft?.productSummary || "",
  moq: draft?.moq || "",
  sampleFee: draft?.sampleFee || "",
  sampleLeadTime: draft?.sampleLeadTime || "",
  massProductionLeadTime: draft?.massProductionLeadTime || "",
  tradeTerm: draft?.tradeTerm || "",
  destinationPort: draft?.destinationPort || "",
  paymentTerm: draft?.paymentTerm || "",
  packagingRequirement: draft?.packagingRequirement || "",
  followUpPlan: draft?.followUpPlan || "",
  nextFollowUpAt: draft?.nextFollowUpAt ? draft.nextFollowUpAt.slice(0, 16) : "",
  assigneeRole: draft?.assigneeRole || "SALES",
})

export default function InquiryReview() {
  const [cases, setCases] = useState<InquiryCase[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<CaseDetail | null>(null)
  const [summary, setSummary] = useState<WorkspaceSummary | null>(null)
  const [loadingCases, setLoadingCases] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [creating, setCreating] = useState(false)
  const [newCaseOpen, setNewCaseOpen] = useState(false)
  const [newFieldOpen, setNewFieldOpen] = useState(false)
  const [actionEditorOpen, setActionEditorOpen] = useState(false)
  const [actionMode, setActionMode] = useState<"missing" | "risk">("missing")
  const [editingActionId, setEditingActionId] = useState<number | null>(null)
  const [editingFieldId, setEditingFieldId] = useState<number | null>(null)
  const [editingField, setEditingField] = useState({ fieldValue: "", status: "USER_CONFIRMED" })
  const [newField, setNewField] = useState({ fieldLabel: "", fieldValue: "", status: "USER_CONFIRMED" })
  const [actionForm, setActionForm] = useState({
    fieldKey: "",
    reason: "",
    questionEn: "",
    priority: "MEDIUM",
    riskType: "SPEC",
    level: "MEDIUM",
    title: "",
    description: "",
    suggestion: "",
  })
  const [newCase, setNewCase] = useState({
    title: "",
    customerName: "",
    contactName: "",
    contactEmail: "",
    country: "",
    source: "email",
    emailText: "",
  })
  const [pasteText, setPasteText] = useState("")
  const [pasteTitle, setPasteTitle] = useState("客户邮件正文")
  const [uploading, setUploading] = useState(false)
  const [analyzing, setAnalyzing] = useState(false)
  const [savingDraft, setSavingDraft] = useState(false)
  const [creatingTask, setCreatingTask] = useState(false)
  const [markingAsked, setMarkingAsked] = useState(false)
  const [emailDraft, setEmailDraft] = useState("")
  const [taskDraft, setTaskDraft] = useState({
    taskTitle: "",
    knownInfo: "",
    missingInfo: "",
    riskSummary: "",
    quoteAssumptions: "",
    productSummary: "",
    moq: "",
    sampleFee: "",
    sampleLeadTime: "",
    massProductionLeadTime: "",
    tradeTerm: "",
    destinationPort: "",
    paymentTerm: "",
    packagingRequirement: "",
    followUpPlan: "",
    nextFollowUpAt: "",
    assigneeRole: "SALES",
  })
  const [error, setError] = useState("")
  const fileInputRef = useRef<HTMLInputElement>(null)

  const selectedCase = detail?.case
  const hasArtifacts = (detail?.artifacts.length ?? 0) > 0
  const quoteReadiness = detail?.quoteTaskDraft?.quoteReadiness ?? 0

  const completion = useMemo(() => {
    if (!detail) return 0
    const values = [
      detail.case.customerName,
      detail.case.contactEmail,
      detail.case.country,
      detail.artifacts.length ? "ok" : "",
      detail.requirements.length ? "ok" : "",
      detail.quoteTaskDraft?.emailDraft,
    ]
    return Math.round((values.filter(Boolean).length / values.length) * 100)
  }, [detail])

  const fetchCases = useCallback(async () => {
    setLoadingCases(true)
    setError("")
    try {
      const { data } = await api.get("/inquiry-review/cases")
      const list: InquiryCase[] = data.cases || []
      setCases(list)
      if (!selectedId && list.length > 0) setSelectedId(list[0].id)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "获取询盘案件失败")
    } finally {
      setLoadingCases(false)
    }
  }, [selectedId])

  const fetchSummary = useCallback(async () => {
    try {
      const { data } = await api.get("/inquiry-review/workspace-summary")
      setSummary(data)
    } catch {
      setSummary(null)
    }
  }, [])

  const fetchDetail = useCallback(async (caseId: number) => {
    setLoadingDetail(true)
    setError("")
    try {
      const { data } = await api.get(`/inquiry-review/cases/${caseId}`)
      setDetail(data)
      setEmailDraft(data.quoteTaskDraft?.emailDraft || "")
      setTaskDraft(buildTaskDraftState(data.quoteTaskDraft))
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "获取案件详情失败")
    } finally {
      setLoadingDetail(false)
    }
  }, [])

  useEffect(() => {
    fetchCases()
    fetchSummary()
  }, [fetchCases, fetchSummary])

  useEffect(() => {
    if (selectedId) fetchDetail(selectedId)
  }, [selectedId, fetchDetail])

  const refreshWorkspace = async (caseId = selectedId) => {
    if (caseId) await fetchDetail(caseId)
    await fetchCases()
    await fetchSummary()
  }

  const handleCreateCase = async () => {
    if (creating) return
    setCreating(true)
    setError("")
    try {
      const { data } = await api.post("/inquiry-review/cases", newCase)
      const created = data.detail?.case as InquiryCase
      await fetchCases()
      await fetchSummary()
      setSelectedId(created.id)
      setDetail(data.detail)
      setNewCaseOpen(false)
      setNewCase({ title: "", customerName: "", contactName: "", contactEmail: "", country: "", source: "email", emailText: "" })
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "创建询盘案件失败")
    } finally {
      setCreating(false)
    }
  }

  const handlePaste = async () => {
    if (!selectedId || !pasteText.trim()) return
    setUploading(true)
    setError("")
    try {
      await api.post(`/inquiry-review/cases/${selectedId}/artifacts/text`, { title: pasteTitle, text: pasteText })
      setPasteText("")
      await refreshWorkspace(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "保存粘贴资料失败")
    } finally {
      setUploading(false)
    }
  }

  const handleFileUpload = async (file?: File) => {
    if (!selectedId || !file) return
    setUploading(true)
    setError("")
    try {
      const formData = new FormData()
      formData.append("file", file)
      await api.post(`/inquiry-review/cases/${selectedId}/artifacts`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      await refreshWorkspace(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "上传资料失败")
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  const saveDrafts = async () => {
    if (!selectedId) return
    setSavingDraft(true)
    setError("")
    try {
      await api.put(`/inquiry-review/cases/${selectedId}/quote-task-draft`, { ...taskDraft, emailDraft })
      await refreshWorkspace(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "保存草稿失败")
    } finally {
      setSavingDraft(false)
    }
  }

  const analyzeCase = async () => {
    if (!selectedId || analyzing) return
    setAnalyzing(true)
    setError("")
    try {
      const { data } = await api.post(`/inquiry-review/cases/${selectedId}/analyze`)
      setDetail(data.detail)
      setEmailDraft(data.detail?.quoteTaskDraft?.emailDraft || "")
      setTaskDraft(buildTaskDraftState(data.detail?.quoteTaskDraft))
      await fetchCases()
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "AI 审查失败")
    } finally {
      setAnalyzing(false)
    }
  }

  const copyEmail = async () => {
    if (emailDraft.trim()) await navigator.clipboard.writeText(emailDraft)
  }

  const startEditField = (item: Requirement) => {
    setEditingFieldId(item.id)
    setEditingField({ fieldValue: item.fieldValue || "", status: item.status || "USER_CONFIRMED" })
  }

  const saveField = async (fieldId: number) => {
    if (!selectedId) return
    setError("")
    try {
      await api.put(`/inquiry-review/cases/${selectedId}/requirements/${fieldId}`, editingField)
      setEditingFieldId(null)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "保存字段失败")
    }
  }

  const createField = async () => {
    if (!selectedId || !newField.fieldLabel.trim()) return
    setError("")
    try {
      await api.post(`/inquiry-review/cases/${selectedId}/requirements`, newField)
      setNewField({ fieldLabel: "", fieldValue: "", status: "USER_CONFIRMED" })
      setNewFieldOpen(false)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "新增字段失败")
    }
  }

  const deleteField = async (fieldId: number) => {
    if (!selectedId) return
    setError("")
    try {
      await api.delete(`/inquiry-review/cases/${selectedId}/requirements/${fieldId}`)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "删除字段失败")
    }
  }

  const openMissingEditor = (item?: MissingField) => {
    setActionMode("missing")
    setEditingActionId(item?.id ?? null)
    setActionForm({
      fieldKey: item?.fieldKey || "",
      reason: item?.reason || "",
      questionEn: item?.questionEn || "",
      priority: item?.priority || "MEDIUM",
      riskType: "SPEC",
      level: "MEDIUM",
      title: "",
      description: "",
      suggestion: "",
    })
    setActionEditorOpen(true)
  }

  const openRiskEditor = (item?: RiskFlag) => {
    setActionMode("risk")
    setEditingActionId(item?.id ?? null)
    setActionForm({
      fieldKey: "",
      reason: "",
      questionEn: "",
      priority: "MEDIUM",
      riskType: item?.riskType || "SPEC",
      level: item?.level || "MEDIUM",
      title: item?.title || "",
      description: item?.description || "",
      suggestion: item?.suggestion || "",
    })
    setActionEditorOpen(true)
  }

  const saveActionItem = async () => {
    if (!selectedId) return
    setError("")
    try {
      if (actionMode === "missing") {
        const payload = {
          fieldKey: actionForm.fieldKey,
          reason: actionForm.reason,
          questionEn: actionForm.questionEn,
          priority: actionForm.priority,
        }
        if (editingActionId) {
          await api.put(`/inquiry-review/cases/${selectedId}/missing-fields/${editingActionId}`, payload)
        } else {
          await api.post(`/inquiry-review/cases/${selectedId}/missing-fields`, payload)
        }
      } else {
        const payload = {
          riskType: actionForm.riskType,
          level: actionForm.level,
          title: actionForm.title,
          description: actionForm.description,
          suggestion: actionForm.suggestion,
        }
        if (editingActionId) {
          await api.put(`/inquiry-review/cases/${selectedId}/risks/${editingActionId}`, payload)
        } else {
          await api.post(`/inquiry-review/cases/${selectedId}/risks`, payload)
        }
      }
      setActionEditorOpen(false)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "保存项目失败")
    }
  }

  const deleteMissing = async (id: number) => {
    if (!selectedId) return
    setError("")
    try {
      await api.delete(`/inquiry-review/cases/${selectedId}/missing-fields/${id}`)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "删除缺失项失败")
    }
  }

  const deleteRisk = async (id: number) => {
    if (!selectedId) return
    setError("")
    try {
      await api.delete(`/inquiry-review/cases/${selectedId}/risks/${id}`)
      await fetchDetail(selectedId)
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "删除风险项失败")
    }
  }

  const updateStatus = async (status: string) => {
    if (!selectedId) return
    setError("")
    try {
      const { data } = await api.put(`/inquiry-review/cases/${selectedId}/status`, { status })
      setDetail((prev) => prev ? { ...prev, case: data.case } : prev)
      await fetchCases()
      await fetchSummary()
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "更新状态失败")
    }
  }

  const createInternalTask = async () => {
    if (!selectedId || creatingTask) return
    setCreatingTask(true)
    setError("")
    try {
      const { data } = await api.post(`/inquiry-review/cases/${selectedId}/create-task`)
      setDetail(data.detail)
      await fetchCases()
      await fetchSummary()
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "创建内部任务失败")
    } finally {
      setCreatingTask(false)
    }
  }

  const markCustomerAsked = async () => {
    if (!selectedId || markingAsked) return
    setMarkingAsked(true)
    setError("")
    try {
      const { data } = await api.post(`/inquiry-review/cases/${selectedId}/mark-customer-asked`, {
        emailDraft,
        followUpPlan: taskDraft.followUpPlan,
        nextFollowUpAt: taskDraft.nextFollowUpAt,
      })
      setDetail(data.detail)
      setEmailDraft(data.detail?.quoteTaskDraft?.emailDraft || emailDraft)
      setTaskDraft(buildTaskDraftState(data.detail?.quoteTaskDraft))
      await fetchCases()
      await fetchSummary()
      await fetchSummary()
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || "记录追问动作失败")
    } finally {
      setMarkingAsked(false)
    }
  }

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <div className="page-kicker">INQUIRY REVIEW AGENT</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">询盘资料审查 Agent</h1>
            <p className="mt-2 text-sm text-muted-foreground">把客户邮件、Excel、PDF 和补充资料整理成结构化需求、缺失项、风险项和内部报价任务。</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={fetchCases} disabled={loadingCases}>
              <RefreshCw size={14} className={loadingCases ? "animate-spin" : ""} />
              刷新
            </Button>
            <Button variant="gradient" size="sm" onClick={() => setNewCaseOpen(true)}>
              <Plus size={14} />
              新建案件
            </Button>
          </div>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-2 rounded-[8px] border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
          <AlertCircle size={16} />
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-3">
              <CardTitle className="flex items-center gap-2">
                <CheckCircle2 size={16} />
                今日待办
              </CardTitle>
              <Badge variant={summary?.highPriorityTodos ? "destructive" : "secondary"} className="text-[10px]">
                高优先级 {summary?.highPriorityTodos ?? 0}
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            {!summary?.todos?.length ? (
              <p className="rounded-[10px] border border-dashed border-[var(--ui-border)] px-4 py-5 text-center text-xs font-semibold text-muted-foreground">
                暂无待办。当前询盘池没有明显阻塞项。
              </p>
            ) : (
              <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
                {summary.todos.slice(0, 6).map((todo) => (
                  <button
                    key={`${todo.caseId}-${todo.type}-${todo.title}`}
                    type="button"
                    onClick={() => setSelectedId(todo.caseId)}
                    className="rounded-[12px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3 text-left transition-colors hover:border-[var(--ui-border-accent)] hover:bg-[var(--ui-muted)] active:scale-[0.99]"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <Badge variant={todo.priority === "HIGH" ? "destructive" : todo.priority === "MEDIUM" ? "warning" : "secondary"} className="text-[10px]">
                        {todo.priority}
                      </Badge>
                      <span className="text-[10px] font-bold text-muted-foreground">{todo.caseNo}</span>
                    </div>
                    <p className="mt-2 text-sm font-black">{todo.title}</p>
                    <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-muted-foreground">{todo.action}</p>
                    <p className="mt-2 truncate text-[11px] font-semibold text-muted-foreground">{todo.customerName || todo.caseTitle}</p>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>案件漏斗</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-2">
              <Metric label="全部案件" value={String(summary?.totalCases ?? 0)} />
              <Metric label="开放案件" value={String(summary?.openCases ?? 0)} />
            </div>
            <div className="mt-3 grid grid-cols-5 gap-1.5">
              {["DRAFT", "REVIEWING", "WAITING_CUSTOMER", "READY_TO_QUOTE", "CLOSED"].map((status) => (
                <div key={status} className="rounded-[9px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] px-2 py-2 text-center">
                  <div className="text-sm font-black">{summary?.funnel?.[status] ?? 0}</div>
                  <div className="mt-1 text-[9px] font-bold text-muted-foreground">{statusLabel[status]}</div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[300px_minmax(0,1fr)_380px]">
        <Card className="min-h-[680px]">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2">
              <Inbox size={16} />
              询盘案件
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <ScrollArea className="h-[620px] px-3 pb-3">
              {loadingCases && cases.length === 0 ? (
                <div className="flex h-40 items-center justify-center">
                  <Loader2 size={22} className="animate-spin text-muted-foreground" />
                </div>
              ) : cases.length === 0 ? (
                <EmptyPanel title="还没有询盘案件" text="先创建一个案件，再上传客户资料。" />
              ) : (
                <div className="space-y-2">
                  {cases.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => setSelectedId(item.id)}
                      className={cn(
                        "w-full rounded-[12px] border p-3 text-left transition-all duration-200 active:scale-[0.99]",
                        selectedId === item.id
                          ? "border-[var(--ui-border-accent)] bg-[var(--ui-accent)]"
                          : "border-[var(--ui-border)] bg-[var(--ui-surface)] hover:bg-[var(--ui-muted)]"
                      )}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-black text-[var(--ui-text)]">{item.title}</p>
                          <p className="mt-1 truncate text-[11px] font-semibold text-[var(--ui-text-muted)]">{item.customerName || "未填写客户"}</p>
                        </div>
                        <Badge variant="secondary" className="shrink-0 text-[10px]">{statusLabel[item.status] || item.status}</Badge>
                      </div>
                      <div className="mt-3 flex items-center justify-between text-[10px] font-bold text-[var(--ui-text-muted)]">
                        <span>{item.caseNo}</span>
                        <span>{formatDate(item.updatedAt)}</span>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </ScrollArea>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <ClipboardCheck size={16} />
                    {selectedCase?.title || "选择一个询盘案件"}
                  </CardTitle>
                  <p className="mt-2 text-xs text-muted-foreground">
                    {selectedCase ? `${selectedCase.caseNo} · ${selectedCase.customerName || "未填写客户"} · ${selectedCase.country || "国家未填"}` : "案件详情会显示在这里"}
                  </p>
                </div>
                {selectedCase && (
                  <div className="flex flex-wrap items-center gap-2">
                    <select
                      value={selectedCase.status}
                      onChange={(event) => updateStatus(event.target.value)}
                      className="h-9 rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-3 text-xs font-black"
                    >
                      <option value="DRAFT">草稿</option>
                      <option value="REVIEWING">审查中</option>
                      <option value="WAITING_CUSTOMER">待客户确认</option>
                      <option value="READY_TO_QUOTE">可报价</option>
                      <option value="CLOSED">已关闭</option>
                    </select>
                    <Button variant="outline" size="sm" onClick={createInternalTask} disabled={creatingTask || !detail?.quoteTaskDraft}>
                      {creatingTask ? <Loader2 size={14} className="animate-spin" /> : <Zap size={14} />}
                      创建任务
                    </Button>
                  </div>
                )}
                {selectedCase && (
                  <div className="grid grid-cols-3 gap-2 text-center">
                    <Metric label="资料" value={String(detail?.artifacts.length ?? 0)} />
                    <Metric label="字段" value={String(detail?.requirements.length ?? 0)} />
                    <Metric label="完整度" value={`${completion}%`} />
                  </div>
                )}
              </div>
            </CardHeader>
            <CardContent>
              {loadingDetail ? (
                <div className="flex h-56 items-center justify-center">
                  <Loader2 size={24} className="animate-spin text-muted-foreground" />
                </div>
              ) : !detail ? (
                <EmptyPanel title="还没有选中案件" text="左侧选择或新建一个询盘案件后开始整理资料。" />
              ) : (
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
                  <InfoCell label="联系人" value={detail.case.contactName || "未填写"} />
                  <InfoCell label="邮箱" value={detail.case.contactEmail || "未填写"} />
                  <InfoCell label="来源" value={detail.case.source || "未填写"} />
                  <InfoCell label="状态" value={statusLabel[detail.case.status] || detail.case.status} />
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <CardTitle className="flex items-center gap-2">
                  <Upload size={16} />
                  客户资料区
                </CardTitle>
                <div className="flex flex-wrap items-center gap-2">
                  <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    accept=".pdf,.doc,.docx,.txt,.xlsx,.xls,.csv,.eml"
                    onChange={(event) => handleFileUpload(event.target.files?.[0])}
                  />
                  <Button variant="outline" size="sm" disabled={!selectedId || uploading} onClick={() => fileInputRef.current?.click()}>
                    {uploading ? <Loader2 size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}
                    上传附件
                  </Button>
                  <Button variant="secondary" size="sm" disabled={!selectedId || uploading || !pasteText.trim()} onClick={handlePaste}>
                    {uploading ? <Loader2 size={14} className="animate-spin" /> : <MailPlus size={14} />}
                    保存粘贴资料
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-[220px_minmax(0,1fr)]">
                <div className="space-y-2">
                  <Label>资料标题</Label>
                  <Input value={pasteTitle} onChange={(event) => setPasteTitle(event.target.value)} placeholder="客户邮件正文" />
                </div>
                <div className="space-y-2">
                  <Label>粘贴客户邮件或聊天记录</Label>
                  <Textarea
                    value={pasteText}
                    onChange={(event) => setPasteText(event.target.value)}
                    placeholder="粘贴客户邮件、WhatsApp/微信沟通记录、产品需求说明..."
                    className="min-h-[104px]"
                    disabled={!selectedId}
                  />
                </div>
              </div>

              <Separator />

              {!hasArtifacts ? (
                <EmptyPanel title="暂无客户资料" text="支持粘贴邮件正文，或上传 PDF、Word、TXT、Excel 规格表。" />
              ) : (
                <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                  {detail?.artifacts.map((artifact) => (
                    <div key={artifact.id} className="rounded-[12px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <FileText size={14} className="text-[var(--ui-accent-strong)]" />
                            <p className="truncate text-sm font-black">{artifact.fileName}</p>
                          </div>
                          <p className="mt-1 text-[11px] font-semibold text-muted-foreground">
                            {artifact.fileType} · {artifact.sourceType}
                          </p>
                        </div>
                        <Badge variant={artifact.parseStatus === "SUCCESS" ? "success" : "destructive"} className="shrink-0 text-[10px]">
                          {artifact.parseStatus === "SUCCESS" ? "已解析" : "失败"}
                        </Badge>
                      </div>
                      <p className="mt-3 line-clamp-3 text-xs leading-relaxed text-[var(--ui-text-muted)]">
                        {artifact.parseStatus === "SUCCESS" ? artifact.contentPreview || "资料已保存，暂无预览。" : artifact.parseError}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <CardTitle>结构化需求</CardTitle>
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" disabled={!selectedId} onClick={() => setNewFieldOpen(true)}>
                    <Plus size={14} />
                    新增字段
                  </Button>
                  <Button variant="gradient" size="sm" disabled={!hasArtifacts || analyzing} onClick={analyzeCase}>
                    {analyzing ? <Loader2 size={14} className="animate-spin" /> : <ClipboardCheck size={14} />}
                    {analyzing ? "审查中..." : "开始 AI 审查"}
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {detail?.requirements.length ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>字段</TableHead>
                      <TableHead>内容</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead className="w-[112px] text-right">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.requirements.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell className="font-bold">{item.fieldLabel}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {editingFieldId === item.id ? (
                            <Textarea
                              value={editingField.fieldValue}
                              onChange={(event) => setEditingField((prev) => ({ ...prev, fieldValue: event.target.value }))}
                              className="min-h-[64px]"
                            />
                          ) : (
                            <div>
                              <p>{item.fieldValue || "未填写"}</p>
                              {item.sourceEvidence && <p className="mt-1 text-[10px] text-muted-foreground/70">{item.sourceEvidence}</p>}
                            </div>
                          )}
                        </TableCell>
                        <TableCell>
                          {editingFieldId === item.id ? (
                            <select
                              value={editingField.status}
                              onChange={(event) => setEditingField((prev) => ({ ...prev, status: event.target.value }))}
                              className="h-9 rounded-[8px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-2 text-xs font-bold"
                            >
                              <option value="USER_CONFIRMED">已确认</option>
                              <option value="NEED_CONFIRM">待客户确认</option>
                              <option value="MISSING">缺失</option>
                              <option value="AI_EXTRACTED">AI 提取</option>
                            </select>
                          ) : (
                            <StatusBadge status={item.status} />
                          )}
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-1">
                            {editingFieldId === item.id ? (
                              <>
                                <Button variant="ghost" size="icon-sm" onClick={() => saveField(item.id)} title="保存">
                                  <Check size={14} />
                                </Button>
                                <Button variant="ghost" size="icon-sm" onClick={() => setEditingFieldId(null)} title="取消">
                                  <X size={14} />
                                </Button>
                              </>
                            ) : (
                              <>
                                <Button variant="ghost" size="icon-sm" onClick={() => startEditField(item)} title="编辑">
                                  <Pencil size={14} />
                                </Button>
                                <Button variant="ghost" size="icon-sm" onClick={() => deleteField(item.id)} title="删除">
                                  <Trash2 size={14} />
                                </Button>
                              </>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
                  {fieldGroups.map((field) => (
                    <div key={field} className="rounded-[10px] border border-dashed border-[var(--ui-border)] px-3 py-2 text-xs font-bold text-muted-foreground">
                      {field}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <AlertCircle size={16} />
                  缺失项与风险
                </CardTitle>
                <div className="flex items-center gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openMissingEditor()} disabled={!selectedId} title="新增缺失项">
                    <Plus size={14} />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openRiskEditor()} disabled={!selectedId} title="新增风险项">
                    <AlertCircle size={14} />
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <SectionEmpty active={!detail?.missingFields.length} text="AI 审查后会列出报价前必须追问的问题。" />
              {detail?.missingFields.map((item) => (
                <ActionItem
                  key={item.id}
                  label={item.priority}
                  title={item.fieldKey}
                  text={item.questionEn || item.reason}
                  onEdit={() => openMissingEditor(item)}
                  onDelete={() => deleteMissing(item.id)}
                />
              ))}
              <Separator />
              <SectionEmpty active={!detail?.risks.length} text="AI 审查后会显示规格、交期、合规、物流等风险。" />
              {detail?.risks.map((item) => (
                <ActionItem
                  key={item.id}
                  label={item.level}
                  title={item.title}
                  text={item.suggestion || item.description}
                  onEdit={() => openRiskEditor(item)}
                  onDelete={() => deleteRisk(item.id)}
                />
              ))}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <MailPlus size={16} />
                  英文追问邮件
                </CardTitle>
                <div className="flex items-center gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={copyEmail} disabled={!emailDraft.trim()} title="复制邮件">
                    <Copy size={14} />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={markCustomerAsked} disabled={!selectedId || markingAsked || !emailDraft.trim()} title="记录已追问客户">
                    {markingAsked ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <Textarea
                value={emailDraft}
                onChange={(event) => setEmailDraft(event.target.value)}
                placeholder="AI 审查后会在这里生成英文追问邮件。你也可以先手动编辑并保存。"
                className="min-h-[180px]"
                disabled={!selectedId}
              />
              <div className="grid grid-cols-1 gap-2">
                <DraftArea label="跟进计划" value={taskDraft.followUpPlan} onChange={(value) => setTaskDraft((prev) => ({ ...prev, followUpPlan: value }))} disabled={!selectedId} />
                <div className="space-y-2">
                  <Label>下次跟进时间</Label>
                  <Input
                    type="datetime-local"
                    value={taskDraft.nextFollowUpAt}
                    onChange={(event) => setTaskDraft((prev) => ({ ...prev, nextFollowUpAt: event.target.value }))}
                    disabled={!selectedId}
                  />
                </div>
              </div>
              <Button className="w-full" variant="outline" onClick={markCustomerAsked} disabled={!selectedId || markingAsked || !emailDraft.trim()}>
                {markingAsked ? <Loader2 size={14} className="animate-spin" /> : <CalendarClock size={14} />}
                已追问客户，进入跟进
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <PackageCheck size={16} />
                  报价准备包
                </CardTitle>
                <Badge variant={quoteReadiness >= 70 ? "success" : quoteReadiness >= 40 ? "warning" : "secondary"} className="text-[10px]">
                  完整度 {quoteReadiness}%
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <Field label="任务标题" value={taskDraft.taskTitle} onChange={(value) => setTaskDraft((prev) => ({ ...prev, taskTitle: value }))} />
              <DraftArea label="产品/规格摘要" value={taskDraft.productSummary} onChange={(value) => setTaskDraft((prev) => ({ ...prev, productSummary: value }))} disabled={!selectedId} />
              <DraftArea label="报价假设" value={taskDraft.quoteAssumptions} onChange={(value) => setTaskDraft((prev) => ({ ...prev, quoteAssumptions: value }))} disabled={!selectedId} />
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <Field label="MOQ" value={taskDraft.moq} onChange={(value) => setTaskDraft((prev) => ({ ...prev, moq: value }))} placeholder="例如：100 sets" />
                <Field label="样品费" value={taskDraft.sampleFee} onChange={(value) => setTaskDraft((prev) => ({ ...prev, sampleFee: value }))} placeholder="待确认" />
                <Field label="样品交期" value={taskDraft.sampleLeadTime} onChange={(value) => setTaskDraft((prev) => ({ ...prev, sampleLeadTime: value }))} placeholder="例如：7-10 days" />
                <Field label="大货交期" value={taskDraft.massProductionLeadTime} onChange={(value) => setTaskDraft((prev) => ({ ...prev, massProductionLeadTime: value }))} placeholder="例如：30-35 days" />
                <Field label="贸易条款" value={taskDraft.tradeTerm} onChange={(value) => setTaskDraft((prev) => ({ ...prev, tradeTerm: value }))} placeholder="FOB / CIF / DDP" />
                <Field label="目的港" value={taskDraft.destinationPort} onChange={(value) => setTaskDraft((prev) => ({ ...prev, destinationPort: value }))} placeholder="Los Angeles" />
                <Field label="付款条款" value={taskDraft.paymentTerm} onChange={(value) => setTaskDraft((prev) => ({ ...prev, paymentTerm: value }))} placeholder="T/T 30% deposit..." />
                <SelectLine label="协作角色" value={taskDraft.assigneeRole} onChange={(value) => setTaskDraft((prev) => ({ ...prev, assigneeRole: value }))} options={["SALES", "ENGINEERING", "PURCHASING", "MANAGER"]} />
              </div>
              <DraftArea label="包装要求" value={taskDraft.packagingRequirement} onChange={(value) => setTaskDraft((prev) => ({ ...prev, packagingRequirement: value }))} disabled={!selectedId} />
              <DraftArea label="已知信息" value={taskDraft.knownInfo} onChange={(value) => setTaskDraft((prev) => ({ ...prev, knownInfo: value }))} disabled={!selectedId} />
              <DraftArea label="缺失信息" value={taskDraft.missingInfo} onChange={(value) => setTaskDraft((prev) => ({ ...prev, missingInfo: value }))} disabled={!selectedId} />
              <DraftArea label="风险摘要" value={taskDraft.riskSummary} onChange={(value) => setTaskDraft((prev) => ({ ...prev, riskSummary: value }))} disabled={!selectedId} />
              <Button className="w-full" variant="gradient" onClick={saveDrafts} disabled={!selectedId || savingDraft}>
                {savingDraft ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                保存草稿
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={newCaseOpen} onOpenChange={setNewCaseOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>新建询盘案件</DialogTitle>
            <DialogDescription>先录入客户和询盘基础信息，后续资料都会归档到这个案件下。</DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Field label="询盘标题" value={newCase.title} onChange={(value) => setNewCase((prev) => ({ ...prev, title: value }))} placeholder="US aluminum window inquiry" />
            <Field label="客户公司" value={newCase.customerName} onChange={(value) => setNewCase((prev) => ({ ...prev, customerName: value }))} placeholder="Brightline Builders" />
            <Field label="联系人" value={newCase.contactName} onChange={(value) => setNewCase((prev) => ({ ...prev, contactName: value }))} placeholder="Michael" />
            <Field label="联系邮箱" value={newCase.contactEmail} onChange={(value) => setNewCase((prev) => ({ ...prev, contactEmail: value }))} placeholder="buyer@example.com" />
            <Field label="国家/地区" value={newCase.country} onChange={(value) => setNewCase((prev) => ({ ...prev, country: value }))} placeholder="US" />
            <Field label="来源渠道" value={newCase.source} onChange={(value) => setNewCase((prev) => ({ ...prev, source: value }))} placeholder="email" />
            <div className="space-y-2 sm:col-span-2">
              <Label>原始邮件正文</Label>
              <Textarea
                value={newCase.emailText}
                onChange={(event) => setNewCase((prev) => ({ ...prev, emailText: event.target.value }))}
                placeholder="可选：创建案件时直接粘贴客户第一封邮件..."
                className="min-h-[140px]"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setNewCaseOpen(false)}>取消</Button>
            <Button onClick={handleCreateCase} disabled={creating}>
              {creating ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
              创建案件
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={newFieldOpen} onOpenChange={setNewFieldOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新增需求字段</DialogTitle>
            <DialogDescription>用于补充 AI 未识别到、但报价前必须记录的信息。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Field label="字段名称" value={newField.fieldLabel} onChange={(value) => setNewField((prev) => ({ ...prev, fieldLabel: value }))} placeholder="例如：玻璃配置" />
            <DraftArea label="字段内容" value={newField.fieldValue} onChange={(value) => setNewField((prev) => ({ ...prev, fieldValue: value }))} />
            <div className="space-y-2">
              <Label>状态</Label>
              <select
                value={newField.status}
                onChange={(event) => setNewField((prev) => ({ ...prev, status: event.target.value }))}
                className="h-10 w-full rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-3 text-sm font-bold"
              >
                <option value="USER_CONFIRMED">已确认</option>
                <option value="NEED_CONFIRM">待客户确认</option>
                <option value="MISSING">缺失</option>
                <option value="AI_EXTRACTED">AI 提取</option>
              </select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setNewFieldOpen(false)}>取消</Button>
            <Button onClick={createField} disabled={!newField.fieldLabel.trim()}>
              <Plus size={14} />
              添加字段
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={actionEditorOpen} onOpenChange={setActionEditorOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{actionMode === "missing" ? "维护缺失项" : "维护风险项"}</DialogTitle>
            <DialogDescription>
              {actionMode === "missing" ? "用于管理报价前必须向客户确认的问题。" : "用于记录报价前需要内部注意或处理的风险。"}
            </DialogDescription>
          </DialogHeader>
          {actionMode === "missing" ? (
            <div className="space-y-3">
              <Field label="缺失字段" value={actionForm.fieldKey} onChange={(value) => setActionForm((prev) => ({ ...prev, fieldKey: value }))} placeholder="例如：玻璃配置" />
              <DraftArea label="原因" value={actionForm.reason} onChange={(value) => setActionForm((prev) => ({ ...prev, reason: value }))} />
              <DraftArea label="英文追问句" value={actionForm.questionEn} onChange={(value) => setActionForm((prev) => ({ ...prev, questionEn: value }))} />
              <SelectLine label="优先级" value={actionForm.priority} onChange={(value) => setActionForm((prev) => ({ ...prev, priority: value }))} options={["HIGH", "MEDIUM", "LOW"]} />
            </div>
          ) : (
            <div className="space-y-3">
              <SelectLine label="风险类型" value={actionForm.riskType} onChange={(value) => setActionForm((prev) => ({ ...prev, riskType: value }))} options={["SPEC", "PRICE", "DELIVERY", "PAYMENT", "COMPLIANCE", "LOGISTICS"]} />
              <SelectLine label="风险等级" value={actionForm.level} onChange={(value) => setActionForm((prev) => ({ ...prev, level: value }))} options={["HIGH", "MEDIUM", "LOW"]} />
              <Field label="风险标题" value={actionForm.title} onChange={(value) => setActionForm((prev) => ({ ...prev, title: value }))} placeholder="例如：玻璃配置不明确" />
              <DraftArea label="风险说明" value={actionForm.description} onChange={(value) => setActionForm((prev) => ({ ...prev, description: value }))} />
              <DraftArea label="处理建议" value={actionForm.suggestion} onChange={(value) => setActionForm((prev) => ({ ...prev, suggestion: value }))} />
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionEditorOpen(false)}>取消</Button>
            <Button onClick={saveActionItem}>
              <CheckCircle2 size={14} />
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
    </div>
  )
}

function DraftArea({ label, value, onChange, disabled }: { label: string; value: string; onChange: (value: string) => void; disabled?: boolean }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Textarea value={value} onChange={(event) => onChange(event.target.value)} className="min-h-[72px]" disabled={disabled} />
    </div>
  )
}

function SelectLine({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: string[] }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 w-full rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface)] px-3 text-sm font-bold"
      >
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] px-3 py-2">
      <div className="text-[10px] font-bold text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm font-black">{value}</div>
    </div>
  )
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3">
      <div className="text-[11px] font-bold text-muted-foreground">{label}</div>
      <div className="mt-2 truncate text-sm font-black">{value}</div>
    </div>
  )
}

function EmptyPanel({ title, text }: { title: string; text: string }) {
  return (
    <div className="m-3 rounded-[12px] border border-dashed border-[var(--ui-border)] p-6 text-center">
      <p className="text-sm font-black">{title}</p>
      <p className="mt-1 text-xs text-muted-foreground">{text}</p>
    </div>
  )
}

function SectionEmpty({ active, text }: { active: boolean; text: string }) {
  if (!active) return null
  return <p className="rounded-[10px] border border-dashed border-[var(--ui-border)] px-3 py-3 text-xs leading-relaxed text-muted-foreground">{text}</p>
}

function ActionItem({ label, title, text, onEdit, onDelete }: { label: string; title: string; text: string; onEdit?: () => void; onDelete?: () => void }) {
  return (
    <div className="rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-black">{title}</p>
        <div className="flex items-center gap-1">
          <Badge variant={label === "HIGH" ? "destructive" : label === "MEDIUM" ? "warning" : "secondary"} className="text-[10px]">{label}</Badge>
          {onEdit && (
            <Button variant="ghost" size="icon-sm" onClick={onEdit} title="编辑">
              <Pencil size={13} />
            </Button>
          )}
          {onDelete && (
            <Button variant="ghost" size="icon-sm" onClick={onDelete} title="关闭">
              <X size={13} />
            </Button>
          )}
        </div>
      </div>
      <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{text}</p>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const labelMap: Record<string, string> = {
    USER_CONFIRMED: "已确认",
    NEED_CONFIRM: "待客户确认",
    MISSING: "缺失",
    AI_EXTRACTED: "AI 提取",
  }
  const variant: "success" | "destructive" | "warning" | "secondary" =
    status === "USER_CONFIRMED" ? "success" : status === "MISSING" ? "destructive" : status === "NEED_CONFIRM" ? "warning" : "secondary"
  return <Badge variant={variant}>{labelMap[status] || status}</Badge>
}

function formatDate(value: string) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" })
}
