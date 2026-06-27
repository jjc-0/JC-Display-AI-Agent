import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { Suspense } from "react"
import Layout from "@/components/layout/Layout"
import { protectedRoutes } from "@/config/routes"
import Login from "@/pages/Login"
import Register from "@/pages/Register"

function RequireAuth({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem("jc-auth-token")
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/new-user" element={<Register />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to="/agent-chat" replace />} />
          {protectedRoutes.map((route) => (
            <Route
              key={route.path}
              path={route.path}
              element={<Suspense fallback={<RouteLoading />}>{route.element}</Suspense>}
            />
          ))}
          <Route path="*" element={<Navigate to="/agent-chat" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

function RouteLoading() {
  return (
    <div className="flex min-h-[260px] items-center justify-center">
      <div className="h-10 w-[220px] animate-pulse rounded-[8px] bg-[#F4F6F5]" />
    </div>
  )
}
