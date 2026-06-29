import { useCallback, useEffect, useRef, useState } from "react"
import * as THREE from "three"
import { Button } from "@/components/ui/button"
import {
  Activity,
  AlertCircle,
  BarChart3,
  Globe,
  Image as ImageIcon,
  Loader2,
  MessageSquare,
  PenTool,
  RefreshCw,
  Star,
} from "lucide-react"
import { useNavigate } from "react-router-dom"
import { cn } from "@/lib/utils"
import { api } from "@/lib/api"

interface AgentDef {
  id: string
  name: string
  shortName: string
  description: string
  icon: React.ReactNode
  category: string
  route: string
  typeKey: string
  bubbleClass: string
}

const agentDefs: AgentDef[] = [
  {
    id: "customer-service",
    name: "智能客服 Agent",
    shortName: "智能客服",
    description: "产品知识库问答、多轮询盘沟通与客户上下文理解。",
    icon: <MessageSquare size={20} />,
    category: "对话",
    route: "/agent-chat",
    typeKey: "chat",
    bubbleClass: "agent-bubble--chat",
  },
  {
    id: "inquiry-scoring",
    name: "询盘评分 Agent",
    shortName: "询盘评分",
    description: "识别高价值询盘，给销售团队更快的响应优先级。",
    icon: <Star size={20} />,
    category: "分析",
    route: "/inquiry",
    typeKey: "inquiry",
    bubbleClass: "agent-bubble--score",
  },
  {
    id: "copywriting",
    name: "文案生成 Agent",
    shortName: "文案生成",
    description: "生成营销文案、产品描述和询盘回复邮件。",
    icon: <PenTool size={20} />,
    category: "创作",
    route: "/copywriting",
    typeKey: "copywriting",
    bubbleClass: "agent-bubble--copy",
  },
  {
    id: "translate",
    name: "多语言翻译 Agent",
    shortName: "多语翻译",
    description: "处理中英日韩德法等语种，保持产品术语一致。",
    icon: <Globe size={20} />,
    category: "翻译",
    route: "/translate",
    typeKey: "translate",
    bubbleClass: "agent-bubble--translate",
  },
  {
    id: "image-recognition",
    name: "AI 视觉识图 Agent",
    shortName: "视觉识图",
    description: "识别产品图片、竞品包装和生产工艺图像。",
    icon: <ImageIcon size={20} />,
    category: "视觉",
    route: "/image-recognition",
    typeKey: "image-recognition",
    bubbleClass: "agent-bubble--vision",
  },
  {
    id: "market-analysis",
    name: "市场分析 Agent",
    shortName: "市场分析",
    description: "追踪行业趋势、竞品动态和价格波动。",
    icon: <BarChart3 size={20} />,
    category: "分析",
    route: "/analysis",
    typeKey: "analysis",
    bubbleClass: "agent-bubble--market",
  },
]

const emptyStats: AgentStats = {
  agents: {},
  totalSessions: 0,
  totalRecords: 0,
}

interface AgentStats {
  agents: Record<string, { sessionCount: number }>
  totalSessions: number
  totalRecords: number
}

