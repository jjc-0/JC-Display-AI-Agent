import axios from "axios"

const api = axios.create({
  baseURL: "/api",
  timeout: 120000,
  headers: { "Content-Type": "application/json" },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("jc-auth-token")
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const msg = error.response?.data?.message || error.message || "请求失败"
    console.error("[API Error]", msg)
    return Promise.reject(error)
  }
)

export { api }
export default api
