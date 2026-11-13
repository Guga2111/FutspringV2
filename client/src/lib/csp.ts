import { createHash } from "node:crypto"
import type { Plugin } from "vite"

// Build-time only (vite.config.ts and csp.test.ts): the app never imports this module.
//
// The app's Content-Security-Policy is a <meta http-equiv> written into dist/index.html by the build, not an nginx
// header: the hash of the inline theme script is computed from the very file it ships in (no manual step), and any
// change to the policy changes index.html, so installed PWAs (whose service worker serves the precached index.html
// with the headers it was cached with) pick it up through the update prompt. A meta tag can't carry frame-ancestors:
// that one stays an nginx header (ops/nginx/futspring.conf).

// mirrors the fallback of src/lib/config.ts
const DEFAULT_API_URL = "http://localhost:8080"

export function inlineScriptHashes(html: string): string[] {
  return [...html.matchAll(/<script(\s[^>]*)?>([\s\S]*?)<\/script>/g)]
    .filter(([, attrs = ""]) => !/\ssrc=/.test(attrs))
    .map(([, , body]) => `'sha256-${createHash("sha256").update(body, "utf8").digest("base64")}'`)
}

// connect-src: the API's origin (another origin in dev builds and the tunnels; the page's own in production) and,
// for an https API, its wss: host, because older Safari doesn't match WebSockets with 'self'.
// style-src 'unsafe-inline': Radix, recharts and sonner write style attributes.
export function contentSecurityPolicy(apiUrl: string, scriptHashes: string[]): string {
  const api = new URL(apiUrl)
  const connect = ["'self'", api.origin, ...(api.protocol === "https:" ? [`wss://${api.host}`] : [])]
  return [
    "default-src 'self'",
    `script-src 'self' ${scriptHashes.join(" ")}`.trim(),
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    `connect-src ${[...new Set(connect)].join(" ")}`,
    "worker-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; ")
}

// Writes the policy right after <meta charset>, before the theme script (a meta policy only covers what follows it)
export function contentSecurityPolicyMeta(): Plugin {
  let apiUrl = DEFAULT_API_URL
  return {
    name: "csp-meta",
    apply: "build",
    enforce: "post",
    configResolved(config) {
      apiUrl = config.env.VITE_API_URL || DEFAULT_API_URL
    },
    transformIndexHtml: {
      order: "post",
      handler(html) {
        const charset = /<meta charset="[^"]*"\s*\/?>/i
        if (!charset.test(html)) throw new Error("csp-meta: index.html has no <meta charset>")
        const policy = contentSecurityPolicy(apiUrl, inlineScriptHashes(html))
        return html.replace(charset, (tag) => `${tag}\n    <meta http-equiv="Content-Security-Policy" content="${policy}" />`)
      },
    },
  }
}