export default function AgentSquare() {
  const [agentStats, setAgentStats] = useState<AgentStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [activeAgentMood, setActiveAgentMood] = useState("idle")
  const navigate = useNavigate()
  const abortRef = useRef<AbortController | null>(null)

  const fetchStats = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError("")
    try {
      const { data } = await api.get("/agent/agent-stats", { signal })
      if (signal?.aborted) return
      setAgentStats(data as AgentStats)
    } catch (e: any) {
      if (signal?.aborted) return
      if (e.response?.status === 403) {
        setAgentStats(emptyStats)
        setError("")
        return
      }
      setError(e.response?.data?.message || e.message || "获取数据失败")
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    fetchStats(controller.signal)
    return () => controller.abort()
  }, [fetchStats])

  const agents = agentDefs.map((def) => {
    const sessionCount = agentStats?.agents?.[def.typeKey]?.sessionCount ?? 0
    const totalSessions = agentStats?.totalSessions ?? 0
    const share = totalSessions > 0 ? Math.round((sessionCount / totalSessions) * 1000) / 10 : 0
    return { ...def, tasks: sessionCount, share }
  })

  if (loading && !agentStats) {
    return (
      <div className="agent-square-page agent-square-page--center animate-fade-in">
        <div className="agent-square-loading">
          <div className="agent-square-loading-mark">
            <Loader2 className="animate-spin text-[var(--ui-accent-strong)]" size={24} />
          </div>
          <p>正在同步智能体数据</p>
          <span>会话、任务和路由统计正在加载</span>
        </div>
      </div>
    )
  }

  return (
    <div className="agent-square-page animate-fade-in">
      <section className="agent-holo-shell">
        <div className="agent-holo-topbar">
          <div>
            <div className="page-kicker">GLOBAL TRADE AGENTS</div>
            <h1>智能体广场</h1>
            <p>点击环绕在机器人旁边的 Agent 气泡，直接进入对应外贸工作流。</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => fetchStats()} disabled={loading} className="agent-refresh-button">
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
            同步
          </Button>
        </div>

        {error && agentStats && (
          <div className="agent-holo-error">
            <AlertCircle size={13} />
            {error}，当前显示缓存数据
          </div>
        )}

        <div className="agent-holo-stage" aria-label="智能体全息广场">
          <div className="agent-stage-lift">
            <AgentRobotScene mood={activeAgentMood} />

            <div className="agent-bubble-layer">
              {agents.map((agent, index) => (
                <button
                  type="button"
                  key={agent.id}
                  className={cn("agent-orb", agent.bubbleClass)}
                  style={{ animationDelay: `${index * 120}ms` }}
                  onPointerEnter={() => setActiveAgentMood(agent.typeKey)}
                  onPointerLeave={() => setActiveAgentMood("idle")}
                  onFocus={() => setActiveAgentMood(agent.typeKey)}
                  onBlur={() => setActiveAgentMood("idle")}
                  onClick={() => navigate(agent.route)}
                >
                  <span className="agent-orb-icon">{agent.icon}</span>
                  <span className="agent-orb-text">
                    <strong>{agent.shortName}</strong>
                    <small>{agent.description}</small>
                  </span>
                  <span className="agent-orb-metric">
                    <span>{agent.category}</span>
                    <b>{agent.tasks.toLocaleString()} 任务</b>
                  </span>
                </button>
              ))}
            </div>
          </div>

          <div className="agent-holo-status">
            <div>
              <Activity size={15} />
              <span>实时路由</span>
            </div>
            <strong>{agentStats?.totalSessions ?? 0}</strong>
            <small>累计会话</small>
          </div>
        </div>
      </section>
    </div>
  )
}

