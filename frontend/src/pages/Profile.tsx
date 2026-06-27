import { ChangeEvent, useEffect, useRef, useState } from "react"
import { Mail, Save, Upload, UserRound } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import api from "@/lib/api"

interface ProfileForm {
  id?: number
  username: string
  role: string
  displayName: string
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
  displayName: "",
  email: "",
  qqEmail: "",
  avatarUrl: "",
  companyName: "",
  department: "",
  jobTitle: "",
  phone: "",
}

export default function Profile() {
  const [profile, setProfile] = useState<ProfileForm>(emptyProfile)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")
  const [usernameStatus, setUsernameStatus] = useState("")
  const [avatarUploading, setAvatarUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const [qqForm, setQqForm] = useState({ qqEmail: "", code: "" })
  const [qqSending, setQqSending] = useState(false)
  const [qqBinding, setQqBinding] = useState(false)
  const [qqMessage, setQqMessage] = useState("")
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  })
  const [passwordMessage, setPasswordMessage] = useState("")
  const [passwordSaving, setPasswordSaving] = useState(false)

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
      } catch (e: any) {
        setUsernameStatus(e.response?.data?.message || "")
      }
    }, 450)
    return () => window.clearTimeout(timer)
  }, [profile.username])

  const loadProfile = async () => {
    setLoading(true)
    try {
      const { data } = await api.get("/auth/me")
      setProfile({ ...emptyProfile, ...data })
      setQqForm((prev) => ({ ...prev, qqEmail: data.qqEmail || "" }))
      syncProfileStorage(data)
    } catch {
      setMessage("资料读取失败，请重新登录后再试。")
    } finally {
      setLoading(false)
    }
  }

  const syncProfileStorage = (data: ProfileForm) => {
    localStorage.setItem("jc-display-login-account", data.displayName || data.username || "")
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
      const { data } = await api.put("/auth/profile", profile)
      if (data.token) localStorage.setItem("jc-auth-token", data.token)
      setProfile({ ...emptyProfile, ...data })
      syncProfileStorage(data)
      setMessage("资料已保存。")
    } catch (e: any) {
      setMessage(e.response?.data?.message || "保存失败，请稍后重试。")
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
      const { data } = await api.post("/auth/avatar", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      setProfile({ ...emptyProfile, ...data })
      syncProfileStorage(data)
      setMessage("头像已更新。")
    } catch (e: any) {
      setMessage(e.response?.data?.message || "头像上传失败。")
    } finally {
      setAvatarUploading(false)
      if (fileRef.current) fileRef.current.value = ""
    }
  }

  const sendQqCode = async () => {
    setQqSending(true)
    setQqMessage("")
    try {
      const { data } = await api.post("/auth/code/send", {
        email: qqForm.qqEmail,
        purpose: "bind_qq",
      })
      setQqMessage(data.message || "验证码已发送。")
    } catch (e: any) {
      setQqMessage(e.response?.data?.message || "验证码发送失败。")
    } finally {
      setQqSending(false)
    }
  }

  const bindQqEmail = async () => {
    setQqBinding(true)
    setQqMessage("")
    try {
      const { data } = await api.post("/auth/qq-email/bind", qqForm)
      setProfile({ ...emptyProfile, ...data })
      setQqForm((prev) => ({ ...prev, code: "" }))
      setQqMessage("QQ 邮箱已绑定。")
    } catch (e: any) {
      setQqMessage(e.response?.data?.message || "QQ 邮箱绑定失败。")
    } finally {
      setQqBinding(false)
    }
  }

  const updatePassword = async () => {
    setPasswordSaving(true)
    setPasswordMessage("")
    try {
      const { data } = await api.put("/auth/password", passwordForm)
      setPasswordMessage(data?.message || "密码已更新。")
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" })
    } catch (e: any) {
      setPasswordMessage(e.response?.data?.message || "修改密码失败。")
    } finally {
      setPasswordSaving(false)
    }
  }

  const avatarText = String(profile.username || "U").slice(0, 1).toUpperCase()
  const createdText = profile.createdAt ? `${new Date(profile.createdAt).getFullYear()}年${new Date(profile.createdAt).getMonth() + 1}月` : "—"

  return (
    <div className="mx-auto max-w-[1420px] space-y-6 animate-fade-in">
      <section className="rounded-[18px] border border-[#E4E8E5] bg-white p-6 shadow-[0_16px_40px_-34px_rgba(15,23,42,0.22)]">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-center">
          <AvatarPreview avatarUrl={profile.avatarUrl} avatarText={avatarText} size="large" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="truncate text-3xl font-black tracking-tight text-[#171916]">
                {profile.displayName || profile.username || "个人信息"}
              </h1>
              <Badge variant="secondary" className="text-[11px]">
                {profile.role === "admin" ? "管理员" : "用户"}
              </Badge>
              <Badge variant="success" className="text-[11px]">启用</Badge>
            </div>
            <p className="mt-4 text-sm font-medium text-[#171916]">{profile.username || "—"}</p>
            <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
              <InfoStat label="账号 ID" value={profile.id ? String(profile.id) : "—"} />
              <InfoStat label="账号类型" value={profile.role === "admin" ? "管理员" : "普通用户"} />
              <InfoStat label="注册时间" value={createdText} />
            </div>
          </div>
        </div>
      </section>

      <section className="rounded-[18px] border border-[#E4E8E5] bg-white p-6">
        <h2 className="text-xl font-black text-[#171916]">资料与头像</h2>
        <p className="mt-2 text-sm text-[#74766F]">维护账号基础资料，用户名保存前会自动检查是否重复。</p>
        <div className="mt-8 grid grid-cols-1 gap-8 lg:grid-cols-2">
          <Card>
            <CardContent className="space-y-5 p-6">
              <AvatarPreview avatarUrl={profile.avatarUrl} avatarText={avatarText} />
              <div>
                <h3 className="text-base font-black text-[#171916]">资料头像</h3>
                <p className="mt-2 text-sm leading-relaxed text-[#343A35]">
                  支持 PNG、JPG、WEBP、GIF，大小不超过 2MB。上传后会同步到右上角账户面板。
                </p>
              </div>
              <input ref={fileRef} type="file" accept="image/*" className="hidden" onChange={uploadAvatar} />
              <Button variant="outline" onClick={() => fileRef.current?.click()} disabled={avatarUploading}>
                <Upload size={14} />
                {avatarUploading ? "上传中" : "上传头像"}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-5 p-6">
              <h3 className="text-base font-black text-[#171916]">编辑个人资料</h3>
              <div className="space-y-2">
                <Label>用户名</Label>
                <Input value={profile.username} onChange={(e) => updateField("username", e.target.value)} placeholder="输入用户名" />
                {usernameStatus && (
                  <p className={`text-xs font-semibold ${usernameStatus.includes("可用") ? "text-[#1F5F53]" : "text-red-600"}`}>
                    {usernameStatus}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label>显示名称</Label>
                <Input value={profile.displayName} onChange={(e) => updateField("displayName", e.target.value)} placeholder="输入显示名称" />
              </div>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label>邮箱</Label>
                  <Input value={profile.email} onChange={(e) => updateField("email", e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>手机</Label>
                  <Input value={profile.phone} onChange={(e) => updateField("phone", e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>公司</Label>
                  <Input value={profile.companyName} onChange={(e) => updateField("companyName", e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label>职位</Label>
                  <Input value={profile.jobTitle} onChange={(e) => updateField("jobTitle", e.target.value)} />
                </div>
              </div>
              {message && <p className="text-xs font-semibold text-[#1F5F53]">{message}</p>}
              <div className="flex justify-end">
                <Button onClick={saveProfile} disabled={saving || loading || usernameStatus.includes("已被使用")}>
                  <Save size={14} />
                  {saving ? "更新中" : "更新资料"}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </section>

      <section className="rounded-[18px] border border-[#E4E8E5] bg-white p-6">
        <h2 className="text-base font-black text-[#171916]">QQ 邮箱绑定</h2>
        <p className="mt-2 text-sm text-[#74766F]">绑定后可用于验证码登录和忘记密码恢复。</p>
        <div className="mt-6 space-y-4">
          <BindingItem icon={<Mail size={20} />} name="QQ 邮箱" status={profile.qqEmail ? "已绑定" : "未绑定"} value={profile.qqEmail || "请输入 QQ 邮箱并完成验证码绑定"} active={Boolean(profile.qqEmail)} />
          <div className="grid grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_160px_160px]">
            <Input value={qqForm.qqEmail} onChange={(e) => setQqForm((prev) => ({ ...prev, qqEmail: e.target.value }))} placeholder="例如：123456@qq.com" />
            <Button variant="outline" onClick={sendQqCode} disabled={qqSending || !qqForm.qqEmail.trim()}>
              {qqSending ? "发送中" : "发送验证码"}
            </Button>
            <Input value={qqForm.code} onChange={(e) => setQqForm((prev) => ({ ...prev, code: e.target.value }))} placeholder="6 位验证码" />
          </div>
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs font-semibold text-[#1F5F53]">{qqMessage}</p>
            <Button onClick={bindQqEmail} disabled={qqBinding || !qqForm.qqEmail.trim() || !qqForm.code.trim()}>
              {qqBinding ? "绑定中" : "绑定 QQ 邮箱"}
            </Button>
          </div>
        </div>
      </section>

      <section className="rounded-[18px] border border-[#E4E8E5] bg-white">
        <div className="border-b border-[#E4E8E5] p-6">
          <h2 className="text-xl font-medium text-[#171916]">修改密码</h2>
        </div>
        <div className="space-y-6 p-6">
          <PasswordField label="当前密码" value={passwordForm.currentPassword} onChange={(value) => setPasswordForm((prev) => ({ ...prev, currentPassword: value }))} />
          <PasswordField label="新密码" value={passwordForm.newPassword} onChange={(value) => setPasswordForm((prev) => ({ ...prev, newPassword: value }))} helper="密码至少需要 8 个字符" />
          <PasswordField label="确认新密码" value={passwordForm.confirmPassword} onChange={(value) => setPasswordForm((prev) => ({ ...prev, confirmPassword: value }))} />
          {passwordMessage && <p className="text-xs font-semibold text-[#1F5F53]">{passwordMessage}</p>}
          <div className="flex justify-end">
            <Button onClick={updatePassword} disabled={passwordSaving}>
              {passwordSaving ? "修改中" : "修改密码"}
            </Button>
          </div>
        </div>
      </section>
    </div>
  )
}

function AvatarPreview({ avatarUrl, avatarText, size }: { avatarUrl: string; avatarText: string; size?: "large" }) {
  const className = size === "large"
    ? "flex h-[118px] w-[118px] items-center justify-center overflow-hidden rounded-[18px] border border-[#E4E8E5] bg-[#F8FBFA] text-3xl font-black"
    : "flex h-24 w-24 items-center justify-center overflow-hidden rounded-[18px] border border-[#E4E8E5] bg-[#F8FBFA] text-2xl font-black"
  return (
    <div className={className}>
      {avatarUrl ? <img src={avatarUrl} alt="用户头像" className="h-full w-full object-cover" /> : <span>{avatarText}</span>}
    </div>
  )
}

function InfoStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[12px] border border-[#E4E8E5] bg-white p-5">
      <p className="text-sm font-medium text-[#171916]">{label}</p>
      <p className="mt-3 text-2xl font-black text-[#171916]">{value}</p>
    </div>
  )
}

function BindingItem({
  icon,
  name,
  status,
  value,
  active,
}: {
  icon: React.ReactNode
  name: string
  status: string
  value?: string
  active?: boolean
}) {
  return (
    <div className="flex items-center gap-5 rounded-[12px] border border-[#E4E8E5] bg-white p-5">
      <div className="flex h-16 w-16 items-center justify-center rounded-[12px] border border-[#E4E8E5] bg-white font-black">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-3">
          <span className="text-lg font-black text-[#171916]">{name}</span>
          <Badge variant={active ? "success" : "secondary"}>{status}</Badge>
        </div>
        {value && <p className="mt-3 text-sm text-[#171916]">{value}</p>}
      </div>
    </div>
  )
}

function PasswordField({
  label,
  value,
  onChange,
  helper,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  helper?: string
}) {
  return (
    <div className="space-y-3">
      <Label className="text-base font-black text-[#343A35]">{label}</Label>
      <Input type="password" value={value} onChange={(event) => onChange(event.target.value)} className="h-16 text-base" />
      {helper && <p className="text-sm text-[#343A35]">{helper}</p>}
    </div>
  )
}
