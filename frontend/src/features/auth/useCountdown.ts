import { useEffect, useState } from "react"

export function useCountdown() {
  const [seconds, setSeconds] = useState(0)

  useEffect(() => {
    if (seconds <= 0) return
    const timer = window.setTimeout(() => setSeconds((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [seconds])

  return {
    seconds,
    running: seconds > 0,
    start: (nextSeconds = 60) => setSeconds(nextSeconds),
  }
}