function AgentRobotScene({ mood }: { mood: string }) {
  const hostRef = useRef<HTMLDivElement>(null)
  const moodRef = useRef(mood)

  useEffect(() => {
    moodRef.current = mood
  }, [mood])

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.setClearColor(0xffffff, 0)
    host.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100)
    camera.position.set(0, 0.82, 8.55)

    const robot = new THREE.Group()
    robot.position.set(0, -0.08, 0)
    robot.scale.setScalar(0.88)
    scene.add(robot)

    const materials: THREE.Material[] = []
    const geometries: THREE.BufferGeometry[] = []
    const trackMaterial = <T extends THREE.Material>(material: T) => {
      materials.push(material)
      return material
    }
    const trackGeometry = <T extends THREE.BufferGeometry>(geometry: T) => {
      geometries.push(geometry)
      return geometry
    }

    const shellMaterial = trackMaterial(new THREE.MeshPhysicalMaterial({
      color: 0xf5fff8,
      transparent: true,
      opacity: 0.78,
      roughness: 0.18,
      metalness: 0.18,
      transmission: 0.2,
      clearcoat: 1,
      clearcoatRoughness: 0.12,
      emissive: 0x5edc85,
      emissiveIntensity: 0.12,
    }))
    const glassMaterial = trackMaterial(new THREE.MeshPhysicalMaterial({
      color: 0xd8ffe7,
      transparent: true,
      opacity: 0.36,
      roughness: 0.08,
      metalness: 0.14,
      transmission: 0.24,
      clearcoat: 1,
      clearcoatRoughness: 0.06,
      emissive: 0x69e894,
      emissiveIntensity: 0.18,
    }))
    const faceMaterial = trackMaterial(new THREE.MeshBasicMaterial({
      color: 0x052116,
      transparent: false,
      depthWrite: true,
    }))
    const accentMaterial = trackMaterial(new THREE.MeshBasicMaterial({
      color: 0x55e68a,
      transparent: true,
      opacity: 0.9,
    }))
    const lineMaterial = trackMaterial(new THREE.LineBasicMaterial({
      color: 0x7df3a6,
      transparent: true,
      opacity: 0.52,
    }))
    const softAccentMaterial = trackMaterial(new THREE.MeshBasicMaterial({
      color: 0x7df3a6,
      transparent: true,
      opacity: 0.24,
      depthWrite: false,
    }))
    const wireMaterial = trackMaterial(new THREE.MeshBasicMaterial({
      color: 0xbaffcf,
      transparent: true,
      opacity: 0.18,
      wireframe: true,
      depthWrite: false,
    }))

    const createRoundedBox = (width: number, height: number, depth: number, radius: number) => {
      const x = -width / 2
      const y = -height / 2
      const shape = new THREE.Shape()
      shape.moveTo(x + radius, y)
      shape.lineTo(x + width - radius, y)
      shape.quadraticCurveTo(x + width, y, x + width, y + radius)
      shape.lineTo(x + width, y + height - radius)
      shape.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
      shape.lineTo(x + radius, y + height)
      shape.quadraticCurveTo(x, y + height, x, y + height - radius)
      shape.lineTo(x, y + radius)
      shape.quadraticCurveTo(x, y, x + radius, y)
      const geometry = new THREE.ExtrudeGeometry(shape, { depth, bevelEnabled: false, curveSegments: 18 })
      geometry.translate(0, 0, -depth / 2)
      return trackGeometry(geometry)
    }

    const createRoundedPlane = (width: number, height: number, radius: number) => {
      const shape = new THREE.Shape()
      const x = -width / 2
      const y = -height / 2
      shape.moveTo(x + radius, y)
      shape.lineTo(x + width - radius, y)
      shape.quadraticCurveTo(x + width, y, x + width, y + radius)
      shape.lineTo(x + width, y + height - radius)
      shape.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
      shape.lineTo(x + radius, y + height)
      shape.quadraticCurveTo(x, y + height, x, y + height - radius)
      shape.lineTo(x, y + radius)
      shape.quadraticCurveTo(x, y, x + radius, y)
      return trackGeometry(new THREE.ShapeGeometry(shape, 18))
    }

    const signedPower = (value: number, power: number) => Math.sign(value) * Math.pow(Math.abs(value), power)
    const createSuperEllipsoid = (
      radiusX: number,
      radiusY: number,
      radiusZ: number,
      power = 0.55,
      widthSegments = 72,
      heightSegments = 36
    ) => {
      const vertices: number[] = []
      const indices: number[] = []
      for (let y = 0; y <= heightSegments; y += 1) {
        const latitude = -Math.PI / 2 + (y / heightSegments) * Math.PI
        const cosLatitude = Math.cos(latitude)
        const sinLatitude = Math.sin(latitude)
        for (let x = 0; x <= widthSegments; x += 1) {
          const longitude = -Math.PI + (x / widthSegments) * Math.PI * 2
          vertices.push(
            radiusX * signedPower(cosLatitude, power) * signedPower(Math.cos(longitude), power),
            radiusY * signedPower(sinLatitude, power),
            radiusZ * signedPower(cosLatitude, power) * signedPower(Math.sin(longitude), power)
          )
        }
      }
      const row = widthSegments + 1
      for (let y = 0; y < heightSegments; y += 1) {
        for (let x = 0; x < widthSegments; x += 1) {
          const a = y * row + x
          const b = a + row
          indices.push(a, b, a + 1, b, b + 1, a + 1)
        }
      }
      const geometry = new THREE.BufferGeometry()
      geometry.setAttribute("position", new THREE.Float32BufferAttribute(vertices, 3))
      geometry.setIndex(indices)
      geometry.computeVertexNormals()
      return trackGeometry(geometry)
    }

    const base = new THREE.Group()
    base.position.y = -1.54
    robot.add(base)

    const baseDisc = new THREE.Mesh(trackGeometry(new THREE.CylinderGeometry(2.28, 2.62, 0.2, 128)), glassMaterial)
    baseDisc.position.y = -0.08
    base.add(baseDisc)

    const baseGlow = new THREE.Mesh(trackGeometry(new THREE.CylinderGeometry(2.0, 2.18, 0.05, 128)), softAccentMaterial)
    baseGlow.position.y = 0.08
    base.add(baseGlow)

    const baseLine = new THREE.LineLoop(trackGeometry(new THREE.BufferGeometry().setFromPoints(
      Array.from({ length: 160 }, (_, i) => {
        const angle = (i / 160) * Math.PI * 2
        return new THREE.Vector3(Math.cos(angle) * 1.82, 0.03, Math.sin(angle) * 1.82)
      })
    )), lineMaterial)
    base.add(baseLine)

    ;[1.14, 1.64, 2.22].forEach((radius, index) => {
      const ring = new THREE.Mesh(trackGeometry(new THREE.TorusGeometry(radius, index === 1 ? 0.015 : 0.01, 10, 180)), index === 2 ? softAccentMaterial : accentMaterial)
      ring.rotation.x = Math.PI / 2
      ring.position.y = index === 0 ? 0.17 : index === 1 ? 0.07 : -0.02
      base.add(ring)
    })

    const bodyGeometry = createSuperEllipsoid(0.76, 0.86, 0.56, 0.62)
    const body = new THREE.Mesh(bodyGeometry, shellMaterial)
    body.position.y = -0.42
    robot.add(body)
    const bodyWire = new THREE.Mesh(bodyGeometry, wireMaterial)
    bodyWire.position.copy(body.position)
    robot.add(bodyWire)

    const chest = new THREE.Mesh(createRoundedBox(0.58, 0.32, 0.06, 0.08), faceMaterial)
    chest.position.set(0, -0.34, 0.57)
    robot.add(chest)
    const chestDotGrid = new THREE.Group()
    chestDotGrid.position.set(0, -0.34, 0.606)
    for (let row = 0; row < 3; row += 1) {
      for (let col = 0; col < 5; col += 1) {
        const dot = new THREE.Mesh(trackGeometry(new THREE.CircleGeometry(0.014, 10)), accentMaterial)
        dot.position.set((col - 2) * 0.07, (row - 1) * 0.065, 0)
        chestDotGrid.add(dot)
      }
    }
    robot.add(chestDotGrid)

    const neck = new THREE.Mesh(trackGeometry(new THREE.CylinderGeometry(0.22, 0.28, 0.28, 48)), glassMaterial)
    neck.position.y = 0.38
    robot.add(neck)

    const headGroup = new THREE.Group()
    headGroup.position.y = 0.55
    robot.add(headGroup)

    const headGeometry = createSuperEllipsoid(1.02, 0.7, 0.62, 0.48, 86, 42)
    const head = new THREE.Mesh(headGeometry, shellMaterial)
    head.position.y = 0.5
    headGroup.add(head)

    const headWire = new THREE.Mesh(headGeometry, wireMaterial)
    headWire.position.copy(head.position)
    headGroup.add(headWire)

    const face = new THREE.Mesh(createRoundedPlane(1.36, 0.56, 0.18), faceMaterial)
    face.position.set(0, 0.48, 0.64)
    headGroup.add(face)

    const eyeGeometry = trackGeometry(new THREE.CircleGeometry(0.1, 28))
    const leftEye = new THREE.Mesh(eyeGeometry, accentMaterial)
    leftEye.position.set(-0.32, 0.52, 0.654)
    leftEye.scale.set(1.22, 1.68, 1)
    const rightEye = new THREE.Mesh(eyeGeometry, accentMaterial)
    rightEye.position.set(0.32, 0.52, 0.654)
    rightEye.scale.set(1.22, 1.68, 1)
    headGroup.add(leftEye, rightEye)

    const smileCurve = new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(-0.22, 0.31, 0.662),
      new THREE.Vector3(0, 0.22, 0.662),
      new THREE.Vector3(0.22, 0.31, 0.662)
    )
    const smile = new THREE.Mesh(trackGeometry(new THREE.TubeGeometry(smileCurve, 24, 0.008, 8, false)), accentMaterial)
    headGroup.add(smile)

    const attentiveMouthCurve = new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(-0.24, 0.3, 0.664),
      new THREE.Vector3(0, 0.18, 0.664),
      new THREE.Vector3(0.24, 0.3, 0.664)
    )
    const attentiveMouth = new THREE.Mesh(trackGeometry(new THREE.TubeGeometry(attentiveMouthCurve, 24, 0.011, 8, false)), accentMaterial)
    attentiveMouth.visible = false
    headGroup.add(attentiveMouth)

    const thinkingMouthGeometry = trackGeometry(new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-0.2, 0.27, 0.665),
      new THREE.Vector3(0.2, 0.27, 0.665),
    ]))
    const thinkingMouth = new THREE.Line(thinkingMouthGeometry, lineMaterial)
    thinkingMouth.visible = false
    headGroup.add(thinkingMouth)

    ;[-1, 1].forEach((side) => {
      const ear = new THREE.Mesh(trackGeometry(new THREE.CylinderGeometry(0.24, 0.24, 0.18, 48)), glassMaterial)
      ear.rotation.z = Math.PI / 2
      ear.position.set(side * 0.98, 0.5, 0.02)
      headGroup.add(ear)
      const earCore = new THREE.Mesh(trackGeometry(new THREE.TorusGeometry(0.15, 0.014, 8, 48)), accentMaterial)
      earCore.rotation.y = Math.PI / 2
      earCore.position.set(side * 1.08, 0.5, 0.02)
      headGroup.add(earCore)
    })

    const antennaStem = new THREE.Mesh(trackGeometry(new THREE.CylinderGeometry(0.01, 0.014, 0.38, 12)), accentMaterial)
    antennaStem.position.set(0, 1.22, 0.02)
    headGroup.add(antennaStem)
    const antennaDot = new THREE.Mesh(trackGeometry(new THREE.SphereGeometry(0.082, 24, 24)), accentMaterial)
    antennaDot.position.set(0, 1.45, 0.02)
    headGroup.add(antennaDot)

    const armGeometry = trackGeometry(new THREE.CapsuleGeometry(0.075, 0.34, 12, 26))
    const forearmGeometry = trackGeometry(new THREE.CapsuleGeometry(0.068, 0.3, 12, 24))
    const handGeometry = trackGeometry(new THREE.SphereGeometry(0.105, 24, 24))
    const armControls: Array<{ group: THREE.Group; side: number; hand: THREE.Mesh }> = []
    ;[-1, 1].forEach((side) => {
      const group = new THREE.Group()
      group.position.set(side * 0.78, -0.08, 0.08)
      robot.add(group)

      const shoulder = new THREE.Mesh(trackGeometry(new THREE.SphereGeometry(0.15, 28, 28)), glassMaterial)
      group.add(shoulder)

      const upperArm = new THREE.Mesh(armGeometry, shellMaterial)
      upperArm.position.set(side * 0.14, -0.25, 0.02)
      upperArm.rotation.z = side * 0.36
      group.add(upperArm)

      const elbow = new THREE.Mesh(trackGeometry(new THREE.SphereGeometry(0.085, 20, 20)), glassMaterial)
      elbow.position.set(side * 0.28, -0.48, 0.05)
      group.add(elbow)

      const forearm = new THREE.Mesh(forearmGeometry, shellMaterial)
      forearm.position.set(side * 0.34, -0.67, 0.07)
      forearm.rotation.z = side * 0.12
      group.add(forearm)

      const hand = new THREE.Mesh(handGeometry, accentMaterial)
      hand.position.set(side * 0.4, -0.86, 0.1)
      hand.scale.set(1.08, 0.82, 1)
      group.add(hand)

      armControls.push({ group, side, hand })
    })

    const particleGeometry = trackGeometry(new THREE.BufferGeometry())
    const particlePositions: number[] = []
    for (let i = 0; i < 170; i += 1) {
      const angle = Math.random() * Math.PI * 2
      const radius = 1.15 + Math.random() * 2.45
      particlePositions.push(
        Math.cos(angle) * radius,
        -0.2 + Math.random() * 3.2,
        Math.sin(angle) * radius - 0.5
      )
    }
    particleGeometry.setAttribute("position", new THREE.Float32BufferAttribute(particlePositions, 3))
    const particleMaterial = trackMaterial(new THREE.PointsMaterial({
      color: 0x2f8f76,
      size: 0.026,
      transparent: true,
      opacity: 0.44,
      depthWrite: false,
    }))
    const particles = new THREE.Points(particleGeometry, particleMaterial)
    scene.add(particles)

    const keyLight = new THREE.PointLight(0xeaffef, 5.4, 18)
    keyLight.position.set(2.8, 3.4, 4.1)
    const rimLight = new THREE.PointLight(0x7df3a6, 5.8, 16)
    rimLight.position.set(-2.8, 1.8, 2.8)
    const fillLight = new THREE.AmbientLight(0xffffff, 0.88)
    scene.add(keyLight, rimLight, fillLight)

    let targetPointerX = 0
    let targetPointerY = 0
    let pointerX = 0
    let pointerY = 0
    const onPointerMove = (event: PointerEvent) => {
      const rect = host.getBoundingClientRect()
      const normalizedX = THREE.MathUtils.clamp((event.clientX - rect.left) / rect.width, 0, 1)
      const normalizedY = THREE.MathUtils.clamp((event.clientY - rect.top) / rect.height, 0, 1)
      targetPointerX = (normalizedX - 0.5) * 0.26
      targetPointerY = (0.5 - normalizedY) * 0.1
    }
    window.addEventListener("pointermove", onPointerMove)

    const resize = () => {
      const width = Math.max(host.clientWidth, 1)
      const height = Math.max(host.clientHeight, 1)
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    }
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host)
    resize()

    let frameId = 0
    const render = () => {
      const elapsed = performance.now() * 0.001
      const activeMood = moodRef.current
      const isActive = activeMood !== "idle"
      const isThinkingMood = activeMood === "inquiry" || activeMood === "analysis"
      const isCreativeMood = activeMood === "copywriting" || activeMood === "image-recognition"
      pointerX += (targetPointerX - pointerX) * 0.08
      pointerY += (targetPointerY - pointerY) * 0.08
      const headTilt = THREE.MathUtils.clamp(pointerY + Math.sin(elapsed * 1.12) * 0.012, -0.055, 0.055)
      robot.rotation.y = Math.sin(elapsed * 0.45) * 0.035 + pointerX * 0.35 + (isActive ? 0.035 : 0)
      headGroup.rotation.x = headTilt
      headGroup.rotation.y = Math.sin(elapsed * 0.8) * 0.035 + pointerX * 0.55
      body.position.y = -0.42 + Math.sin(elapsed * 1.05) * 0.035
      bodyWire.position.y = body.position.y
      headGroup.position.y = 0.55 + Math.sin(elapsed * 1.05 + 0.24) * 0.04
      const eyeLift = pointerY * 0.5 + (isActive ? 0.018 : 0)
      leftEye.position.y = 0.52 + eyeLift
      rightEye.position.y = 0.52 + eyeLift
      leftEye.scale.set(isThinkingMood ? 0.82 : isActive ? 1.42 : 1.22, isCreativeMood ? 1.92 : isActive ? 1.5 : 1.68, 1)
      rightEye.scale.set(isThinkingMood ? 0.82 : isActive ? 1.42 : 1.22, isCreativeMood ? 1.92 : isActive ? 1.5 : 1.68, 1)
      smile.visible = !isActive
      attentiveMouth.visible = isActive && !isThinkingMood
      thinkingMouth.visible = isActive && isThinkingMood
      attentiveMouth.scale.setScalar(isCreativeMood ? 1.18 : 1)
      armControls.forEach(({ group, side, hand }) => {
        const wave = isActive && side > 0 ? Math.sin(elapsed * 5.6) * 0.12 : 0
        const lift = isActive && side > 0 ? -0.72 : side * 0.04
        group.rotation.z = THREE.MathUtils.lerp(group.rotation.z, lift + wave, 0.08)
        group.rotation.x = Math.sin(elapsed * 1.2 + side) * 0.035
        hand.scale.setScalar(1 + (isActive && side > 0 ? Math.sin(elapsed * 6.2) * 0.08 : 0))
      })
      base.rotation.y = elapsed * 0.075
      particles.rotation.y = elapsed * 0.075
      particles.position.y = Math.sin(elapsed * 0.72) * 0.08
      renderer.render(scene, camera)
      frameId = window.requestAnimationFrame(render)
    }
    render()

    return () => {
      window.cancelAnimationFrame(frameId)
      window.removeEventListener("pointermove", onPointerMove)
      resizeObserver.disconnect()
      renderer.domElement.remove()
      geometries.forEach((geometry) => geometry.dispose())
      materials.forEach((material) => material.dispose())
      renderer.dispose()
    }
  }, [])

  return (
    <div className="agent-robot-wrap">
      <div ref={hostRef} className="agent-robot-canvas" />
    </div>
  )
}
