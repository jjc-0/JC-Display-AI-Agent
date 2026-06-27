import { ChangeEvent, useEffect, useRef, useState } from "react"
import { Camera, CheckCircle2, KeyRound, Mail, Moon, Save, SunMedium, Upload } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { getApiError, isQqEmail } from "@/features/auth/authUtils"
import { useCountdown } from "@/features/auth/useCountdown"
import { useThemeMode } from "@/hooks/useThemeMode"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

interface ProfileForm {
  id?: number
  username: string
  role: string
  email: string
  qqEmail: string
  avatarUrl: string
  companyName: string
  department: string
  jobTitle: string
  phone: string
  createdAt?: string | null
}

const emptyProfile: ProfileForm = {
  username: "",
  role: "",
  email: "",
  qqEmail: "",
  avatarUrl: "",
  companyName: "",
  department: "",
  jobTitle: "",
  phone: "",
}

export default function Profile() {
  const fileRef = useRef<HTMLInputElement>(null)
  const qqCountdown = useCountdown()
  const { isDark, toggleTheme } = useThemeMode()
  const [profile, setProfile] = useState<ProfileForm>(emptyProfile)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [message, setMessage] = useState("")
  const [usernameStatus, setUsernameStatus] = useState("")
  const [qqForm, setQqForm] = useState({ qqEmail: "", code: "" })
  const [qqSending, setQqSending] = useState(false)
  const [qqBinding, setQqBinding] = useState(false)
  const [qqMessage, setQqMessage] = useState("")
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" })
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordMessage, setPasswordMessage] = useState("")

  useEffect(() => {
    loadProfile()
  }, [])

  useEffect(() => {
    if (!profile.username || profile.username.length < 3) {
      setUsernameStatus("")
      return
    }
    const timer = window.setTimeout(async () => {
      try {
        const { data } = await api.get("/auth/username/check", { params: { username: profile.username } })
        setUsernameStatus(data.available ? "用户名可用" : "用户名已被使用")
      } catch (error) {
        setUsernameStatus(getApiError(error, ""))
      }
    }, 420)
    return () => window.clearTimeout(timer)
  }, [profile.username])

  const loadProfile = async () => {
    setLoading(true)
    try {
      const { data } = await api.get("/auth/me")
      const next = { ...emptyProfile, ...data }
      setProfile(next)
      setQqForm((prev) => ({ ...prev, qqEmail: next.qqEmail || next.email || "" }))
      syncProfileStorage(next)
    } catch (error) {
      setMessage(getApiError(error, "资料读取失败，请重新登录后再试。"))
    } finally {
      setLoading(false)
    }
  }

  const syncProfileStorage = (data: ProfileForm) => {
    localStorage.setItem("jc-display-login-account", data.username || "")
    localStorage.setItem("jc-display-login-role", data.role || "")
    window.dispatchEvent(new Event("jc-auth-profile-updated"))
  }

  const updateField = (key: keyof ProfileForm, value: string) => {
    setProfile((prev) => ({ ...prev, [key]: value }))
  }

  const saveProfile = async () => {
    setSaving(true)
    setMessage("")
    try {
      const payload = {
        username: profile.username,
        email: profile.email,
        companyName: profile.companyName,
        department: profile.department,
        jobTitle: profile.jobTitle,
        phone: profile.phone,
      }
      const { data } = await api.put("/auth/profile", payload)
      if (data.token) localStorage.setItem("jc-auth-token", data.token)
      const next = { ...emptyProfile, ...data }
      setProfile(next)
      syncProfileStorage(next)
      setMessage("资料已保存。")
    } catch (error) {
      setMessage(getApiError(error, "保存失败，请稍后重试。"))
    } finally {
      setSaving(false)
    }
  }

  const uploadAvatar = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    const formData = new FormData()
    formData.append("file", file)
    setAvatarUploading(true)
    setMessage("")
    try {
      const { data } = await api.post("/auth/avatar", formData, { headers: { "Content-Type": "multipart/form-data" } })
      const next = { ...emptyProfile, ...data }
      setProfile(next)
      syncProfileStorage(next)
      setMessage("头像已更新。")
    } catch (error) {
      setMessage(getApiError(error, "头像上传失败。"))
    } finally {
      setAvatarUploading(false)
      if (fileRef.current) fileRef.current.value = ""
    }
  }

  const sendQqCode = async () => {
    setQqMessage("")
    if (!isQqEmail(qqForm.qqEmail)) {
      setQqMessage("请输入有效的 QQ 邮箱。")
      return
    }
    setQqSending(true)
    try {
      const { data } = await api.post("/auth/code/send", { email: qqForm.qqEmail.trim(), purpose: "bind_qq" })
      setQqMessage(data.message || "验证码已发送。")
      qqCountdown.start()
    } catch (error) {
      setQqMessage(getApiError(error, "验证码发送失败。"))
    } finally {
      setQqSending(false)
    }
  }

  const bindQqEmail = async () => {
    setQqMessage("")
    if (!isQqEmail(qqForm.qqEmail) || !qqForm.code.trim()) {
      setQqMessage("请填写 QQ 邮箱和验证码。")
      return
    }
    setQqBinding(true)
    try {
      const { data } = await api.post("/auth/qq-email/bind", { qqEmail: qqForm.qqEmail.trim(), code: qqForm.code.trim() })
      const next = { ...emptyProfile, ...data }
      setProfile(next)
      setQqForm({ qqEmail: next.qqEmail || "", code: "" })
      setQqMessage("QQ 邮箱已绑定。")
    } catch (error) {
      setQqMessage(getApiError(error, "QQ 邮箱绑定失败。"))
    } finally {
      setQqBinding(false)
    }
  }

  const updatePassword = async () => {
    setPasswordMessage("")
    if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
      setPasswordMessage("请完整填写密码信息。")
      return
    }
    if (passwordForm.newPassword.length < 8) {
      setPasswordMessage("新密码至少需要 8 个字符。")
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordMessage("两次输入的新密码不一致。")
      return
    }
    setPasswordSaving(true)
    try {
      const { data } = await api.put("/auth/password", passwordForm)
      setPasswordMessage(data?.message || "密码已更新。")
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" })
    } catch (error) {
      setPasswordMessage(getApiError(error, "修改密码失败。"))
    } finally {
      setPasswordSaving(false)
    }
  }

  const avatarText = String(profile.username || "U").slice(0, 1).toUpperCase()
  const createdText = profile.createdAt
    ? new Date(profile.createdAt).toLocaleDateString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" })
    : "暂无记录"

  return (
    <div className="mx-auto grid max-w-[1420px] grid-cols-1 gap-4 animate-fade-in xl:grid-cols-[360px_minmax(0,1fr)]">
      <aside className="space-y-4">
        <section className="rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-5 shadow-[var(--ui-shadow-panel)]">
          <div className="flex flex-col items-center text-center">
            <AvatarPreview avatarUrl={profile.avatarUrl} avatarText={avatarText} size="large" />
            <h1 className="mt-5 max-w-full truncate text-2xl font-black tracking-tight text-[var(--ui-text)]">
              {profile.username || "个人信息"}
            </h1>
            <p className="mt-2 text-sm font-semibold text-[var(--ui-text-muted)]">账号 ID：{profile.id || "暂无"}</p>
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              <Badge variant={profile.role === "admin" ? "warning" : "secondary"}>{profile.role === "admin" ? "管理员" : "普通用户"}</Badge>
              <Badge variant="success">已启用</Badge>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 gap-3">
            <InfoStat label="账号 ID" value={profile.id ? String(profile.id) : "暂无"} />
            <InfoStat label="注册时间" value={createdText} />
            <InfoStat label="QQ 邮箱" value={profile.qqEmail || "未绑定"} />
          </div>
        </section>

        <section className="rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-5 text-[var(--ui-text)] shadow-[var(--ui-shadow-panel)]">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] border border-[var(--ui-border-accent)] bg-[var(--ui-accent)] text-[var(--ui-accent-strong)]">
              {isDark ? <SunMedium size={18} /> : <Moon size={18} />}
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-base font-black text-[var(--ui-text)]">主题切换</h2>
              <p className="mt-1 text-xs leading-relaxed text-[var(--ui-text-muted)]">
                白色主题使用黑色字体，黑色主题使用白色字体，绿色只作为按钮与状态点缀。
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={toggleTheme}
            className="mt-4 flex h-12 w-full items-center justify-between rounded-[8px] bg-[var(--ui-button-primary-bg)] px-4 text-sm font-black text-[var(--ui-button-primary-fg)] transition-colors hover:bg-[var(--ui-button-primary-hover)] active:translate-y-px"
          >
            <span>{isDark ? "切换白色主题" : "启用黑色主题"}</span>
            {isDark ? <SunMedium size={17} /> : <Moon size={17} />}
          </button>
        </section>

        <section className="rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-5">
          <div className="flex items-center gap-3">
            <div className="profile-icon-cell"><Camera size={18} /></div>
            <div>
              <h2 className="text-base font-black text-[var(--ui-text)]">头像</h2>
              <p className="text-xs font-medium text-[var(--ui-text-muted)]">支持 PNG、JPG、WEBP、GIF，最大 2MB。</p>
            </div>
          </div>
          <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={uploadAvatar} />
          <Button variant="outline" className="mt-4 w-full rounded-[8px]" onClick={() => fileRef.current?.click()} disabled={avatarUploading}>
            <Upload size={14} />
            {avatarUploading ? "上传中" : "上传头像"}
          </Button>
        </section>
      </aside>

      <main className="space-y-4">
        <section className="rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface)] p-5 shadow-[var(--ui-shadow-panel)]">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-[11px] font-black uppercase tracking-[0.08em] text-[var(--ui-text-muted)]">Profile</p>
              <h2 className="mt-1 text-xl font-black text-[var(--ui-text)]">资料与用户名</h2>
              <p className="mt-1 text-sm text-[var(--ui-text-muted)]">系统只保留一个用户名。对话归属绑定账号 ID，改名不会丢失历史记录。</p>
            </div>
            <Button onClick={saveProfile} disabled={saving || loading || usernameStatus === "用户名已被使用"} className="rounded-[8px]">
              <Save size={14} />
              {saving ? "保存中" : "保存资料"}
            </Button>
          </div>

          <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <FormField label="用户名">
              <Input value={profile.username} onChange={(event) => updateField("username", event.target.value)} className="auth-input" placeholder="输入用户名" />
              {usernameStatus && <p className={cn("mt-2 text-xs font-black", usernameStatus === "用户名可用" ? "text-[#1F5F53]" : "text-destructive")}>{usernameStatus}</p>}
            </FormField>
            <FormField label="联系邮箱">
              <Input value={profile.email} onChange={(event) => updateField("email", event.target.value)} className="auth-input" placeholder="业务联系邮箱" />
            </FormField>
            <FormField label="手机号">
              <Input value={profile.phone} onChange={(event) => updateField("phone", event.target.value)} className="auth-input" placeholder="手机号" />
            </FormField>
            <FormField label="公司">
              <Input value={profile.companyName} onChange={(event) => updateField("companyName", event.target.value)} className="auth-input" placeholder="公司名称" />
            </FormField>
            <FormField label="部门">
              <Input value={profile.department} onChange={(event) => updateField("department", event.target.value)} className="auth-input" placeholder="部门" />
            </FormField>
            <FormField label="职位">
              <Input value={profile.jobTitle} onChange={(event) => updateField("jobTitle", event.target.value)} className="auth-input" placeholder="职位" />
            </FormField>
          </div>
          {message && <p className={cn("mt-4 text-sm font-black", message.includes("已") ? "text-[#1F5F53]" : "text-destructive")}>{message}</p>}
        </section>

        <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Card className="overflow-hidden">
            <CardContent className="p-5">
              <PanelTitle icon={<Mail size={18} />} title="QQ 邮箱绑定" text="用于验证码登录、注册校验和找回密码。" />
              <div className="mt-5 space-y-3">
                <BindingStatus active={Boolean(profile.qqEmail)} text={profile.qqEmail || "尚未绑定 QQ 邮箱"} />
                <Input value={qqForm.qqEmail} onChange={(event) => setQqForm((prev) => ({ ...prev, qqEmail: event.target.value }))} placeholder="example@qq.com" className="auth-input" />
                <div className="grid grid-cols-[minmax(0,1fr)_128px] gap-2">
                  <Input value={qqForm.code} onChange={(event) => setQqForm((prev) => ({ ...prev, code: event.target.value }))} placeholder="验证码" className="auth-input" maxLength={6} />
                  <Button variant="outline" className="h-12 rounded-[8px]" onClick={sendQqCode} disabled={qqSending || qqCountdown.running}>
                    {qqCountdown.running ? `${qqCountdown.seconds}s` : qqSending ? "发送中" : "发送验证码"}
                  </Button>
                </div>
                {qqMessage && <p className={cn("text-xs font-black", qqMessage.includes("已") ? "text-[#1F5F53]" : "text-destructive")}>{qqMessage}</p>}
                <Button className="w-full rounded-[8px]" onClick={bindQqEmail} disabled={qqBinding}>
                  {qqBinding ? "绑定中" : "绑定 QQ 邮箱"}
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="overflow-hidden">
            <CardContent className="p-5">
              <PanelTitle icon={<KeyRound size={18} />} title="修改密码" text="修改成功后请使用新密码登录，验证码登录不受影响。" />
              <div className="mt-5 space-y-3">
                <Input type="password" value={passwordForm.currentPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, currentPassword: event.target.value }))} placeholder="当前密码" className="auth-input" />
                <Input type="password" value={passwordForm.newPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, newPassword: event.target.value }))} placeholder="新密码，至少 8 位" className="auth-input" />
                <Input type="password" value={passwordForm.confirmPassword} onChange={(event) => setPasswordForm((prev) => ({ ...prev, confirmPassword: event.target.value }))} placeholder="确认新密码" className="auth-input" />
                {passwordMessage && <p className={cn("text-xs font-black", passwordMessage.includes("已") ? "text-[#1F5F53]" : "text-destructive")}>{passwordMessage}</p>}
                <Button className="w-full rounded-[8px]" onClick={updatePassword} disabled={passwordSaving}>
                  {passwordSaving ? "修改中" : "修改密码"}
                </Button>
              </div>
            </CardContent>
          </Card>
        </section>
      </main>
    </div>
  )
}

