import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import { CheckCircle2, Factory, ShieldCheck, Sparkles } from "lucide-react"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"

interface AuthShellProps {
  eyebrow: string
  title: string
  description: string
  children: React.ReactNode
  footer?: React.ReactNode
}

const trustItems = [
  { icon: ShieldCheck, label: "企业级权限", text: "Token 登录态、验证码校验、角色分流" },
  { icon: Factory, label: "外贸业务链路", text: "询盘、报价、打样、物流和售后集中管理" },
  { icon: Sparkles, label: "AI Agent 执行", text: "面向产品库、知识库和客户沟通持续参与" },
]

const atmosphereKeywords = [
  { text: "GLOBAL TRADE", className: "login-keyword--trade" },
  { text: "RECYCLABLE", className: "login-keyword--recycle" },
  { text: "SECURE", className: "login-keyword--secure" },
  { text: "RELIABLE", className: "login-keyword--reliable" },
  { text: "AI AGENT", className: "login-keyword--agent" },
  { text: "EXPORT READY", className: "login-keyword--export" },
]

export const AUTH_SPLASH_SEEN_KEY = "jc-auth-splash-seen"

export default function AuthShell({ eyebrow, title, description, children, footer }: AuthShellProps) {
  const [showSplash, setShowSplash] = useState(false)

  useEffect(() => {
    if (sessionStorage.getItem(AUTH_SPLASH_SEEN_KEY)) return
    setShowSplash(true)
    sessionStorage.setItem(AUTH_SPLASH_SEEN_KEY, "1")
  }, [])

  return (
    <main className="relative min-h-[100dvh] overflow-hidden bg-transparent text-[#171916]">
      {showSplash && <SplashIntro />}
      <div className="pointer-events-none absolute inset-0 login-grid-bg" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[46dvh] bg-[linear-gradient(135deg,rgba(4,139,202,0.06),rgba(248,251,250,0.38)_44%,rgba(20,148,73,0.06))]" />
      <LoginAtmosphere />

      <div className="relative mx-auto grid min-h-[100dvh] w-full max-w-[1320px] grid-cols-1 gap-8 px-4 py-5 md:grid-cols-[minmax(0,0.95fr)_minmax(420px,0.78fr)] md:px-8 lg:px-10">
        <section className="flex min-h-[34dvh] flex-col justify-between py-6 md:min-h-0 md:py-10">
          <Link to="/login" className="flex w-fit items-center gap-3">
            <CompanyLogoMark className="h-12 w-16 flex-shrink-0" />
            <div className="leading-none">
              <p className="text-[20px] font-black tracking-tight text-[#139CDA]">JC</p>
              <p className="mt-1 text-[18px] font-black tracking-tight text-[#17211F]">DisplayPackaging</p>
              <p className="mt-1 text-[12px] font-semibold text-[#74766F]">杰创展示 & 包装</p>
            </div>
          </Link>

          <div className="max-w-[660px] pb-4 pt-12 md:pb-16 md:pt-0">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-[#D8E7DF] bg-white/72 px-3 py-1.5 text-xs font-bold text-[#477260] shadow-[0_12px_32px_-24px_rgba(32,92,69,0.45)]">
              <span className="h-2 w-2 rounded-full bg-[#0B918C] login-breath-dot" />
              {eyebrow}
            </div>
            <h1 className="max-w-[11ch] text-[2.35rem] font-black leading-[0.98] tracking-tight text-[#17211F] sm:text-5xl md:text-6xl">
              {title}
            </h1>
            <p className="mt-6 max-w-[58ch] text-base leading-7 text-[#62706A]">{description}</p>

            <div className="mt-9 grid max-w-[620px] grid-cols-1 gap-3 sm:grid-cols-3">
              {trustItems.map((item, index) => {
                const Icon = item.icon
                return (
                  <div
                    key={item.label}
                    className="login-benefit-tile rounded-[8px] border border-[#DCE7E2] bg-white/70 px-4 py-4 shadow-[0_18px_36px_-30px_rgba(31,76,65,0.45)]"
                    style={{ animationDelay: `${0.08 + index * 0.08}s` }}
                  >
                    <Icon size={17} className="mb-4 text-[#0B918C]" />
                    <div className="text-sm font-black text-[#22312D]">{item.label}</div>
                    <div className="mt-2 text-xs leading-relaxed text-[#74766F]">{item.text}</div>
                  </div>
                )
              })}
            </div>
          </div>

          {footer || (
            <div className="hidden items-center gap-2 text-xs font-semibold text-[#74766F] md:flex">
              <CheckCircle2 size={15} className="text-[#2F6B5F]" />
              QQ 邮箱验证码由企业 SMTP 发送，生产环境请使用授权码。
            </div>
          )}
        </section>

        <section className="flex items-center justify-center py-4 md:justify-end md:py-10">
          <div className="w-full max-w-[462px] rounded-[18px] border border-white/80 bg-white/90 p-5 shadow-[0_28px_80px_-48px_rgba(32,63,58,0.55)] backdrop-blur-xl sm:p-7">
            {children}
          </div>
        </section>
      </div>
    </main>
  )
}

function SplashIntro() {
  return (
    <div className="login-splash pointer-events-none fixed inset-0 flex items-center justify-center bg-[#F8FBFA]">
      <div className="login-splash-diagonal" aria-hidden="true" />
      <div className="login-splash-inner flex w-[min(88vw,760px)] flex-col items-center gap-7">
        <div className="login-logo-stage relative h-[236px] w-[318px] sm:h-[300px] sm:w-[410px]" aria-hidden="true">
          <img src="/logo-leaf-blue.png" alt="" className="login-leaf login-leaf-blue absolute" />
          <img src="/logo-leaf-green.png" alt="" className="login-leaf login-leaf-green absolute" />
        </div>
        <div className="login-wordmark text-center">
          <p className="text-3xl font-bold tracking-tight text-[#151B1A] sm:text-5xl">
            <span className="text-[#139CDA]">JC</span>DisplayPackaging
          </p>
          <p className="mt-3 text-lg font-semibold tracking-[0.42em] text-[#202826] sm:text-2xl">
            杰创展示&包装
          </p>
        </div>
      </div>
    </div>
  )
}

function LoginAtmosphere() {
  return (
    <div className="login-atmosphere pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      <svg className="login-trade-map" viewBox="0 0 1200 720" preserveAspectRatio="none">
        <path className="login-trade-route login-trade-route--blue" d="M68 452 C246 300 382 350 512 248 C704 96 856 158 1128 82" />
        <path className="login-trade-route login-trade-route--green" d="M92 610 C244 478 430 512 566 398 C732 260 876 300 1108 204" />
        <path className="login-trade-route login-trade-route--blue" d="M184 512 C286 442 324 366 406 330 C512 284 574 332 666 252" />
        <path className="login-trade-route login-trade-route--green" d="M430 540 C512 446 578 442 632 384 C694 318 720 252 804 214" />
        <g className="login-route-node login-route-node--one">
          <circle cx="214" cy="348" r="5" />
          <circle cx="214" cy="348" r="18" />
        </g>
        <g className="login-route-node login-route-node--two">
          <circle cx="536" cy="236" r="5" />
          <circle cx="536" cy="236" r="18" />
        </g>
        <g className="login-route-node login-route-node--three">
          <circle cx="860" cy="276" r="5" />
          <circle cx="860" cy="276" r="18" />
        </g>
      </svg>

      <div className="login-agent-core">
        <span />
        <span />
        <span />
      </div>

      {atmosphereKeywords.map((item, index) => (
        <div key={item.text} className={`login-keyword-pop ${item.className}`} style={{ animationDelay: `${3.7 + index * 0.18}s` }}>
          {item.text}
        </div>
      ))}
    </div>
  )
}
