import { sendClientError, type ClientErrorReport, type ClientErrorSource } from "@/api/clientErrors"

// The API's limits (ClientErrorRequestDTO)
const LIMITS = { message: 500, stack: 4000, path: 200, release: 40, userAgent: 300 }
const MAX_REPORTS_PER_LOAD = 10

// Browser noise with nothing to fix: the ResizeObserver warning Chrome fires as an error, and errors of
// cross-origin scripts (extensions), which reach us as "Script error." with no stack
const IGNORED = [/^ResizeObserver loop/, /^Script error\.?$/]

const seen = new Set<string>()
let sent = 0

// Drops the query and the hash of every URL in the text (a reset link carries its token in the query), keeping a
// stack frame's ":line:column"
export function stripUrlQueries(text: string): string {
  return text.replace(/https?:\/\/[^\s()]+/g, (url) => {
    const position = url.match(/(?::\d+){1,2}$/)?.[0] ?? ""
    const address = url.slice(0, url.length - position.length)
    const cut = address.search(/[?#]/)
    return cut < 0 ? url : address.slice(0, cut) + position
  })
}

function describe(error: unknown): { message: string; stack: string | null } {
  if (error instanceof Error) return { message: error.message || error.name, stack: error.stack ?? null }
  if (typeof error === "string") return { message: error, stack: null }
  try {
    return { message: JSON.stringify(error) ?? String(error), stack: null }
  } catch {
    return { message: String(error), stack: null }
  }
}

// Builds the report, or null when it is noise (blank or a known browser warning)
export function buildReport(error: unknown, source: ClientErrorSource): ClientErrorReport | null {
  const { message, stack } = describe(error)
  const trimmed = message.trim()
  if (!trimmed || (!stack && IGNORED.some((pattern) => pattern.test(trimmed)))) return null
  return {
    message: stripUrlQueries(trimmed).slice(0, LIMITS.message),
    stack: stack ? stripUrlQueries(stack).slice(0, LIMITS.stack) : null,
    // pathname only: never the query or the hash
    path: window.location.pathname.slice(0, LIMITS.path),
    release: __APP_VERSION__.slice(0, LIMITS.release),
    userAgent: navigator.userAgent.slice(0, LIMITS.userAgent),
    source,
  }
}

// Sends an uncaught error to the API (a log line in Loki). Each message + stack once, at most 10 per page load;
// a failed send is ignored (never reported again, never thrown)
export function reportError(error: unknown, source: ClientErrorSource): void {
  if (sent >= MAX_REPORTS_PER_LOAD) return
  const report = buildReport(error, source)
  if (!report) return
  const key = `${report.message}\n${report.stack ?? ""}`
  if (seen.has(key)) return
  seen.add(key)
  sent += 1
  sendClientError(report).catch(() => {})
}

// Called once in main.tsx, before the render; listeners, so no other handler is replaced
export function installErrorReporting(): void {
  window.addEventListener("error", (event) => reportError(event.error ?? event.message, "WINDOW"))
  window.addEventListener("unhandledrejection", (event) => reportError(event.reason, "PROMISE"))
}

// Tests only: a fresh page load
export function resetErrorReporting(): void {
  seen.clear()
  sent = 0
}
