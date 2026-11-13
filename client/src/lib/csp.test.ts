import { createHash } from "node:crypto"
import { existsSync, readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"

// The Content-Security-Policy of the app lives in the nginx site (ops/nginx/futspring.conf) and allows the inline
// theme script of index.html by its hash: editing the script without updating nginx would block it in production.
// The source is checked always; the build (dist/, what is deployed) too when it exists, and CI builds before testing,
// so a script Vite or a plugin adds or rewrites at build time is caught as well.
const CLIENT = join(__dirname, "..", "..")
const DIST = join(CLIENT, "dist")
const indexHtml = readFileSync(join(CLIENT, "index.html"), "utf8")
const built = existsSync(join(DIST, "index.html"))
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

  it.runIf(built || process.env.CI)("the built index.html has the same inline scripts", () => {
    const distHtml = readFileSync(join(DIST, "index.html"), "utf8")
    expect(inlineScriptHashes(distHtml).sort()).toEqual(inlineScriptHashes(indexHtml).sort())
  })

  it.runIf(built || process.env.CI)("the built CSS loads no font from a data: URI (font-src is 'self')", () => {
    const css = readdirSync(join(DIST, "assets"))
      .filter((file) => file.endsWith(".css"))
      .map((file) => readFileSync(join(DIST, "assets", file), "utf8"))
    expect(css.length).toBeGreaterThan(0)
    for (const sheet of css) expect(sheet).not.toMatch(/url\(\s*["']?data:(?:font|application\/(?:x-)?font)/)
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
