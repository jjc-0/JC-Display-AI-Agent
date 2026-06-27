export type UserRole = "admin" | "user"

export const DEFAULT_USER_ROUTE = "/agent-chat"
export const DEFAULT_ADMIN_ROUTE = "/dashboard"

export function normalizeRole(role?: string | null): UserRole {
  return role === "admin" ? "admin" : "user"
}

export function canAccess(requiredRoles: UserRole[] | undefined, role?: string | null) {
  if (!requiredRoles || requiredRoles.length === 0) return true
  return requiredRoles.includes(normalizeRole(role))
}

export function defaultRouteForRole(role?: string | null) {
  return normalizeRole(role) === "admin" ? DEFAULT_ADMIN_ROUTE : DEFAULT_USER_ROUTE
}
