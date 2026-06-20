import { useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  FileText,
  Plus,
  Edit3,
  Trash2,
  Loader2,
  Sparkles,
  Eye,
  X,
  Check,
} from "lucide-react"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

interface Template {
  id: string
  name: string
  description: string
  category: string
  template: string
  targetPlatform: string
  variables: string[]
  active: boolean
}

export default function Templates() {
  const [templates, setTemplates] = useState<Template[]>([])
  const [loading, setLoading] = useState(true)
  const [editMode, setEditMode] = useState(false)
  const [previewId, setPreviewId] = useState<string | null>(null)
  const [form, setForm] = useState({
    id: "",
    name: "",
    description: "",
    category: "copywriting",
    template: "",
    targetPlatform: "",
    variables: "",
    active: true,
  })

  useEffect(() => { loadTemplates() }, [])

  const loadTemplates = async () => {
    try {
      const { data } = await api.get("/copywriting/templates")
      setTemplates(Array.isArray(data) ? data : [])
    } catch {}
    setLoading(false)
  }

  const resetForm = () => {
    setForm({ id: "", name: "", description: "", category: "copywriting", template: "", targetPlatform: "", variables: "", active: true })
    setEditMode(false)
  }

  const editTemplate = (t: Template) => {
    setForm({
      id: t.id,
      name: t.name,
      description: t.description || "",
      category: t.category,
      template: t.template,
      targetPlatform: t.targetPlatform || "",
      variables: (t.variables || []).join(", "),
      active: t.active,
    })
    setEditMode(true)
  }

  const saveTemplate = async () => {
    try {
      const payload = {
        ...form,
        variables: form.variables.split(",").map((v) => v.trim()).filter(Boolean),
      }
      if (editMode) {
        await api.put(`/copywriting/templates/${form.id}`, payload)
      } else {
        await api.post("/copywriting/templates", payload)
      }
      resetForm()
      loadTemplates()
    } catch {}
  }

  const deleteTemplate = async (id: string) => {
    try {
      await api.delete(`/copywriting/templates/${id}`)
      loadTemplates()
    } catch {}
  }

  const toggleTemplate = async (id: string) => {
    try {
      await api.put(`/copywriting/templates/${id}/toggle`)
      loadTemplates()
    } catch {}
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Prompt 模板管理</h1>
          <p className="mt-1 text-sm text-muted-foreground">管理 AI 提示词模板 · 可复用 · 可协作</p>
        </div>
        <Button variant="gradient" size="sm" onClick={resetForm}>
          <Plus size={14} /> 新建模板
        </Button>
      </div>

      <Tabs defaultValue="list">
        <TabsList>
          <TabsTrigger value="list">模板列表</TabsTrigger>
          <TabsTrigger value="edit">{editMode ? "编辑模板" : "新建模板"}</TabsTrigger>
        </TabsList>

        <TabsContent value="list">
          {loading ? (
            <div className="flex justify-center py-16"><Loader2 className="animate-spin text-muted-foreground" /></div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {templates.map((t) => (
                <Card key={t.id} className={cn(!t.active && "opacity-60")}>
                  <CardHeader>
                    <div className="flex items-start justify-between">
                      <div>
                        <CardTitle className="text-sm">{t.name}</CardTitle>
                        <p className="text-[11px] text-muted-foreground mt-1 line-clamp-2">{t.description}</p>
                      </div>
                      <Badge variant={t.active ? "success" : "secondary"} className="text-[9px]">
                        {t.active ? "启用" : "禁用"}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className="flex flex-wrap gap-1 mb-2">
                      <Badge variant="secondary" className="text-[9px]">{t.category}</Badge>
                      {t.targetPlatform && <Badge variant="outline" className="text-[9px]">{t.targetPlatform}</Badge>}
                    </div>
                    <div className="flex gap-1.5">
                      <Button variant="outline" size="sm" onClick={() => editTemplate(t)}>
                        <Edit3 size={12} /> 编辑
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => toggleTemplate(t.id)}>
                        {t.active ? <X size={12} /> : <Check size={12} />}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => deleteTemplate(t.id)}>
                        <Trash2 size={12} className="text-destructive" />
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
              {templates.length === 0 && (
                <Card className="col-span-full">
                  <CardContent className="flex flex-col items-center py-16 text-muted-foreground">
                    <FileText size={48} className="mb-4 opacity-20" />
                    <p className="text-sm">暂无 Prompt 模板</p>
                    <p className="text-xs mt-1">点击"新建模板"开始创建</p>
                  </CardContent>
                </Card>
              )}
            </div>
          )}
        </TabsContent>

        <TabsContent value="edit">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{editMode ? "编辑模板" : "新建模板"}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>模板名称</Label>
                  <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                </div>
                <div className="space-y-2">
                  <Label>分类</Label>
                  <Input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
                </div>
              </div>
              <div className="space-y-2">
                <Label>描述</Label>
                <Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>目标平台</Label>
                <Input value={form.targetPlatform} onChange={(e) => setForm({ ...form, targetPlatform: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>变量 (逗号分隔)</Label>
                <Input
                  value={form.variables}
                  onChange={(e) => setForm({ ...form, variables: e.target.value })}
                  placeholder="productName, sellingPoints, country"
                />
              </div>
              <div className="space-y-2">
                <Label>Prompt 模板内容</Label>
                <Textarea
                  className="min-h-[200px] font-mono text-xs"
                  value={form.template}
                  onChange={(e) => setForm({ ...form, template: e.target.value })}
                  placeholder="输入 Prompt 模板，使用 {{variable}} 作为变量占位符..."
                />
              </div>
              <div className="flex gap-2">
                <Button variant="gradient" onClick={saveTemplate}>
                  <Check size={14} /> 保存
                </Button>
                <Button variant="outline" onClick={resetForm}>
                  <X size={14} /> 取消
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
