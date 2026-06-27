import { FormEvent, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail, ShieldCheck, UserRound, UsersRound } from "lucide-react"
import AuthShell from "@/features/auth/AuthShell"
import { getApiError, isQqEmail, nextAuthRoute, saveAuthSession } from "@/features/auth/authUtils"
import { useCountdown } from "@/features/auth/useCountdown"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

type RegisterRole = "user" | "admin"

const roleOptions: Array<{ value: RegisterRole; title: string; description: string; icon: typeof UsersRound }> = [
  { value: "user", title: "普通用户", description: "处理询盘、文案、图片、翻译和日常 Agent 任务", icon: UsersRound },
  { value: "admin", title: "管理员", description: "管理知识库、接口配置、团队账号和系统数据", icon: ShieldCheck },
]

export default function Register() {
  const navigate = useNavigate()
  const countdown = useCountdown()
  const [role, setRole] = useState<RegisterRole>("user")
  const [username, setUsername] = useState("")
  const [qqEmail, setQqEmail] = useState("")
  const [code, setCode] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState("")

  const sendRegisterCode = async () => {
    setMessage("")
    if (!isQqEmail(qqEmail)) {
      setMessage("请输入有效的 QQ 邮箱。")
      return
    }
    setSendingCode(true)
    try {
      const { data } = await api.post("/auth/code/send", { email: qqEmail.trim(), purpose: "register" })
      setMessage(data.message || "验证码已发送。")
      countdown.start()
    } catch (error) {
      setMessage(getApiError(error, "验证码发送失败。"))
    } finally {
      setSendingCode(false)
    }
  }

  const submitRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMessage("")
    if (username.trim().length < 3) {
      setMessage("用户名至少需要 3 个字符。")
      return
    }
    if (!isQqEmail(qqEmail)) {
      setMessage("请输入有效的 QQ 邮箱。")
      return
    }
    if (!code.trim()) {
      setMessage("请输入邮箱验证码。")
      return
    }
    if (password.length < 8) {
      setMessage("密码至少需要 8 个字符。")
      return
    }
    if (password !== confirmPassword) {
      setMessage("两次输入的密码不一致。")
      return
    }

    setSubmitting(true)
    try {
      const { data } = await api.post("/auth/register", {
        username: username.trim(),
        qqEmail: qqEmail.trim(),
        code: code.trim(),
        password,
        role,
      })
      saveAuthSession(data)
      navigate(nextAuthRoute(data.role), { replace: true })
    } catch (error) {
      setMessage(getApiError(error, "注册失败，请稍后重试。"))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="新账号注册"
      title="创建杰创智能工作区账号"
      description="使用 QQ 邮箱验证码完成注册，后续可用于验证码登录、找回密码和账号安全验证。管理员账号拥有更多配置能力，请谨慎分配。"
    >
      <div className="mb-6">
        <p className="text-sm font-black text-[#0B918C]">账号注册</p>
        <h2 className="mt-2 text-2xl font-black tracking-tight text-[#17211F]">选择身份并完成验证</h2>
      </div>

      <div className="mb-6 grid grid-cols-2 gap-2 rounded-[10px] bg-[#EEF5F1] p-1.5">
        {roleOptions.map((option) => {
          const Icon = option.icon
          const selected = role === option.value
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => setRole(option.value)}
              className={cn("flex min-h-[86px] flex-col items-start justify-between rounded-[8px] px-3 py-3 text-left transition-all active:scale-[0.98]", selected ? "bg-white text-[#17211F] shadow-[0_12px_28px_-24px_rgba(28,74,62,0.72)]" : "text-[#6A766F] hover:bg-white/60")}
            >
              <span className="flex items-center gap-2 text-sm font-black"><Icon size={16} />{option.title}</span>
              <span className="text-[11px] leading-4 text-current/70">{option.description}</span>
            </button>
          )
        })}
      </div>

      <form className="space-y-4" onSubmit={submitRegister}>
        <Field label="用户名" htmlFor="register-username">
          <UserRound className="auth-input-icon" size={16} />
          <Input id="register-username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="至少 3 个字符" className="auth-input pl-10" autoComplete="username" />
        </Field>

        <Field label="QQ 邮箱" htmlFor="register-email">
          <Mail className="auth-input-icon" size={16} />
          <Input id="register-email" value={qqEmail} onChange={(event) => setQqEmail(event.target.value)} placeholder="example@qq.com" className="auth-input pl-10" autoComplete="email" />
        </Field>

        <div className="grid grid-cols-[minmax(0,1fr)_128px] gap-2">
          <Input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" className="auth-input" inputMode="numeric" maxLength={6} />
          <Button type="button" variant="outline" className="h-12 rounded-[8px]" onClick={sendRegisterCode} disabled={sendingCode || countdown.running}>
            {countdown.running ? `${countdown.seconds}s` : sendingCode ? "发送中" : "获取验证码"}
          </Button>
        </div>

        <Field label="密码" htmlFor="register-password">
          <LockKeyhole className="auth-input-icon" size={16} />
          <Input id="register-password" value={password} onChange={(event) => setPassword(event.target.value)} type={showPassword ? "text" : "password"} placeholder="至少 8 位，建议包含字母和数字" className="auth-input pl-10 pr-11" autoComplete="new-password" />
          <PasswordToggle visible={showPassword} onClick={() => setShowPassword((value) => !value)} />
        </Field>

        <Field label="确认密码" htmlFor="register-confirm">
          <LockKeyhole className="auth-input-icon" size={16} />
          <Input id="register-confirm" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} type={showPassword ? "text" : "password"} placeholder="再次输入密码" className="auth-input pl-10" autoComplete="new-password" />
        </Field>

        <p className={cn("min-h-5 text-sm font-semibold", message.includes("已发送") ? "text-[#1F5F53]" : "text-destructive")}>{message}</p>

        <Button type="submit" size="lg" className="h-12 w-full rounded-[8px] bg-[#17211F] text-white hover:bg-[#22312D]" disabled={submitting}>
          {submitting ? "正在创建" : "创建账号并进入"}
          <ArrowRight size={17} />
        </Button>

        <div className="text-center text-sm text-muted-foreground">
          已有账号？
          <Link to="/login" className="ml-1 font-black text-[#0B918C] hover:text-[#087C78]">返回登录</Link>
        </div>
      </form>
    </AuthShell>
  )
}

function Field({ label, htmlFor, children }: { label: string; htmlFor: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={htmlFor}>{label}</Label>
      <div className="relative">{children}</div>
    </div>
  )
}

function PasswordToggle({ visible, onClick }: { visible: boolean; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="absolute right-2 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-[7px] text-muted-foreground transition-colors hover:bg-[#EEF5F1] hover:text-foreground" aria-label={visible ? "隐藏密码" : "显示密码"}>
      {visible ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  )
}
