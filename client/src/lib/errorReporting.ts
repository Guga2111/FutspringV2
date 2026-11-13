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
  return text.replace(/(?:https?|wss?):\/\/[^\s()]+/g, (url) => {
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

function toReport(message: string, stack: string | null, source: ClientErrorSource): ClientErrorReport | null {
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

// Builds the report, or null when it is noise (blank or a known browser warning)
export function buildReport(error: unknown, source: ClientErrorSource): ClientErrorReport | null {
  const { message, stack } = describe(error)
  return toReport(message, stack, source)
}

type Violation = Pick<
  SecurityPolicyViolationEvent,
  "disposition" | "effectiveDirective" | "blockedURI" | "sourceFile" | "lineNumber" | "columnNumber"
>

// Browser extensions inject scripts and styles the policy refuses: nothing of ours to fix
const EXTENSION = /^(?:chrome|moz|safari-web)-extension:/

// Hosts the app never loads from (fonts are self-hosted with @fontsource), but extensions inject into the page itself,
// so the violation names the page, not the extension: Google Fonts stylesheets and their font files
const FOREIGN_HOSTS = new Set(["fonts.googleapis.com", "fonts.gstatic.com"])

function foreignHost(uri: string): boolean {
  try {
    return FOREIGN_HOSTS.has(new URL(uri).hostname)
  } catch {
    return false // "inline", "eval", "data", "blob": not a URL
  }
}

// A Content-Security-Policy violation ("report" while the header is Report-Only, "enforce" after), so the
// violations of every user reach Loki, not only the ones in the developer's console
export function buildViolationReport(event: Violation): ClientErrorReport | null {
  if (EXTENSION.test(event.blockedURI) || EXTENSION.test(event.sourceFile) || foreignHost(event.blockedURI)) return null
  const message = `CSP ${event.disposition} ${event.effectiveDirective}: ${event.blockedURI || "(sem URI)"}`
  const stack = event.sourceFile ? `at ${event.sourceFile}:${event.lineNumber}:${event.columnNumber}` : null
  return toReport(message, stack, "CSP")
}

// Each message + stack once, at most 10 per page load; a failed send is ignored (never reported again, never thrown)
function send(report: ClientErrorReport | null): void {
  if (!report || sent >= MAX_REPORTS_PER_LOAD) return
  const key = `${report.message}\n${report.stack ?? ""}`
  if (seen.has(key)) return
  seen.add(key)
  sent += 1
  sendClientError(report).catch(() => {})
}

// Sends an uncaught error to the API (a log line in Loki)
export function reportError(error: unknown, source: ClientErrorSource): void {
  send(buildReport(error, source))
}

// Called once in main.tsx, before the render; listeners, so no other handler is replaced
export function installErrorReporting(): void {
  window.addEventListener("error", (event) => reportError(event.error ?? event.message, "WINDOW"))
  window.addEventListener("unhandledrejection", (event) => reportError(event.reason, "PROMISE"))
  document.addEventListener("securitypolicyviolation", (event) => send(buildViolationReport(event)))
}

// Tests only: a fresh page load
export function resetErrorReporting(): void {
  seen.clear()
  sent = 0
}
