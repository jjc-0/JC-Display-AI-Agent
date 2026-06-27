import { FormEvent, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail, ShieldCheck, UserRound } from "lucide-react"
import AuthShell from "@/features/auth/AuthShell"
import { getApiError, isQqEmail, nextAuthRoute, saveAuthSession } from "@/features/auth/authUtils"
import { useCountdown } from "@/features/auth/useCountdown"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

type LoginMode = "password" | "code"

export default function Login() {
  const navigate = useNavigate()
  const loginCountdown = useCountdown()
  const resetCountdown = useCountdown()
  const [mode, setMode] = useState<LoginMode>("password")
  const [account, setAccount] = useState("")
  const [password, setPassword] = useState("")
  const [email, setEmail] = useState("")
  const [code, setCode] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [message, setMessage] = useState("")
  const [resetOpen, setResetOpen] = useState(false)
  const [resetForm, setResetForm] = useState({ email: "", code: "", newPassword: "", confirmPassword: "" })
  const [resetSubmitting, setResetSubmitting] = useState(false)
  const [resetSending, setResetSending] = useState(false)
  const [resetMessage, setResetMessage] = useState("")

  const submitLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMessage("")
    if (mode === "password" && (!account.trim() || !password)) {
      setMessage("请输入账号和密码。")
      return
    }
    if (mode === "code" && (!isQqEmail(email) || !code.trim())) {
      setMessage("请输入 QQ 邮箱和 6 位验证码。")
      return
    }

    setSubmitting(true)
    try {
      const { data } = mode === "password"
        ? await api.post("/auth/login", { account: account.trim(), password })
        : await api.post("/auth/login/code", { email: email.trim(), code: code.trim() })
      saveAuthSession(data)
      navigate(nextAuthRoute(data.role), { replace: true })
    } catch (error) {
      setMessage(getApiError(error, "登录失败，请稍后重试。"))
    } finally {
      setSubmitting(false)
    }
  }

  const sendLoginCode = async () => {
    setMessage("")
    if (!isQqEmail(email)) {
      setMessage("请输入有效的 QQ 邮箱。")
      return
    }
    setSendingCode(true)
    try {
      const { data } = await api.post("/auth/code/send", { email: email.trim(), purpose: "login" })
      setMessage(data.message || "验证码已发送。")
      loginCountdown.start()
    } catch (error) {
      setMessage(getApiError(error, "验证码发送失败。"))
    } finally {
      setSendingCode(false)
    }
  }

  const sendResetCode = async () => {
    setResetMessage("")
    if (!isQqEmail(resetForm.email)) {
      setResetMessage("请输入有效的 QQ 邮箱。")
      return
    }
    setResetSending(true)
    try {
      const { data } = await api.post("/auth/code/send", { email: resetForm.email.trim(), purpose: "reset_password" })
      setResetMessage(data.message || "验证码已发送。")
      resetCountdown.start()
    } catch (error) {
      setResetMessage(getApiError(error, "验证码发送失败。"))
    } finally {
      setResetSending(false)
    }
  }

  const resetPassword = async () => {
    setResetMessage("")
    if (!isQqEmail(resetForm.email) || !resetForm.code.trim() || !resetForm.newPassword || !resetForm.confirmPassword) {
      setResetMessage("请完整填写找回密码信息。")
      return
    }
    if (resetForm.newPassword.length < 8) {
      setResetMessage("新密码至少需要 8 个字符。")
      return
    }
    if (resetForm.newPassword !== resetForm.confirmPassword) {
      setResetMessage("两次输入的新密码不一致。")
      return
    }

    setResetSubmitting(true)
    try {
      const { data } = await api.post("/auth/password/reset", {
        email: resetForm.email.trim(),
        code: resetForm.code.trim(),
        newPassword: resetForm.newPassword,
        confirmPassword: resetForm.confirmPassword,
      })
      setResetMessage(data.message || "密码已重置，请使用新密码登录。")
      setResetForm({ email: "", code: "", newPassword: "", confirmPassword: "" })
    } catch (error) {
      setResetMessage(getApiError(error, "密码重置失败。"))
    } finally {
      setResetSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="AI 外贸业务中台"
      title="登录杰创智能工作区"
      description="统一进入询盘评分、知识库检索、产品图处理和 Agent 执行中心。账号密码适合日常登录，QQ 邮箱验证码适合快速登录和找回密码。"
    >
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-black text-[#0B918C]">账号登录</p>
          <h2 className="mt-2 text-2xl font-black tracking-tight text-[#17211F]">欢迎回来</h2>
        </div>
        <div className="flex h-11 w-11 items-center justify-center rounded-[12px] border border-[#DDE9E4] bg-[#F4FAF7] text-[#0B918C]">
          <ShieldCheck size={21} />
        </div>
      </div>

      <form className="space-y-4" onSubmit={submitLogin}>
        <div className="grid grid-cols-2 gap-2 rounded-[10px] bg-[#EEF5F1] p-1.5">
          <ModeButton active={mode === "password"} onClick={() => setMode("password")}>密码登录</ModeButton>
          <ModeButton active={mode === "code"} onClick={() => setMode("code")}>验证码登录</ModeButton>
        </div>

        {mode === "password" ? (
          <>
            <Field label="账号 / QQ 邮箱" htmlFor="login-account">
              <UserRound className="auth-input-icon" size={16} />
              <Input id="login-account" value={account} onChange={(event) => setAccount(event.target.value)} placeholder="请输入用户名或 QQ 邮箱" className="auth-input pl-10" autoComplete="username" />
            </Field>
            <Field label="密码" htmlFor="login-password">
              <LockKeyhole className="auth-input-icon" size={16} />
              <Input id="login-password" value={password} onChange={(event) => setPassword(event.target.value)} type={showPassword ? "text" : "password"} placeholder="请输入登录密码" className="auth-input pl-10 pr-11" autoComplete="current-password" />
              <PasswordToggle visible={showPassword} onClick={() => setShowPassword((value) => !value)} />
            </Field>
          </>
        ) : (
          <>
            <Field label="QQ 邮箱" htmlFor="login-email">
              <Mail className="auth-input-icon" size={16} />
              <Input id="login-email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="example@qq.com" className="auth-input pl-10" autoComplete="email" />
            </Field>
            <div className="grid grid-cols-[minmax(0,1fr)_128px] gap-2">
              <Input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" className="auth-input" inputMode="numeric" maxLength={6} />
              <Button type="button" variant="outline" className="h-12 rounded-[8px]" onClick={sendLoginCode} disabled={sendingCode || loginCountdown.running}>
                {loginCountdown.running ? `${loginCountdown.seconds}s` : sendingCode ? "发送中" : "获取验证码"}
              </Button>
            </div>
          </>
        )}

        <div className="flex min-h-6 items-center justify-between gap-3">
          <p className={cn("text-sm font-semibold", message.includes("已发送") ? "text-[#1F5F53]" : "text-destructive")}>{message}</p>
          <button type="button" className="shrink-0 text-sm font-black text-[#0B918C] hover:text-[#087C78]" onClick={() => setResetOpen((value) => !value)}>
            忘记密码
          </button>
        </div>

        {resetOpen && (
          <div className="space-y-3 rounded-[12px] border border-[#DCE7E2] bg-[#F8FBFA] p-4">
            <div>
              <h3 className="text-sm font-black text-[#171916]">通过 QQ 邮箱找回密码</h3>
              <p className="mt-1 text-xs font-medium text-[#74766F]">验证码会发送到已绑定账号的 QQ 邮箱。</p>
            </div>
            <Input value={resetForm.email} onChange={(event) => setResetForm((prev) => ({ ...prev, email: event.target.value }))} placeholder="QQ 邮箱" className="auth-input" />
            <div className="grid grid-cols-[minmax(0,1fr)_128px] gap-2">
              <Input value={resetForm.code} onChange={(event) => setResetForm((prev) => ({ ...prev, code: event.target.value }))} placeholder="验证码" className="auth-input" maxLength={6} />
              <Button type="button" variant="outline" className="h-12 rounded-[8px]" onClick={sendResetCode} disabled={resetSending || resetCountdown.running}>
                {resetCountdown.running ? `${resetCountdown.seconds}s` : resetSending ? "发送中" : "发送验证码"}
              </Button>
            </div>
            <Input type="password" value={resetForm.newPassword} onChange={(event) => setResetForm((prev) => ({ ...prev, newPassword: event.target.value }))} placeholder="新密码，至少 8 位" className="auth-input" />
            <Input type="password" value={resetForm.confirmPassword} onChange={(event) => setResetForm((prev) => ({ ...prev, confirmPassword: event.target.value }))} placeholder="确认新密码" className="auth-input" />
            {resetMessage && <p className={cn("text-xs font-semibold", resetMessage.includes("已") ? "text-[#1F5F53]" : "text-destructive")}>{resetMessage}</p>}
            <Button type="button" variant="outline" className="h-11 w-full rounded-[8px]" onClick={resetPassword} disabled={resetSubmitting}>
              {resetSubmitting ? "重置中" : "重置密码"}
            </Button>
          </div>
        )}

        <Button type="submit" size="lg" className="h-12 w-full rounded-[8px] bg-[#17211F] text-white hover:bg-[#22312D]" disabled={submitting}>
          {submitting ? "正在登录" : "进入工作区"}
          <ArrowRight size={17} />
        </Button>

        <div className="text-center text-sm text-muted-foreground">
          还没有账号？
          <Link to="/register" className="ml-1 font-black text-[#0B918C] hover:text-[#087C78]">创建账号</Link>
        </div>
      </form>
    </AuthShell>
  )
}

function ModeButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} className={cn("rounded-[8px] px-3 py-2.5 text-sm font-black transition-colors", active ? "bg-white text-[#17211F] shadow-[0_12px_28px_-24px_rgba(28,74,62,0.72)]" : "text-[#6A766F] hover:bg-white/60")}>
      {children}
    </button>
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
