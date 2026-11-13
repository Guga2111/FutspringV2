import { existsSync, readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { contentSecurityPolicy, inlineScriptHashes } from "@/lib/csp"

// The policy is written into dist/index.html by the build (src/lib/csp.ts); nginx only adds frame-ancestors.
// The built file is checked when dist/ exists, and CI builds before testing.
const CLIENT = join(__dirname, "..", "..")
const DIST = join(CLIENT, "dist")
const indexHtml = readFileSync(join(CLIENT, "index.html"), "utf8")
const nginx = readFileSync(join(CLIENT, "..", "ops", "nginx", "futspring.conf"), "utf8")
const built = existsSync(join(DIST, "index.html"))

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

  it("allows the given scripts by hash, never unsafe-inline or eval", () => {
    const policy = contentSecurityPolicy("https://futspring.luisgosampaio.com/api", ["'sha256-abc='"])
    expect(directive(policy, "default-src")).toEqual(["'self'"])
    expect(directive(policy, "script-src")).toEqual(["'self'", "'sha256-abc='"])
    expect(directive(policy, "object-src")).toEqual(["'none'"])
    expect(policy).not.toContain("unsafe-eval")
    // a meta policy ignores frame-ancestors (and the browser warns): it is nginx's
    expect(directive(policy, "frame-ancestors")).toEqual([])
  })

  it("connects to the API's origin, and its wss: host when it is https", () => {
    expect(directive(contentSecurityPolicy("https://futspring.luisgosampaio.com/api", []), "connect-src")).toEqual([
      "'self'",
      "https://futspring.luisgosampaio.com",
      "wss://futspring.luisgosampaio.com",
    ])
    expect(directive(contentSecurityPolicy("http://localhost:8080", []), "connect-src")).toEqual([
      "'self'",
      "http://localhost:8080",
    ])
  })

  it("nginx only adds frame-ancestors, with no policy of its own to keep in sync", () => {
    const headers = [...nginx.matchAll(/add_header\s+(Content-Security-Policy\S*)\s+"([^"]+)"/g)]
    expect(headers.map(([, name, value]) => [name, value])).toEqual([["Content-Security-Policy", "frame-ancestors 'none'"]])
  })

  it.runIf(built || process.env.CI)("the built index.html carries the policy before every inline script", () => {
    const distHtml = readFileSync(join(DIST, "index.html"), "utf8")
    const meta = distHtml.match(/<meta http-equiv="Content-Security-Policy" content="([^"]+)"/)
    expect(meta).not.toBeNull()
    const policy = meta?.[1] ?? ""
    const hashes = directive(policy, "script-src").filter((source) => source.startsWith("'sha256-"))
    expect(hashes.sort()).toEqual(inlineScriptHashes(distHtml).sort())
    expect(distHtml.indexOf("Content-Security-Policy")).toBeLessThan(distHtml.search(/<script(?![^>]*\ssrc=)/))
  })

  it.runIf(built || process.env.CI)("the built CSS loads no font from a data: URI (font-src is 'self')", () => {
    const css = readdirSync(join(DIST, "assets"))
      .filter((file) => file.endsWith(".css"))
      .map((file) => readFileSync(join(DIST, "assets", file), "utf8"))
    expect(css.length).toBeGreaterThan(0)
    for (const sheet of css) expect(sheet).not.toMatch(/url\(\s*["']?data:(?:font|application\/(?:x-)?font)/)
  })
})
