import { createHash } from "node:crypto"
import { readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"

// The Content-Security-Policy of the app lives in the nginx site (ops/nginx/futspring.conf) and allows the inline
// theme script of index.html by its hash. Vite copies that script to dist/index.html unchanged, so its hash is
// computed from the source here: editing the script without updating nginx would block it in production.
const CLIENT = join(__dirname, "..", "..")
const indexHtml = readFileSync(join(CLIENT, "index.html"), "utf8")
const nginx = readFileSync(join(CLIENT, "..", "ops", "nginx", "futspring.conf"), "utf8")

function inlineScriptHashes(html: string): string[] {
  return [...html.matchAll(/<script(\s[^>]*)?>([\s\S]*?)<\/script>/g)]
    .filter(([, attrs = ""]) => !/\ssrc=/.test(attrs))
    .map(([, , body]) => `'sha256-${createHash("sha256").update(body, "utf8").digest("base64")}'`)
}

function cspHeaders(conf: string): string[] {
  return [...conf.matchAll(/add_header\s+Content-Security-Policy(?:-Report-Only)?\s+"([^"]+)"/g)].map(([, policy]) => policy)
}

function directive(policy: string, name: string): string[] {
  const found = policy
    .split(";")
    .map((part) => part.trim().split(/\s+/))
    .find(([key]) => key === name)
  return found ? found.slice(1) : []
}

describe("Content-Security-Policy", () => {
  it("index.html has exactly one inline script (the theme)", () => {
    expect(inlineScriptHashes(indexHtml)).toHaveLength(1)
  })

  it("nginx allows the inline scripts of index.html by their current hash, and no other", () => {
    const policies = cspHeaders(nginx)
    expect(policies).toHaveLength(1)
    const allowedHashes = directive(policies[0], "script-src").filter((source) => source.startsWith("'sha256-"))
    expect(allowedHashes.sort()).toEqual(inlineScriptHashes(indexHtml).sort())
  })

  it("keeps scripts to the origin and the page out of frames", () => {
    const [policy] = cspHeaders(nginx)
    expect(directive(policy, "default-src")).toEqual(["'self'"])
    expect(directive(policy, "script-src")).not.toContain("'unsafe-inline'")
    expect(directive(policy, "script-src")).not.toContain("'unsafe-eval'")
    expect(directive(policy, "frame-ancestors")).toEqual(["'none'"])
    expect(directive(policy, "object-src")).toEqual(["'none'"])
  })
})
