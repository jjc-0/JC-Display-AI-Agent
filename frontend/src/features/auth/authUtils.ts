export function getApiError(error: unknown, fallback: string) {
  return (error as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
}

export function isQqEmail(value: string) {
  return /^[a-zA-Z0-9._%+-]+@qq\.com$/i.test(value.trim())
}

export function saveAuthSession(data: { token: string; username: string; role: string }) {
  localStorage.setItem("jc-auth-token", data.token)
  localStorage.setItem("jc-display-login-role", data.role)
  localStorage.setItem("jc-display-login-account", data.username)
  window.dispatchEvent(new Event("jc-auth-profile-updated"))
}

export function nextAuthRoute(role: string) {
  return role === "admin" ? "/dashboard" : "/agent-chat"
}
