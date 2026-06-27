import { FormEvent, useMemo, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import {
  ArrowRight,
  Check,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
  UserRound,
  UsersRound,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

type LoginRole = "user" | "admin"
type LoginMode = "password" | "code"

const roleOptions: Array<{
  value: LoginRole
  title: string
  description: string
  icon: typeof UsersRound
}> = [
  {
    value: "user",
    title: "普通用户",
    description: "进入业务协同与 AI Agent 工作区",
    icon: UsersRound,
  },
  {
    value: "admin",
    title: "管理员",
    description: "进入数据看板与平台配置中心",
    icon: ShieldCheck,
  },
]

const userBenefits = ["询盘跟进", "产品图片", "文案生成"]
const adminBenefits = ["数据看板", "知识库", "接口授权"]
const atmosphereKeywords = [
  { text: "GLOBAL TRADE", className: "login-keyword--trade" },
  { text: "RECYCLABLE", className: "login-keyword--recycle" },
  { text: "SECURE", className: "login-keyword--secure" },
  { text: "RELIABLE", className: "login-keyword--reliable" },
  { text: "AI AGENT", className: "login-keyword--agent" },
  { text: "EXPORT READY", className: "login-keyword--export" },
]

export default function Login() {
  const navigate = useNavigate()
  const [role, setRole] = useState<LoginRole>("user")
  const [account, setAccount] = useState("")
  const [password, setPassword] = useState("")
  const [codeEmail, setCodeEmail] = useState("")
  const [loginCode, setLoginCode] = useState("")
  const [loginMode, setLoginMode] = useState<LoginMode>("password")
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [error, setError] = useState("")
  const [resetOpen, setResetOpen] = useState(false)
  const [resetForm, setResetForm] = useState({ email: "", code: "", newPassword: "", confirmPassword: "" })
  const [resetSending, setResetSending] = useState(false)
  const [resetSubmitting, setResetSubmitting] = useState(false)
  const [resetMessage, setResetMessage] = useState("")

  const activeRole = roleOptions.find((item) => item.value === role) ?? roleOptions[0]
  const ActiveIcon = activeRole.icon
  const benefits = useMemo(() => (role === "admin" ? adminBenefits : userBenefits), [role])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError("")

    if (loginMode === "password" && (!account.trim() || !password.trim())) {
      setError("请输入账号和密码后继续。")
      return
    }
    if (loginMode === "code" && (!codeEmail.trim() || !loginCode.trim())) {
      setError("请输入 QQ 邮箱和验证码后继续。")
      return
    }

    setSubmitting(true)
    try {
      const response = loginMode === "password"
        ? await api.post("/auth/login", { account: account.trim(), password })
        : await api.post("/auth/login/code", { email: codeEmail.trim(), code: loginCode.trim() })
      const { token, username, role: userRole } = response.data
      localStorage.setItem("jc-auth-token", token)
      localStorage.setItem("jc-display-login-role", userRole)
      localStorage.setItem("jc-display-login-account", username)
      navigate(userRole === "admin" ? "/dashboard" : "/agent-chat", { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        "登录失败，请检查网络后重试。"
      setError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const sendLoginCode = async () => {
    setSendingCode(true)
    setError("")
    try {
      const { data } = await api.post("/auth/code/send", {
        email: codeEmail.trim(),
        purpose: "login",
      })
      setError(data.message || "验证码已发送。")
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        "验证码发送失败。"
      setError(msg)
    } finally {
      setSendingCode(false)
    }
  }

  const sendResetCode = async () => {
    setResetSending(true)
    setResetMessage("")
    try {
      const { data } = await api.post("/auth/code/send", {
        email: resetForm.email.trim(),
        purpose: "reset_password",
      })
      setResetMessage(data.message || "验证码已发送。")
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        "验证码发送失败。"
      setResetMessage(msg)
    } finally {
      setResetSending(false)
    }
  }

  const resetPassword = async () => {
    setResetSubmitting(true)
    setResetMessage("")
    try {
      const { data } = await api.post("/auth/password/reset", {
        email: resetForm.email.trim(),
        code: resetForm.code.trim(),
        newPassword: resetForm.newPassword,
        confirmPassword: resetForm.confirmPassword,
      })
      setResetMessage(data.message || "密码已重置。")
      setResetForm({ email: "", code: "", newPassword: "", confirmPassword: "" })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        "密码重置失败。"
      setResetMessage(msg)
    } finally {
      setResetSubmitting(false)
    }
  }

  return (
    <main className="relative h-[100dvh] overflow-y-auto overflow-x-hidden bg-[#F7FAF8] text-foreground">
      <SplashIntro />

      <div className="pointer-events-none absolute inset-0 login-grid-bg" />
      <LoginAtmosphere />
      <div className="relative mx-auto grid min-h-[100dvh] w-full max-w-[1320px] grid-cols-1 px-4 py-5 md:grid-cols-[minmax(0,0.95fr)_minmax(420px,0.78fr)] md:px-8 lg:px-10">
        <section className="flex min-h-[38dvh] flex-col justify-between py-6 md:min-h-0 md:py-10">
          <div className="flex items-center gap-3">
            <CompanyLogoMark className="h-11 w-14 flex-shrink-0" />
            <div className="leading-tight">
              <p className="text-[20px] font-bold tracking-tight">JCDisplayPackaging</p>
              <p className="text-[16px] font-medium text-muted-foreground">杰创展示 & 包装</p>
            </div>
          </div>

          <div className="login-copy-reveal max-w-[640px] pb-4 pt-14 md:pb-12 md:pt-0">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-[#D8E7DF] bg-white/70 px-3 py-1.5 text-xs font-medium text-[#477260] shadow-[0_12px_32px_-24px_rgba(32,92,69,0.45)]">
              <span className="h-2 w-2 rounded-full bg-[#0EA5A3] login-breath-dot" />
              AI 外贸业务中台
            </div>
            <h1 className="max-w-[10ch] text-[2.45rem] font-semibold leading-[0.98] tracking-tight text-[#17211F] sm:text-5xl md:text-6xl">
              登录到杰创智能工作区
            </h1>
            <p className="mt-6 max-w-[58ch] text-base leading-7 text-[#62706A]">
              统一进入询盘评分、图片识别、知识库检索与 Agent 执行中心。普通用户专注业务处理，管理员负责配置、数据与权限。
            </p>

            <div className="mt-8 grid max-w-[520px] grid-cols-3 gap-3">
              {benefits.map((item, index) => (
                <div
                  key={item}
                  className="login-benefit-tile rounded-[8px] border border-[#DCE7E2] bg-white/64 px-3 py-3 text-sm font-semibold text-[#22312D] shadow-[0_18px_36px_-30px_rgba(31,76,65,0.45)]"
                  style={{ animationDelay: `${0.16 + index * 0.08}s` }}
                >
                  <Check size={15} className="mb-4 text-[#0B918C]" />
                  {item}
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="flex items-center justify-center py-4 md:justify-end md:py-10">
          <div className="login-panel-reveal w-full max-w-[455px] rounded-[8px] border border-white/80 bg-white/86 p-5 shadow-[0_28px_80px_-48px_rgba(32,63,58,0.55)] backdrop-blur-xl sm:p-7">
            <div className="mb-6 flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-[#0B918C]">账户登录</p>
                <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#17211F]">选择身份后继续</h2>
              </div>
              <div className="flex h-11 w-11 items-center justify-center rounded-[8px] border border-[#DDE9E4] bg-[#F4FAF7] text-[#0B918C]">
                <ActiveIcon size={21} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2 rounded-[8px] bg-[#EEF5F1] p-1.5" role="tablist" aria-label="登录身份">
              {roleOptions.map((option) => {
                const Icon = option.icon
                const selected = role === option.value
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setRole(option.value)}
                    className={cn(
                      "flex min-h-[78px] flex-col items-start justify-between rounded-[7px] px-3 py-3 text-left transition-all duration-300 active:scale-[0.98]",
                      selected
                        ? "bg-white text-[#17211F] shadow-[0_14px_32px_-26px_rgba(28,74,62,0.75)]"
                        : "text-[#6A766F] hover:bg-white/55 hover:text-[#273832]"
                    )}
                    aria-pressed={selected}
                  >
                    <span className="flex items-center gap-2 text-sm font-semibold">
                      <Icon size={16} />
                      {option.title}
                    </span>
                    <span className="line-clamp-2 text-[11px] leading-4 text-current/68">{option.description}</span>
                  </button>
                )
              })}
            </div>

            <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
              <div className="grid grid-cols-2 gap-2 rounded-[8px] bg-[#EEF5F1] p-1.5">
                <button
                  type="button"
                  onClick={() => setLoginMode("password")}
                  className={cn("rounded-[7px] px-3 py-2 text-sm font-semibold transition-colors", loginMode === "password" ? "bg-white text-[#17211F]" : "text-[#6A766F]")}
                >
                  密码登录
                </button>
                <button
                  type="button"
                  onClick={() => setLoginMode("code")}
                  className={cn("rounded-[7px] px-3 py-2 text-sm font-semibold transition-colors", loginMode === "code" ? "bg-white text-[#17211F]" : "text-[#6A766F]")}
                >
                  QQ 验证码
                </button>
              </div>

              {loginMode === "password" ? (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="login-account">账号</Label>
                    <div className="relative">
                      <UserRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        id="login-account"
                        value={account}
                        onChange={(event) => setAccount(event.target.value)}
                        placeholder={role === "admin" ? "请输入管理员账号" : "请输入用户账号"}
                        className="h-12 rounded-[8px] border-[#DCE7E2] bg-white pl-10"
                        autoComplete="username"
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="login-password">密码</Label>
                    <div className="relative">
                      <LockKeyhole className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        id="login-password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        type={showPassword ? "text" : "password"}
                        placeholder="请输入登录密码"
                        className="h-12 rounded-[8px] border-[#DCE7E2] bg-white pl-10 pr-11"
                        autoComplete="current-password"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword((value) => !value)}
                        className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-[7px] text-muted-foreground transition-colors hover:bg-[#EEF5F1] hover:text-foreground"
                        aria-label={showPassword ? "隐藏密码" : "显示密码"}
                      >
                        {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>
                </>
              ) : (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="login-code-email">QQ 邮箱</Label>
                    <Input
                      id="login-code-email"
                      value={codeEmail}
                      onChange={(event) => setCodeEmail(event.target.value)}
                      placeholder="请输入已绑定的 QQ 邮箱"
                      className="h-12 rounded-[8px] border-[#DCE7E2] bg-white"
                      autoComplete="email"
                    />
                  </div>
                  <div className="grid grid-cols-[minmax(0,1fr)_126px] gap-2">
                    <Input
                      value={loginCode}
                      onChange={(event) => setLoginCode(event.target.value)}
                      placeholder="6 位验证码"
                      className="h-12 rounded-[8px] border-[#DCE7E2] bg-white"
                    />
                    <Button type="button" variant="outline" className="h-12" onClick={sendLoginCode} disabled={sendingCode || !codeEmail.trim()}>
                      {sendingCode ? "发送中" : "发验证码"}
                    </Button>
                  </div>
                </>
              )}

              <div className="min-h-5">
                {error && <p className="text-sm font-medium text-destructive">{error}</p>}
              </div>

              <button
                type="button"
                className="text-sm font-semibold text-[#0B918C] hover:text-[#087C78]"
                onClick={() => setResetOpen((open) => !open)}
              >
                忘记密码？
              </button>

              {resetOpen && (
                <div className="space-y-3 rounded-[8px] border border-[#DCE7E2] bg-[#F8FBFA] p-3">
                  <Label>使用已绑定 QQ 邮箱找回密码</Label>
                  <Input value={resetForm.email} onChange={(event) => setResetForm((prev) => ({ ...prev, email: event.target.value }))} placeholder="QQ 邮箱" />
                  <div className="grid grid-cols-[minmax(0,1fr)_126px] gap-2">
                    <Input value={resetForm.code} onChange={(event) => setResetForm((prev) => ({ ...prev, code: event.target.value }))} placeholder="验证码" />
                    <Button type="button" variant="outline" onClick={sendResetCode} disabled={resetSending || !resetForm.email.trim()}>
                      {resetSending ? "发送中" : "发验证码"}
                    </Button>
                  </div>
                  <Input type="password" value={resetForm.newPassword} onChange={(event) => setResetForm((prev) => ({ ...prev, newPassword: event.target.value }))} placeholder="新密码，至少 8 位" />
                  <Input type="password" value={resetForm.confirmPassword} onChange={(event) => setResetForm((prev) => ({ ...prev, confirmPassword: event.target.value }))} placeholder="确认新密码" />
                  {resetMessage && <p className="text-xs font-semibold text-[#1F5F53]">{resetMessage}</p>}
                  <Button type="button" variant="outline" className="w-full" onClick={resetPassword} disabled={resetSubmitting}>
                    {resetSubmitting ? "重置中" : "重置密码"}
                  </Button>
                </div>
              )}

              <Button
                type="submit"
                size="lg"
                className="h-12 w-full rounded-[8px] bg-[#17211F] text-white hover:bg-[#22312D]"
                disabled={submitting}
              >
                {submitting ? "正在进入..." : `进入${activeRole.title}工作区`}
                <ArrowRight size={17} />
              </Button>
              <div className="text-center text-sm text-muted-foreground">
                新用户？
                <Link to="/register" className="ml-1 font-semibold text-[#0B918C] hover:text-[#087C78]">
                  创建账号
                </Link>
              </div>
            </form>
          </div>
        </section>
      </div>
    </main>
  )
}

function LoginAtmosphere() {
  return (
    <div className="login-atmosphere pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      <svg className="login-trade-map" viewBox="0 0 1200 720" preserveAspectRatio="none">
        <path
          className="login-trade-route login-trade-route--blue"
          d="M68 452 C246 300 382 350 512 248 C704 96 856 158 1128 82"
        />
        <path
          className="login-trade-route login-trade-route--green"
          d="M92 610 C244 478 430 512 566 398 C732 260 876 300 1108 204"
        />
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
        <div
          key={item.text}
          className={cn("login-keyword-pop", item.className)}
          style={{ animationDelay: `${3.7 + index * 0.18}s` }}
        >
          {item.text}
        </div>
      ))}
    </div>
  )
}

function SplashIntro() {
  return (
    <div className="login-splash pointer-events-none fixed inset-0 flex items-center justify-center bg-[#F8FBFA]">
      <div className="login-splash-diagonal" aria-hidden="true" />
      <div className="login-splash-inner flex w-[min(88vw,760px)] flex-col items-center gap-7">
        <div className="login-logo-stage relative h-[236px] w-[318px] sm:h-[300px] sm:w-[410px]" aria-hidden="true">
          <img
            src="/logo-leaf-blue.png"
            alt=""
            className="login-leaf login-leaf-blue absolute"
          />
          <img
            src="/logo-leaf-green.png"
            alt=""
            className="login-leaf login-leaf-green absolute"
          />
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
