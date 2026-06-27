import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Workflow,
  GripVertical,
  Plus,
  Play,
  Save,
  ArrowRight,
  Zap,
  Bot,
  Globe,
  Cog,
  Mail,
  FileText,
  Database,
  BarChart3,
  CheckCircle2,
  X,
} from "lucide-react"
import { cn } from "@/lib/utils"

interface WorkflowNode {
  id: string
  type: "trigger" | "agent" | "tool" | "output"
  label: string
  description: string
  icon: React.ReactNode
  color: string
  borderColor: string
}

const nodeTypes: Record<string, { nodes: WorkflowNode[] }> = {
  trigger: {
    nodes: [
      {
        id: "t1", type: "trigger", label: "定时触发", description: "按 cron 表达式定时执行",
        icon: <Zap size={16} />, color: "bg-amber-100 text-amber-600", borderColor: "border-amber-300",
      },
      {
        id: "t2", type: "trigger", label: "Webhook", description: "HTTP 事件驱动触发",
        icon: <Globe size={16} />, color: "bg-[#EEF7F3] text-[#1F5F53]", borderColor: "border-[#D7E8E0]",
      },
      {
        id: "t3", type: "trigger", label: "邮件触发", description: "收到新邮件时触发",
        icon: <Mail size={16} />, color: "bg-emerald-100 text-emerald-600", borderColor: "border-emerald-300",
      },
    ],
  },
  agent: {
    nodes: [
      {
        id: "a1", type: "agent", label: "客服 Agent", description: "自动回复客户咨询",
        icon: <Bot size={16} />, color: "bg-[#E9F7F5] text-[#087C78]", borderColor: "border-[#BFE2DA]",
      },
      {
        id: "a2", type: "agent", label: "翻译 Agent", description: "多语言翻译处理",
        icon: <Globe size={16} />, color: "bg-[#EEF7F3] text-[#1F5F53]", borderColor: "border-[#D7E8E0]",
      },
      {
        id: "a3", type: "agent", label: "分析 Agent", description: "市场数据分析",
        icon: <BarChart3 size={16} />, color: "bg-[#F4F6F5] text-[#343A35]", borderColor: "border-[#E4E8E5]",
      },
    ],
  },
  tool: {
    nodes: [
      {
        id: "to1", type: "tool", label: "知识库检索", description: "RAG 检索产品知识",
        icon: <Database size={16} />, color: "bg-[#F4F6F5] text-[#74766F]", borderColor: "border-[#E4E8E5]",
      },
      {
        id: "to2", type: "tool", label: "SEO 分析", description: "关键词与排名分析",
        icon: <BarChart3 size={16} />, color: "bg-[#EEF7F3] text-[#1F5F53]", borderColor: "border-[#D7E8E0]",
      },
      {
        id: "to3", type: "tool", label: "邮件发送", description: "自动发送回复邮件",
        icon: <Mail size={16} />, color: "bg-[#E9F7F5] text-[#087C78]", borderColor: "border-[#BFE2DA]",
      },
    ],
  },
  output: {
    nodes: [
      {
        id: "o1", type: "output", label: "邮件回复", description: "输出到邮件",
        icon: <Mail size={16} />, color: "bg-[#EEF7F3] text-[#1F5F53]", borderColor: "border-[#D7E8E0]",
      },
      {
        id: "o2", type: "output", label: "数据报表", description: "导出分析报告",
        icon: <FileText size={16} />, color: "bg-amber-50 text-amber-700", borderColor: "border-amber-200",
      },
      {
        id: "o3", type: "output", label: "API 回调", description: "发送到外部 API",
        icon: <Cog size={16} />, color: "bg-[#F4F6F5] text-[#74766F]", borderColor: "border-[#E4E8E5]",
      },
    ],
  },
}

const columnMeta: Record<string, { title: string; desc: string }> = {
  trigger: { title: "Trigger", desc: "选择触发条件" },
  agent: { title: "Agent", desc: "选择执行 Agent" },
  tool: { title: "Tool", desc: "选择工具调用" },
  output: { title: "Output", desc: "选择输出方式" },
}

