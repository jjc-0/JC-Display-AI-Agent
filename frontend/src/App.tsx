import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { Suspense } from "react"
import Layout from "@/components/layout/Layout"
import { canAccess, defaultRouteForRole } from "@/config/access"
import { protectedRoutes } from "@/config/routes"
import Login from "@/pages/Login"
import Register from "@/pages/Register"

function RequireAuth({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem("jc-auth-token")
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

function RequireRole({ roles, children }: { roles?: Array<"admin" | "user">; children: React.ReactNode }) {
  const role = localStorage.getItem("jc-display-login-role")
  return canAccess(roles, role) ? <>{children}</> : <Navigate to={defaultRouteForRole(role)} replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/new-user" element={<Register />} />
        <Route path="/" element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to={defaultRouteForRole(localStorage.getItem("jc-display-login-role"))} replace />} />
          {protectedRoutes.map((route) => (
            <Route
              key={route.path}
              path={route.path}
              element={
                <RequireRole roles={route.roles}>
                  <Suspense fallback={<RouteLoading />}>{route.element}</Suspense>
                </RequireRole>
              }
            />
          ))}
          <Route path="*" element={<Navigate to={defaultRouteForRole(localStorage.getItem("jc-display-login-role"))} replace />} />
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
