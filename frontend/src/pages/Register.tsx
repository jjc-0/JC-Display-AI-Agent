import { FormEvent, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowRight, Check, Eye, EyeOff, LockKeyhole, ShieldCheck, UserRound, UsersRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import CompanyLogoMark from "@/components/brand/CompanyLogoMark"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

type RegisterRole = "user" | "admin"

const roleOptions: Array<{
  value: RegisterRole
  title: string
  description: string
  icon: typeof UsersRound
}> = [
  {
    value: "user",
    title: "普通用户",
    description: "创建业务协同与 AI Agent 使用账号",
    icon: UsersRound,
  },
  {
    value: "admin",
    title: "管理员",
    description: "创建平台配置与数据管理账号",
    icon: ShieldCheck,
  },
]

export default function Register() {
  const navigate = useNavigate()
  const [role, setRole] = useState<RegisterRole>("user")
  const [account, setAccount] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState("")

  const activeRole = roleOptions.find((item) => item.value === role) ?? roleOptions[0]

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError("")

    if (!account.trim() || !password.trim() || !confirmPassword.trim()) {
      setError("请完整填写账号和密码。")
      return
    }

    if (account.trim().length < 3) {
      setError("账号至少需要 3 个字符。")
      return
    }

    if (password.length < 6) {
      setError("密码至少需要 6 位。")
      return
    }

    if (password !== confirmPassword) {
      setError("两次输入的密码不一致。")
      return
    }

    setSubmitting(true)
    try {
      const response = await api.post("/auth/register", {
        username: account.trim(),
        password,
        role,
      })
      const { token, username, role: userRole } = response.data
      localStorage.setItem("jc-auth-token", token)
      localStorage.setItem("jc-display-login-role", userRole)
      localStorage.setItem("jc-display-login-account", username)
      navigate(userRole === "admin" ? "/dashboard" : "/agent-chat", { replace: true })
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        "注册失败，请检查网络后重试。"
      setError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="relative h-[100dvh] overflow-y-auto overflow-x-hidden bg-[#F7FAF8] text-foreground">
      <div className="pointer-events-none absolute inset-0 login-grid-bg" />
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="register-diagonal-band" />
      </div>

      <div className="relative mx-auto grid min-h-[100dvh] w-full max-w-[1180px] grid-cols-1 px-4 py-5 md:grid-cols-[minmax(0,0.85fr)_minmax(420px,0.72fr)] md:px-8 lg:px-10">
        <section className="flex min-h-[28dvh] flex-col justify-between py-6 md:min-h-0 md:py-10">
          <Link to="/login" className="flex w-fit items-center gap-3">
            <CompanyLogoMark className="h-11 w-14 flex-shrink-0" />
            <div className="leading-tight">
              <p className="text-[20px] font-bold tracking-tight">JCDisplayPackaging</p>
              <p className="text-[16px] font-medium text-muted-foreground">杰创展示 & 包装</p>
            </div>
          </Link>

          <div className="max-w-[620px] pb-4 pt-12 md:pb-16 md:pt-0">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-[#D8E7DF] bg-white/70 px-3 py-1.5 text-xs font-medium text-[#477260] shadow-[0_12px_32px_-24px_rgba(32,92,69,0.45)]">
              <span className="h-2 w-2 rounded-full bg-[#0EA5A3] login-breath-dot" />
              新用户注册
            </div>
            <h1 className="max-w-[10ch] text-[2.45rem] font-semibold leading-[0.98] tracking-tight text-[#17211F] sm:text-5xl md:text-6xl">
              创建杰创智能工作区账号
            </h1>
            <p className="mt-6 max-w-[58ch] text-base leading-7 text-[#62706A]">
              使用统一账号进入外贸业务、智能体协作、知识库与平台配置。注册完成后会直接进入对应身份的工作区。
            </p>

            <div className="mt-8 grid max-w-[520px] grid-cols-3 gap-3">
              {["GLOBAL TRADE", "AI AGENT", "SECURE"].map((item) => (
                <div
                  key={item}
                  className="rounded-[8px] border border-[#DCE7E2] bg-white/64 px-3 py-3 text-xs font-bold tracking-[0.08em] text-[#22312D] shadow-[0_18px_36px_-30px_rgba(31,76,65,0.45)]"
                >
                  <Check size={15} className="mb-4 text-[#0B918C]" />
                  {item}
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="flex items-center justify-center py-4 md:justify-end md:py-10">
          <div className="w-full max-w-[455px] rounded-[8px] border border-white/80 bg-white/86 p-5 shadow-[0_28px_80px_-48px_rgba(32,63,58,0.55)] backdrop-blur-xl sm:p-7">
            <div className="mb-6">
              <p className="text-sm font-semibold text-[#0B918C]">账户注册</p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight text-[#17211F]">选择身份并创建账号</h2>
            </div>

            <div className="grid grid-cols-2 gap-2 rounded-[8px] bg-[#EEF5F1] p-1.5" role="tablist" aria-label="注册身份">
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
              <div className="space-y-2">
                <Label htmlFor="register-account">账号</Label>
                <div className="relative">
                  <UserRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="register-account"
                    value={account}
                    onChange={(event) => setAccount(event.target.value)}
                    placeholder="请输入新账号"
                    className="h-12 rounded-[8px] border-[#DCE7E2] bg-white pl-10"
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="register-password">密码</Label>
                <div className="relative">
                  <LockKeyhole className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="register-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    type={showPassword ? "text" : "password"}
                    placeholder="至少 6 位密码"
                    className="h-12 rounded-[8px] border-[#DCE7E2] bg-white pl-10 pr-11"
                    autoComplete="new-password"
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

              <div className="space-y-2">
                <Label htmlFor="register-confirm">确认密码</Label>
                <Input
                  id="register-confirm"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  type={showPassword ? "text" : "password"}
                  placeholder="再次输入密码"
                  className="h-12 rounded-[8px] border-[#DCE7E2] bg-white"
                  autoComplete="new-password"
                />
              </div>

              <div className="min-h-5">
                {error && <p className="text-sm font-medium text-destructive">{error}</p>}
              </div>

              <Button
                type="submit"
                size="lg"
                className="h-12 w-full rounded-[8px] bg-[#17211F] text-white hover:bg-[#22312D]"
                disabled={submitting}
              >
                {submitting ? "正在创建..." : `创建${activeRole.title}账号`}
                <ArrowRight size={17} />
              </Button>

              <div className="text-center text-sm text-muted-foreground">
                已有账号？
                <Link to="/login" className="ml-1 font-semibold text-[#0B918C] hover:text-[#087C78]">
                  返回登录
                </Link>
              </div>
            </form>
          </div>
        </section>
      </div>
    </main>
  )
}
