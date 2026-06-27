import { useEffect, useRef } from "react"
import { Link } from "react-router-dom"
import * as THREE from "three"
import {
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  FileSearch,
  MailCheck,
  MessageSquareText,
  PackageCheck,
  Radar,
  Route,
  ShieldCheck,
  Truck,
} from "lucide-react"
import { Button } from "@/components/ui/button"

const followStages = [
  { label: "线索识别", value: "Inquiry", hint: "国家、产品、数量、预算" },
  { label: "需求确认", value: "Need", hint: "应用场景、规格、认证、交期" },
  { label: "报价推进", value: "Quote", hint: "MOQ、包装、贸易条款、样品费" },
  { label: "样品闭环", value: "Sample", hint: "设计稿、打样、寄样、反馈" },
  { label: "订单复购", value: "Order", hint: "付款、生产、物流、售后" },
]

const businessModules = [
  {
    title: "询盘跟进",
    text: "识别客户采购意图，整理缺失信息，生成下一封英文跟进邮件。",
    icon: FileSearch,
    status: "前端预览",
  },
  {
    title: "报价准备",
    text: "把产品、MOQ、包装、交期、贸易条款组织成可发送的报价说明。",
    icon: BadgeCheck,
    status: "待接入",
  },
  {
    title: "样品进度",
    text: "围绕设计确认、打样、拍照、寄样和反馈形成清晰时间线。",
    icon: PackageCheck,
    status: "待接入",
  },
  {
    title: "合规物流",
    text: "预留 FSC、环保油墨、包装测试、HS Code、目的港风险检查入口。",
    icon: Truck,
    status: "待接入",
  },
]

