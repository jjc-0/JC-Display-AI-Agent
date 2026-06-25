import { useState, useRef, useCallback, useEffect } from "react"
import * as Dialog from "@radix-ui/react-dialog"
import {
  X, Download, RefreshCw, PenTool,
  ZoomIn, ZoomOut, RotateCcw, Maximize2,
} from "lucide-react"

interface ImageViewerProps {
  /** 图片 URL */
  src: string | null
  /** 图片提示词 (用于重新生成/编辑) */
  prompt?: string
  /** 关闭回调 */
  onClose: () => void
  /** 重新生成 */
  onRegenerate?: (prompt: string) => void
  /** 编辑图片 */
  onEdit?: (prompt: string, action: string) => void
}

export default function ImageViewer({ src, prompt, onClose, onRegenerate, onEdit }: ImageViewerProps) {
  const [scale, setScale] = useState(1)
  const [position, setPosition] = useState({ x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [editing, setEditing] = useState(false)
  const [editPrompt, setEditPrompt] = useState("")
  const containerRef = useRef<HTMLDivElement>(null)
  const MIN_SCALE = 0.5
  const MAX_SCALE = 5

  // 重置状态
  useEffect(() => {
    if (src) { setScale(1); setPosition({ x: 0, y: 0 }); setEditing(false); setEditPrompt("") }
  }, [src])

  // 快捷键
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (!src) return
      if (e.key === "Escape") onClose()
      if (e.key === "=" || e.key === "+") zoom(0.2)
      if (e.key === "-") zoom(-0.2)
      if (e.key === "0") { setScale(1); setPosition({ x: 0, y: 0 }) }
    }
    window.addEventListener("keydown", handler)
    return () => window.removeEventListener("keydown", handler)
  }, [src, scale])

  // 滚轮缩放
  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault()
    const delta = e.deltaY > 0 ? -0.1 : 0.1
    zoomAtPoint(delta, e.clientX, e.clientY)
  }, [scale, position])

  const zoom = (delta: number) => {
    setScale(s => Math.min(MAX_SCALE, Math.max(MIN_SCALE, Math.round((s + delta) * 10) / 10)))
  }

  const zoomAtPoint = (delta: number, cx: number, cy: number) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return
    const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale + delta))
    const ratio = newScale / scale - 1
    setPosition(p => ({
      x: p.x - (cx - rect.left - rect.width / 2 - p.x) * ratio,
      y: p.y - (cy - rect.top - rect.height / 2 - p.y) * ratio,
    }))
    setScale(newScale)
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (scale > 1) { setDragging(true); setDragStart({ x: e.clientX - position.x, y: e.clientY - position.y }) }
  }
  const handleMouseMove = (e: React.MouseEvent) => {
    if (dragging) setPosition({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y })
  }
  const handleMouseUp = () => setDragging(false)

  const resetZoom = () => { setScale(1); setPosition({ x: 0, y: 0 }) }
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) containerRef.current?.requestFullscreen()
    else document.exitFullscreen()
  }

  const handleSave = async () => {
    if (!src) return
    try {
      const res = await fetch(src)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = prompt ? prompt.slice(0, 20).replace(/[^a-zA-Z0-9\u4e00-\u9fff]/g, "_") + ".png" : "image.png"
      a.click()
      URL.revokeObjectURL(url)
    } catch { window.open(src, "_blank") }
  }

  const handleRegenerate = () => {
    if (onRegenerate && prompt) onRegenerate(prompt)
    onClose()
  }

  const handleEditSubmit = () => {
    if (onEdit && editPrompt.trim()) {
      onEdit(editPrompt.trim(), "edit")
      onClose()
    }
  }

  if (!src) return null

  return (
    <Dialog.Root open={!!src} onOpenChange={(open) => { if (!open) onClose() }}>
      <Dialog.Portal>
        {/* 背景遮罩 */}
        <Dialog.Overlay className="fixed inset-0 z-[100] bg-black/85 backdrop-blur-md" />

        {/* 内容 */}
        <Dialog.Content
          className="fixed inset-0 z-[101] flex flex-col outline-none"
          onPointerDownOutside={(e) => e.preventDefault()}
        >
          {/* ── 顶部工具栏 ── */}
          <div className="absolute top-0 left-0 right-0 z-10 flex items-center justify-between px-4 py-3 bg-gradient-to-b from-black/60 to-transparent">
            <div className="flex items-center gap-2">
              {prompt && (
                <span className="text-white/70 text-xs max-w-[300px] truncate hidden sm:block">
                  {prompt.length > 40 ? prompt.slice(0, 40) + "…" : prompt}
                </span>
              )}
            </div>

            <div className="flex items-center gap-1">
              {/* 缩放控制 */}
              <button onClick={() => zoom(-0.2)} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors" title="缩小 (-)">
                <ZoomOut size={18} />
              </button>
              <span className="text-white/60 text-xs w-10 text-center tabular-nums">{Math.round(scale * 100)}%</span>
              <button onClick={() => zoom(0.2)} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors" title="放大 (+)">
                <ZoomIn size={18} />
              </button>
              <button onClick={resetZoom} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors" title="重置 (0)">
                <RotateCcw size={16} />
              </button>
              <div className="w-px h-5 bg-white/20 mx-1" />

              {/* 功能按钮 */}
              <button onClick={handleSave} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5" title="保存图片">
                <Download size={18} />
                <span className="text-xs hidden sm:inline">保存</span>
              </button>

              {onRegenerate && (
                <button onClick={handleRegenerate} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5" title="重新生成">
                  <RefreshCw size={18} />
                  <span className="text-xs hidden sm:inline">重新生成</span>
                </button>
              )}

              {onEdit && (
                <button
                  onClick={() => { setEditing(!editing); setEditPrompt("") }}
                  className={`p-2 rounded-lg transition-colors flex items-center gap-1.5 ${editing ? "bg-blue-500/30 text-blue-300" : "text-white/70 hover:text-white hover:bg-white/10"}`}
                  title="局部修改"
                >
                  <PenTool size={18} />
                  <span className="text-xs hidden sm:inline">编辑</span>
                </button>
              )}

              <button onClick={toggleFullscreen} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors" title="全屏">
                <Maximize2 size={16} />
              </button>

              <div className="w-px h-5 bg-white/20 mx-1" />
              <button onClick={onClose} className="p-2 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors" title="关闭 (Esc)">
                <X size={20} />
              </button>
            </div>
          </div>

          {/* ── 图片区域 ── */}
          <div
            ref={containerRef}
            className="flex-1 flex items-center justify-center overflow-hidden"
            onWheel={handleWheel}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            style={{ cursor: scale > 1 ? (dragging ? "grabbing" : "grab") : "default" }}
          >
            <img
              src={src}
              alt={prompt || ""}
              className="select-none"
              draggable={false}
              style={{
                transform: `translate(${position.x}px, ${position.y}px) scale(${scale})`,
                transition: dragging ? "none" : "transform 0.15s ease-out",
                maxWidth: scale <= 1 ? "90vw" : "none",
                maxHeight: scale <= 1 ? "85vh" : "none",
                objectFit: "contain",
              }}
            />
          </div>

          {/* ── 底部编辑面板 ── */}
          {editing && (
            <div className="absolute bottom-0 left-0 right-0 z-10 bg-black/70 backdrop-blur-md border-t border-white/10 px-4 py-3">
              <div className="max-w-2xl mx-auto flex items-center gap-3">
                <span className="text-white/70 text-xs whitespace-nowrap">修改: </span>
                <input
                  autoFocus
                  value={editPrompt}
                  onChange={e => setEditPrompt(e.target.value)}
                  onKeyDown={e => { if (e.key === "Enter") handleEditSubmit() }}
                  placeholder="描述你要修改的内容，例如：把背景换成白色"
                  className="flex-1 bg-white/10 text-white text-sm rounded-lg px-3 py-2 outline-none border border-white/20 focus:border-blue-400/50 placeholder:text-white/30"
                />
                <button
                  onClick={handleEditSubmit}
                  disabled={!editPrompt.trim()}
                  className="px-4 py-2 bg-blue-500 hover:bg-blue-600 disabled:opacity-40 text-white text-sm rounded-lg font-medium transition-colors"
                >
                  确认
                </button>
                <button onClick={() => setEditing(false)} className="p-2 text-white/50 hover:text-white transition-colors">
                  <X size={18} />
                </button>
              </div>
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