function AvatarPreview({ avatarUrl, avatarText, size }: { avatarUrl: string; avatarText: string; size?: "large" }) {
  const className = size === "large"
    ? "flex h-[118px] w-[118px] items-center justify-center overflow-hidden rounded-[22px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] text-3xl font-black text-[var(--ui-text)]"
    : "flex h-24 w-24 items-center justify-center overflow-hidden rounded-[18px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] text-2xl font-black text-[var(--ui-text)]"
  return (
    <div className={className}>
      {avatarUrl ? <img src={avatarUrl} alt="用户头像" className="h-full w-full object-cover" /> : <span>{avatarText}</span>}
    </div>
  )
}

function InfoStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[12px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] p-3">
      <p className="text-[11px] font-black text-[var(--ui-text-muted)]">{label}</p>
      <p className="mt-2 truncate text-sm font-black text-[var(--ui-text)]">{value}</p>
    </div>
  )
}

function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
    </div>
  )
}

function PanelTitle({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="flex items-start gap-3">
      <div className="profile-icon-cell">{icon}</div>
      <div>
        <h2 className="text-base font-black text-[var(--ui-text)]">{title}</h2>
        <p className="mt-1 text-sm leading-relaxed text-[var(--ui-text-muted)]">{text}</p>
      </div>
    </div>
  )
}

function BindingStatus({ active, text }: { active: boolean; text: string }) {
  return (
    <div className="flex items-center gap-3 rounded-[10px] border border-[var(--ui-border)] bg-[var(--ui-surface-subtle)] px-3 py-3">
      <CheckCircle2 size={16} className={active ? "text-[#1F5F53]" : "text-[var(--ui-text-muted)]"} />
      <span className="min-w-0 flex-1 truncate text-sm font-black text-[var(--ui-text)]">{text}</span>
      <Badge variant={active ? "success" : "secondary"}>{active ? "已绑定" : "未绑定"}</Badge>
    </div>
  )
}