export default function TradeWorkspace() {
  return (
    <div className="trade-follow-page animate-fade-in">
      <section className="trade-follow-hero trade-follow-hero--webgl">
        <div className="trade-follow-copy trade-follow-copy--overlay">
          <div className="page-kicker">GLOBAL TRADE FOLLOW-UP</div>
          <h1>外贸业务跟进</h1>
          <p>
            以客户为中心，把询盘、需求确认、报价、样品、订单和复购串成一条可追踪的业务链路，后续每个节点都可以接入 Agent 自动化。
          </p>
          <div className="trade-follow-actions">
            <Link to="/agent-chat">
              <Button className="rounded-[8px] active:translate-y-[1px]">
                <MessageSquareText size={16} />
                进入业务对话
              </Button>
            </Link>
            <Link to="/knowledge-base">
              <Button variant="outline" className="rounded-[8px] active:translate-y-[1px]">
                维护产品知识库
                <ArrowRight size={15} />
              </Button>
            </Link>
          </div>
        </div>

        <TradeGlobeScene />
      </section>

      <section className="trade-follow-strip" aria-label="外贸跟进阶段">
        {followStages.map((stage, index) => (
          <div key={stage.value} className="trade-follow-step" style={{ animationDelay: `${index * 80}ms` }}>
            <span>{stage.label}</span>
            <strong>{stage.value}</strong>
            <small>{stage.hint}</small>
          </div>
        ))}
      </section>

      <section className="trade-follow-grid">
        <div className="trade-follow-main">
          <div className="trade-follow-section-head">
            <div>
              <div className="page-kicker">FOLLOW-UP MODULES</div>
              <h2>先搭好业务骨架</h2>
            </div>
            <p>功能还没完全落地时，先用清晰、留白充足的入口承载业务方向，后续逐步接入真实数据和自动化动作。</p>
          </div>

          <div className="trade-module-grid">
            {businessModules.map((item, index) => (
              <article key={item.title} className="trade-module-tile animate-fade-in-up" style={{ animationDelay: `${index * 70}ms` }}>
                <div className="trade-module-icon">
                  <item.icon size={19} />
                </div>
                <div>
                  <div className="trade-module-title-row">
                    <h3>{item.title}</h3>
                    <span>{item.status}</span>
                  </div>
                  <p>{item.text}</p>
                </div>
              </article>
            ))}
          </div>
        </div>

        <aside className="trade-follow-side">
          <div className="trade-follow-side-top">
            <Radar size={18} />
            <span>业务雷达</span>
          </div>
          <h2>下一步可展开的能力</h2>
          <div className="trade-capability-list">
            <Capability icon={<Route size={16} />} title="客户阶段流转" text="每个客户有明确阶段、负责人和下一步动作。" />
            <Capability icon={<ShieldCheck size={16} />} title="风险提示" text="付款、交期、认证、物流异常提前暴露。" />
            <Capability icon={<MailCheck size={16} />} title="邮件与微信分工" text="正式报价走邮件，快速提醒走 JC claw。" />
            <Capability icon={<CheckCircle2 size={16} />} title="结果可追踪" text="每次跟进沉淀为任务、话术和客户记录。" />
          </div>
        </aside>
      </section>
    </div>
  )
}
function TradeGlobeScene() {
  const hostRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const host = hostRef.current
    if (!host) return

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.setClearColor(0x000000, 0)
    host.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100)
    camera.position.set(0.72, 0.16, 7.35)

    const group = new THREE.Group()
    group.position.set(1.22, 0.02, 0)
    group.rotation.x = -0.22
    group.rotation.z = -0.14
    scene.add(group)

    const rimLight = new THREE.PointLight(0x7df3a6, 5.1, 26)
    rimLight.position.set(3.2, 3.4, 4.4)
    const coldLight = new THREE.PointLight(0xb7ffe2, 2.35, 18)
    coldLight.position.set(-3.6, -1.8, 3.8)
    scene.add(rimLight)
    scene.add(coldLight)
    scene.add(new THREE.AmbientLight(0xffffff, 0.78))

    const globeRadius = 2.9
    const latLngToVector = (lat: number, lng: number, radius = globeRadius) => {
      const latitude = THREE.MathUtils.degToRad(lat)
      const longitude = THREE.MathUtils.degToRad(lng)
      return new THREE.Vector3(
        Math.cos(latitude) * Math.sin(longitude) * radius,
        Math.sin(latitude) * radius,
        Math.cos(latitude) * Math.cos(longitude) * radius
      )
    }

    const sphereGeometry = new THREE.SphereGeometry(globeRadius, 128, 128)
    const sphereMaterial = new THREE.MeshPhysicalMaterial({
      color: 0x061811,
      transparent: true,
      opacity: 0.58,
      roughness: 0.24,
      metalness: 0.34,
      transmission: 0.1,
      clearcoat: 0.92,
      clearcoatRoughness: 0.18,
      emissive: 0x052916,
      emissiveIntensity: 0.34,
    })
    group.add(new THREE.Mesh(sphereGeometry, sphereMaterial))

    const rimGeometry = new THREE.SphereGeometry(globeRadius * 1.018, 96, 96)
    const rimMaterial = new THREE.MeshBasicMaterial({
      color: 0x78ef9e,
      transparent: true,
      opacity: 0.14,
      wireframe: true,
      depthWrite: false,
    })
    const rimGrid = new THREE.Mesh(rimGeometry, rimMaterial)
    group.add(rimGrid)

    const atmosphereGeometry = new THREE.SphereGeometry(globeRadius * 1.08, 96, 96)
    const atmosphereMaterial = new THREE.MeshBasicMaterial({
      color: 0x69e894,
      transparent: true,
      opacity: 0.14,
      side: THREE.BackSide,
      depthWrite: false,
    })
    group.add(new THREE.Mesh(atmosphereGeometry, atmosphereMaterial))

    const dotPositions: number[] = []
    const dotColors: number[] = []
    const seaColor = new THREE.Color(0xeaffef)
    for (let lat = -72; lat <= 72; lat += 3.1) {
      const latitudeCos = Math.max(Math.cos(THREE.MathUtils.degToRad(lat)), 0.32)
      const longitudeStep = Math.max(3.2, 4.8 / latitudeCos)
      for (let lng = -180; lng < 180; lng += longitudeStep) {
        const seeded = Math.abs(Math.sin(lat * 12.9898 + lng * 78.233) * 43758.5453) % 1
        if (seeded < 0.46) continue
        const point = latLngToVector(lat, lng + Math.sin(lat * 0.33) * 0.3, globeRadius * 1.012)
        dotPositions.push(point.x, point.y, point.z)
        const intensity = 0.2 + seeded * 0.28
        dotColors.push(seaColor.r * intensity, seaColor.g * intensity, seaColor.b * intensity)
      }
    }

    const dotGeometry = new THREE.BufferGeometry()
    dotGeometry.setAttribute("position", new THREE.Float32BufferAttribute(dotPositions, 3))
    dotGeometry.setAttribute("color", new THREE.Float32BufferAttribute(dotColors, 3))
    const dotMaterial = new THREE.PointsMaterial({
      size: 0.018,
      transparent: true,
      opacity: 0.86,
      vertexColors: true,
      depthWrite: false,
    })
    const dotField = new THREE.Points(dotGeometry, dotMaterial)
    group.add(dotField)

    const outlineMaterial = new THREE.LineBasicMaterial({ color: 0x7df3a6, transparent: true, opacity: 0.84, depthWrite: false })
    const outlineGlowMaterial = new THREE.LineBasicMaterial({ color: 0xd7ffe2, transparent: true, opacity: 0.28, depthWrite: false })
    const mapGeometries: THREE.BufferGeometry[] = []
    const outlineGroup = new THREE.Group()
    group.add(outlineGroup)

    const coastDotGeometry = new THREE.BufferGeometry()
    const coastDotMaterial = new THREE.PointsMaterial({
      color: 0x73f3a4,
      size: 0.021,
      transparent: true,
      opacity: 0.78,
      depthWrite: false,
    })
    const coastDots = new THREE.Points(coastDotGeometry, coastDotMaterial)
    group.add(coastDots)

    let disposed = false
    fetch("/data/ne_50m_coastline.geojson")
      .then((response) => response.json())
      .then((geojson: { features?: Array<{ geometry?: { coordinates?: number[][] | number[][][]; type?: string } }> }) => {
        if (disposed) return
        const coastPositions: number[] = []
        geojson.features?.forEach((feature) => {
          const geometry = feature.geometry
          const lines =
            geometry?.type === "LineString"
              ? [geometry.coordinates as number[][]]
              : geometry?.type === "MultiLineString"
                ? (geometry.coordinates as number[][][])
                : []

          lines.forEach((line) => {
            if (line.length < 2) return
            const simplified = line.filter((_, index) => index % 2 === 0)
            const points = simplified.map(([lng, lat]) => {
              const point = latLngToVector(lat, lng, globeRadius * 1.03)
              coastPositions.push(point.x, point.y, point.z)
              return point
            })
            if (points.length < 2) return
            const geometryLine = new THREE.BufferGeometry().setFromPoints(points)
            const geometryGlow = new THREE.BufferGeometry().setFromPoints(points.map((point) => point.clone().multiplyScalar(1.002)))
            mapGeometries.push(geometryLine, geometryGlow)
            outlineGroup.add(new THREE.Line(geometryLine, outlineMaterial))
            outlineGroup.add(new THREE.Line(geometryGlow, outlineGlowMaterial))
          })
        })
        coastDotGeometry.setAttribute("position", new THREE.Float32BufferAttribute(coastPositions, 3))
        coastDotGeometry.computeBoundingSphere()
      })
      .catch(() => {
        // The globe still renders with the base dot field if the static map asset is unavailable.
      })

    const routeMaterial = new THREE.LineDashedMaterial({
      color: 0x98f7b6,
      transparent: true,
      opacity: 0.5,
      depthWrite: false,
      dashSize: 0.095,
      gapSize: 0.06,
    })
    const accentRouteMaterial = new THREE.LineDashedMaterial({
      color: 0xe9b95f,
      transparent: true,
      opacity: 0.7,
      depthWrite: false,
      dashSize: 0.125,
      gapSize: 0.052,
    })
    const routeHaloMaterial = new THREE.LineBasicMaterial({ color: 0xb9ffd0, transparent: true, opacity: 0.16, depthWrite: false })
    const accentRouteHaloMaterial = new THREE.LineBasicMaterial({ color: 0xffd487, transparent: true, opacity: 0.18, depthWrite: false })
    const routeGeometries: THREE.BufferGeometry[] = []
    const routeGroup = new THREE.Group()
    const animatedRoutes: Array<{
      curve: THREE.QuadraticBezierCurve3
      plane: THREE.Mesh
      spark: THREE.Mesh
      speed: number
      phase: number
    }> = []
    const routes: Array<{ from: [number, number]; to: [number, number]; accent?: boolean; height?: number }> = [
      { from: [39.9, 116.4], to: [51.5, -0.1], accent: true, height: 1.5 },
      { from: [39.9, 116.4], to: [38.9, -77], accent: true, height: 1.56 },
      { from: [31.2, 121.5], to: [52.5, 13.4], height: 1.44 },
      { from: [31.2, 121.5], to: [48.9, 2.4], height: 1.46 },
      { from: [22.3, 114.2], to: [25.2, 55.3], accent: true, height: 1.36 },
      { from: [22.3, 114.2], to: [-23.6, -46.6], height: 1.62 },
      { from: [1.3, 103.8], to: [-6.2, 106.8], height: 1.25 },
      { from: [1.3, 103.8], to: [52.5, 13.4], height: 1.48 },
      { from: [35.7, 139.7], to: [-33.9, 151.2], height: 1.35 },
      { from: [35.7, 139.7], to: [34.1, -118.2], accent: true, height: 1.6 },
      { from: [25.2, 55.3], to: [51.5, -0.1], accent: true, height: 1.4 },
      { from: [25.2, 55.3], to: [-26.2, 28.0], height: 1.36 },
      { from: [39.9, 116.4], to: [19.4, -99.1], height: 1.58 },
      { from: [40.7, -74.0], to: [52.5, 13.4], height: 1.34 },
      { from: [40.7, -74.0], to: [25.2, 55.3], height: 1.5 },
      { from: [51.5, -0.1], to: [-33.9, 151.2], height: 1.58 },
      { from: [48.9, 2.4], to: [1.3, 103.8], height: 1.47 },
      { from: [19.4, -99.1], to: [-23.6, -46.6], height: 1.34 },
    ]

    const flightPlaneGeometry = new THREE.ConeGeometry(0.028, 0.13, 3)
    const flightPlaneMaterial = new THREE.MeshBasicMaterial({ color: 0xeaffef, transparent: true, opacity: 0.92, depthWrite: false })
    const flightSparkGeometry = new THREE.SphereGeometry(0.022, 10, 10)
    const flightSparkMaterial = new THREE.MeshBasicMaterial({ color: 0xa9ffc6, transparent: true, opacity: 0.74, depthWrite: false })
    const upAxis = new THREE.Vector3(0, 1, 0)
    routes.forEach((route, index) => {
      const start = latLngToVector(route.from[0], route.from[1], globeRadius * 1.04)
      const end = latLngToVector(route.to[0], route.to[1], globeRadius * 1.04)
      const mid = start.clone().add(end).normalize().multiplyScalar(globeRadius * (route.height ?? 1.34))
      const curve = new THREE.QuadraticBezierCurve3(start, mid, end)
      const geometry = new THREE.BufferGeometry().setFromPoints(curve.getPoints(72))
      const haloGeometry = new THREE.BufferGeometry().setFromPoints(curve.getPoints(72).map((point) => point.clone().multiplyScalar(1.004)))
      routeGeometries.push(geometry, haloGeometry)
      const routeLine = new THREE.Line(geometry, route.accent ? accentRouteMaterial : routeMaterial)
      routeLine.computeLineDistances()
      routeGroup.add(routeLine)
      routeGroup.add(new THREE.Line(haloGeometry, route.accent ? accentRouteHaloMaterial : routeHaloMaterial))

      const plane = new THREE.Mesh(flightPlaneGeometry, flightPlaneMaterial)
      const spark = new THREE.Mesh(flightSparkGeometry, route.accent ? new THREE.MeshBasicMaterial({
        color: 0xffd487,
        transparent: true,
        opacity: 0.78,
        depthWrite: false,
      }) : flightSparkMaterial)
      routeGroup.add(plane)
      routeGroup.add(spark)
      animatedRoutes.push({
        curve,
        plane,
        spark,
        speed: 0.045 + (index % 5) * 0.008,
        phase: index * 0.173,
      })
    })
    group.add(routeGroup)

    const createKeywordSprite = (text: string) => {
      const canvas = document.createElement("canvas")
      const scale = 2
      canvas.width = 320 * scale
      canvas.height = 72 * scale
      const context = canvas.getContext("2d")
      if (context) {
        context.scale(scale, scale)
        context.clearRect(0, 0, 320, 72)
        context.font = "700 24px Arial, Microsoft YaHei, sans-serif"
        context.textAlign = "center"
        context.textBaseline = "middle"
        context.shadowColor = "rgba(134, 255, 174, 0.64)"
        context.shadowBlur = 16
        context.lineWidth = 3
        context.strokeStyle = "rgba(7, 30, 19, 0.5)"
        context.strokeText(text, 160, 34)
        context.fillStyle = "rgba(234, 255, 239, 0.95)"
        context.fillText(text, 160, 34)
        context.shadowBlur = 9
        context.fillStyle = "rgba(119, 244, 160, 0.38)"
        context.fillRect(160 - Math.min(118, text.length * 8), 55, Math.min(236, text.length * 16), 2)
      }
      const texture = new THREE.CanvasTexture(canvas)
      texture.colorSpace = THREE.SRGBColorSpace
      const material = new THREE.SpriteMaterial({ map: texture, transparent: true, opacity: 0.88, depthWrite: false, depthTest: false })
      const sprite = new THREE.Sprite(material)
      sprite.scale.set(0.78 + Math.min(text.length, 12) * 0.018, 0.2, 1)
      return { sprite, texture, material }
    }

    const foregroundLabelGroup = new THREE.Group()
    foregroundLabelGroup.position.copy(group.position)
    scene.add(foregroundLabelGroup)

    const keywordSprites: Array<{
      sprite: THREE.Sprite
      baseScale: THREE.Vector3
      basePosition: THREE.Vector3
      phase: number
      jumpEvery: number
    }> = []
    const keywordTextures: THREE.Texture[] = []
    const keywordMaterials: THREE.SpriteMaterial[] = []
    const keywordAnchors: Array<{ text: string; position: [number, number, number] }> = [
      { text: "B2B SOURCING", position: [-0.72, 1.12, globeRadius * 1.1] },
      { text: "GLOBAL RFQ", position: [0.54, 0.72, globeRadius * 1.12] },
      { text: "EXPORT READY", position: [-0.92, 0.16, globeRadius * 1.13] },
      { text: "OEM ODM", position: [0.58, -0.3, globeRadius * 1.11] },
      { text: "TRADE TERMS", position: [-0.26, -0.82, globeRadius * 1.12] },
    ]
    keywordAnchors.forEach((label) => {
      const { sprite, texture, material } = createKeywordSprite(label.text)
      sprite.position.set(label.position[0], label.position[1], label.position[2])
      keywordSprites.push({
        sprite,
        baseScale: sprite.scale.clone(),
        basePosition: sprite.position.clone(),
        phase: keywordSprites.length * 0.83,
        jumpEvery: 3.8 + (keywordSprites.length % 4) * 0.9,
      })
      keywordTextures.push(texture)
      keywordMaterials.push(material)
      foregroundLabelGroup.add(sprite)
    })

    const markerGeometry = new THREE.SphereGeometry(0.035, 12, 12)
    const markerMaterial = new THREE.MeshBasicMaterial({ color: 0xeaffef, transparent: true, opacity: 0.92 })
    const markers = routes.flatMap((route) => [route.from, route.to]).map(([lat, lng]) => {
      const marker = new THREE.Mesh(markerGeometry, markerMaterial)
      marker.position.copy(latLngToVector(lat, lng, globeRadius * 1.055))
      group.add(marker)
      return marker
    })

    const starGeometry = new THREE.BufferGeometry()
    const starPositions = new Float32Array(520 * 3)
    for (let i = 0; i < 520; i += 1) {
      const radius = 3.45 + Math.random() * 0.56
      const theta = Math.random() * Math.PI * 2
      const phi = Math.acos(2 * Math.random() - 1)
      starPositions[i * 3] = radius * Math.sin(phi) * Math.cos(theta)
      starPositions[i * 3 + 1] = radius * Math.cos(phi)
      starPositions[i * 3 + 2] = radius * Math.sin(phi) * Math.sin(theta)
    }
    starGeometry.setAttribute("position", new THREE.BufferAttribute(starPositions, 3))
    const starMaterial = new THREE.PointsMaterial({
      color: 0xd7ffe2,
      size: 0.013,
      transparent: true,
      opacity: 0.38,
      depthWrite: false,
    })
    const starField = new THREE.Points(starGeometry, starMaterial)
    group.add(starField)

    let frameId = 0
    const clock = new THREE.Clock()
    const deepSurface = new THREE.Color(0x061811)
    const midSurface = new THREE.Color(0x0b2f1d)
    const lightSurface = new THREE.Color(0x1d6a38)
    const deepEmissive = new THREE.Color(0x052916)
    const lightEmissive = new THREE.Color(0x46a965)
    const easeInOut = (value: number) => value * value * (3 - 2 * value)

    const resize = () => {
      const width = Math.max(host.clientWidth, 320)
      const height = Math.max(host.clientHeight, 420)
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    }

    const animate = () => {
      const elapsed = clock.getElapsedTime()
      const breathCycle = (Math.sin(elapsed * 0.58 - 0.75) + 1) / 2
      const breath = easeInOut(Math.pow(breathCycle, 1.35))
      const pulse = easeInOut(Math.pow((Math.sin(elapsed * 1.16 + 0.4) + 1) / 2, 2.1))
      group.rotation.y = elapsed * 0.16
      group.position.y = Math.sin(elapsed * 0.5) * 0.06
      foregroundLabelGroup.position.set(group.position.x, group.position.y, group.position.z)
      rimGrid.rotation.y = elapsed * 0.26
      dotField.rotation.y = Math.sin(elapsed * 0.32) * 0.018
      outlineGroup.scale.setScalar(1 + Math.sin(elapsed * 0.85) * 0.004)
      routeGroup.rotation.y = Math.sin(elapsed * 0.45) * 0.015
      starField.rotation.y = -elapsed * 0.05
      sphereMaterial.color.copy(deepSurface).lerp(midSurface, 0.62).lerp(lightSurface, breath * 0.42)
      sphereMaterial.emissive.lerpColors(deepEmissive, lightEmissive, breath * 0.64)
      sphereMaterial.emissiveIntensity = 0.24 + breath * 0.2 + pulse * 0.05
      atmosphereMaterial.opacity = 0.1 + breath * 0.055 + pulse * 0.022
      rimMaterial.opacity = 0.1 + breath * 0.04
      dotMaterial.opacity = 0.74 + breath * 0.12
      coastDotMaterial.opacity = 0.64 + breath * 0.16
      outlineMaterial.opacity = 0.66 + breath * 0.14
      outlineGlowMaterial.opacity = 0.16 + breath * 0.12
      routeMaterial.opacity = 0.38 + breath * 0.14
      accentRouteMaterial.opacity = 0.56 + breath * 0.16
      routeHaloMaterial.opacity = 0.09 + breath * 0.075
      accentRouteHaloMaterial.opacity = 0.1 + breath * 0.09
      routeMaterial.dashOffset = -elapsed * 0.09
      accentRouteMaterial.dashOffset = -elapsed * 0.12
      animatedRoutes.forEach((route, index) => {
        const t = (route.phase + elapsed * route.speed) % 1
        const position = route.curve.getPointAt(t)
        const tangent = route.curve.getTangentAt(t).normalize()
        route.plane.position.copy(position)
        route.plane.quaternion.setFromUnitVectors(upAxis, tangent)
        route.plane.scale.setScalar(0.84 + Math.sin(elapsed * 3 + index) * 0.08)
        route.spark.position.copy(route.curve.getPointAt((t + 0.965) % 1))
        route.spark.scale.setScalar(0.8 + breath * 0.34)
      })
      keywordSprites.forEach(({ sprite, baseScale, basePosition, phase, jumpEvery }) => {
        const float = Math.sin(elapsed * 1.1 + phase)
        const jumpProgress = ((elapsed + phase) % jumpEvery) / jumpEvery
        const jump = jumpProgress < 0.16 ? Math.sin((jumpProgress / 0.16) * Math.PI) : 0
        const scale = 1 + float * 0.018 + jump * 0.11
        sprite.position.set(basePosition.x, basePosition.y + float * 0.035 + jump * 0.18, basePosition.z)
        sprite.material.opacity = 0.58 + breath * 0.12 + jump * 0.2
        sprite.scale.set(baseScale.x * scale, baseScale.y * scale, baseScale.z)
      })
      markers.forEach((marker, index) => {
        marker.scale.setScalar(1 + Math.sin(elapsed * 2.2 + index * 0.55) * 0.28)
      })
      rimLight.intensity = 4.7 + Math.sin(elapsed * 1.4) * 0.48
      renderer.render(scene, camera)
      frameId = window.requestAnimationFrame(animate)
    }

    resize()
    animate()
    const observer = new ResizeObserver(resize)
    observer.observe(host)

    return () => {
      disposed = true
      window.cancelAnimationFrame(frameId)
      observer.disconnect()
      host.removeChild(renderer.domElement)
      sphereGeometry.dispose()
      sphereMaterial.dispose()
      rimGeometry.dispose()
      rimMaterial.dispose()
      atmosphereGeometry.dispose()
      atmosphereMaterial.dispose()
      dotGeometry.dispose()
      dotMaterial.dispose()
      coastDotGeometry.dispose()
      coastDotMaterial.dispose()
      mapGeometries.forEach((geometry) => geometry.dispose())
      outlineMaterial.dispose()
      outlineGlowMaterial.dispose()
      scene.remove(foregroundLabelGroup)
      routeGeometries.forEach((geometry) => geometry.dispose())
      routeMaterial.dispose()
      accentRouteMaterial.dispose()
      routeHaloMaterial.dispose()
      accentRouteHaloMaterial.dispose()
      flightPlaneGeometry.dispose()
      flightPlaneMaterial.dispose()
      flightSparkGeometry.dispose()
      flightSparkMaterial.dispose()
      animatedRoutes.forEach((route) => {
        if (route.spark.material !== flightSparkMaterial) {
          ;(route.spark.material as THREE.Material).dispose()
        }
      })
      keywordTextures.forEach((texture) => texture.dispose())
      keywordMaterials.forEach((material) => material.dispose())
      markerGeometry.dispose()
      markerMaterial.dispose()
      starGeometry.dispose()
      starMaterial.dispose()
      renderer.dispose()
    }
  }, [])

  return (
    <div className="trade-webgl-wrap">
      <div ref={hostRef} className="trade-webgl-canvas" />
    </div>
  )
}

function Capability({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="trade-capability-item">
      <div className="trade-capability-icon">{icon}</div>
      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
    </div>
  )
}