export default function WorkflowBuilder() {
  const [pipeline, setPipeline] = useState<Array<{ type: string; node: WorkflowNode | null }>>([
    { type: "trigger", node: null },
    { type: "agent", node: null },
    { type: "tool", node: null },
    { type: "output", node: null },
  ])
  const [draggedNode, setDraggedNode] = useState<WorkflowNode | null>(null)

  const addNodeToStage = (stageIndex: number, node: WorkflowNode) => {
    const updated = [...pipeline]
    updated[stageIndex] = { ...updated[stageIndex], node }
    setPipeline(updated)
  }

  const removeNodeFromStage = (stageIndex: number) => {
    const updated = [...pipeline]
    updated[stageIndex] = { ...updated[stageIndex], node: null }
    setPipeline(updated)
  }

  const handleDragStart = (node: WorkflowNode) => {
    setDraggedNode(node)
  }

  const handleDrop = (stageIndex: number) => {
    if (draggedNode && stageIndex !== undefined) {
      addNodeToStage(stageIndex, draggedNode)
      setDraggedNode(null)
    }
  }

  const clearPipeline = () => {
    setPipeline(pipeline.map((p) => ({ ...p, node: null })))
  }

  const isPipelineComplete = pipeline.every((p) => p.node !== null)

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="page-hero p-5 sm:p-6">
        <div className="relative z-[1] flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <div className="page-kicker">AI WORKFLOW ROUTING</div>
            <h1 className="mt-3 text-2xl font-bold tracking-tight text-foreground">Workflow Builder</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              Trigger → Agent → Tool → Output，拖拽编排询盘、知识库和渠道动作。
            </p>
          </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={clearPipeline}>
            <X size={14} />
            清空
          </Button>
          <Button variant="outline" size="sm">
            <Save size={14} />
            保存草稿
          </Button>
          <Button variant="gradient" size="sm" disabled={!isPipelineComplete}>
            <Play size={14} />
            运行工作流
          </Button>
        </div>
        </div>
      </div>

      {/* Pipeline Visualization */}
      <Card className="animate-fade-in-up">
        <CardHeader className="pb-3">
          <CardTitle className="text-base flex items-center gap-2">
            <Workflow size={17} className="text-primary" />
            编排管道
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-4 gap-4">
            {pipeline.map((stage, i) => (
              <div key={stage.type}>
                {/* Stage Header */}
                <div className="text-center mb-3">
                  <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {columnMeta[stage.type].title}
                  </p>
                  <p className="text-[10px] text-muted-foreground/60">{columnMeta[stage.type].desc}</p>
                </div>
                {/* Drop Zone */}
                <div
                  className={cn(
                    "min-h-[120px] rounded-[8px] border-2 border-dashed p-3 transition-all duration-200 flex items-center justify-center",
                    !stage.node
                      ? "border-border hover:border-primary/40 hover:bg-accent/20"
                      : "border-transparent",
                    draggedNode && !stage.node ? "border-primary/60 bg-accent/30" : ""
                  )}
                  onDragOver={(e) => {
                    e.preventDefault()
                    e.currentTarget.classList.add("border-primary/60", "bg-accent/30")
                  }}
                  onDragLeave={(e) => {
                    e.currentTarget.classList.remove("border-primary/60", "bg-accent/30")
                  }}
                  onDrop={(e) => {
                    e.preventDefault()
                    e.currentTarget.classList.remove("border-primary/60", "bg-accent/30")
                    handleDrop(i)
                  }}
                >
                  {stage.node ? (
                    <div
                      className={cn(
                        "w-full rounded-[8px] border bg-card p-3 group/item relative",
                        stage.node.borderColor
                      )}
                    >
                      <button
                        onClick={() => removeNodeFromStage(i)}
                        className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-muted border border-border flex items-center justify-center opacity-0 group-hover/item:opacity-100 transition-opacity hover:bg-destructive hover:text-destructive-foreground"
                      >
                        <X size={10} />
                      </button>
                      <div className="flex items-center gap-2.5">
                        <div className={`ai-orbit w-8 h-8 rounded-[8px] ${stage.node.color} flex items-center justify-center`}>
                          {stage.node.icon}
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-semibold truncate">{stage.node.label}</p>
                          <p className="text-[10px] text-muted-foreground truncate">{stage.node.description}</p>
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-1 text-muted-foreground/40">
                      <Plus size={20} />
                      <span className="text-[10px]">拖拽到此处</span>
                    </div>
                  )}
                </div>
                {/* Arrow (except after last) */}
                {i < pipeline.length - 1 && (
                  <div className="flex items-center justify-center mt-3">
                    <ArrowRight size={18} className="text-muted-foreground/30" />
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Completion Indicator */}
          <div className="mt-6 flex items-center gap-2 justify-center">
            {pipeline.map((stage, i) => (
              <div key={i} className="flex items-center gap-2">
                <div
                  className={cn(
                    "w-2.5 h-2.5 rounded-full transition-colors",
                    stage.node ? "bg-emerald-500" : "bg-muted-foreground/20"
                  )}
                />
                {i < pipeline.length - 1 && (
                  <div
                    className={cn(
                      "w-8 h-[2px] transition-colors",
                      stage.node && pipeline[i + 1]?.node ? "bg-emerald-300" : "bg-muted-foreground/10"
                    )}
                  />
                )}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Available Nodes */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {Object.entries(nodeTypes).map(([type, { nodes }], groupIdx) => (
          <Card key={type} className={`animate-fade-in-up stagger-${groupIdx + 1}`}>
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">{columnMeta[type].title}</CardTitle>
                <Badge variant="secondary" className="text-[10px]">{nodes.length}</Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-2">
              {nodes.map((node) => (
                <div
                  key={node.id}
                  draggable
                  onDragStart={(e) => {
                    e.dataTransfer.effectAllowed = "move"
                    handleDragStart(node)
                  }}
                  className={cn(
                    "flex items-center gap-2.5 px-3 py-2.5 rounded-[8px] border bg-card cursor-grab active:cursor-grabbing transition-all duration-200",
                    "hover:shadow-sm hover:border-primary/30 hover:bg-accent/20",
                    "active:scale-[0.98] active:shadow-inner",
                    node.borderColor
                  )}
                >
                  <GripVertical size={14} className="text-muted-foreground/40 flex-shrink-0" />
                  <div className={`w-7 h-7 rounded-[9px] ${node.color} flex items-center justify-center flex-shrink-0`}>
                    {node.icon}
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-semibold truncate">{node.label}</p>
                    <p className="text-[10px] text-muted-foreground truncate">{node.description}</p>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Tips */}
      <div className="trade-signal-card flex items-center gap-3 px-4 py-3 animate-fade-in-up">
        <CheckCircle2 size={16} className="text-primary flex-shrink-0" />
        <p className="text-xs text-muted-foreground">
          <span className="font-semibold text-foreground">操作提示：</span>
          从下方节点库拖拽组件到编排管道中，按 Trigger → Agent → Tool → Output 顺序组装你的自动化工作流。完成后点击「运行工作流」启动。
        </p>
      </div>
    </div>
  )
}
