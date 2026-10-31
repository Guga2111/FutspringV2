// Keep the full local destination across login, registration, tab switches and expired sessions.
// Never let a caller-controlled returnTo become an external redirect.
export function authReturnTo(query: string | null, state: unknown): string {
  const from = (state as { from?: { pathname?: string; search?: string; hash?: string } } | null)?.from
  const destination = query ?? (from?.pathname ? from.pathname + (from.search ?? "") + (from.hash ?? "") : "/home")
  return destination.startsWith("/") && !destination.startsWith("//") && !destination.includes("\\") && !/[\r\n]/.test(destination) ? destination : "/home"
}
