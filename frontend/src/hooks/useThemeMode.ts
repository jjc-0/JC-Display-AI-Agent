import { useEffect, useState } from "react"

export type ThemeMode = "light" | "dark"

const THEME_KEY = "jc-display-theme"
const THEME_EVENT = "jc-theme-updated"

function readTheme(): ThemeMode {
  if (typeof window === "undefined") return "light"
  return localStorage.getItem(THEME_KEY) === "dark" ? "dark" : "light"
}

function applyTheme(theme: ThemeMode) {
  if (typeof document === "undefined") return
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

export function useThemeMode() {
  const [theme, setTheme] = useState<ThemeMode>(readTheme)

  useEffect(() => {
    applyTheme(theme)
    localStorage.setItem(THEME_KEY, theme)
  }, [theme])

  useEffect(() => {
    const syncTheme = () => setTheme(readTheme())
    window.addEventListener(THEME_EVENT, syncTheme)
    return () => window.removeEventListener(THEME_EVENT, syncTheme)
  }, [])

  const setNextTheme = (nextTheme: ThemeMode) => {
    localStorage.setItem(THEME_KEY, nextTheme)
    applyTheme(nextTheme)
    setTheme(nextTheme)
    window.dispatchEvent(new Event(THEME_EVENT))
  }

  return {
    theme,
    isDark: theme === "dark",
    toggleTheme: () => setNextTheme(theme === "dark" ? "light" : "dark"),
    setTheme: setNextTheme,
  }
}
