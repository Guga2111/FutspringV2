import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("@/api/clientErrors", () => ({ sendClientError: vi.fn() }))

import { sendClientError } from "@/api/clientErrors"
import { redactErrorText, buildReport, buildViolationReport, reportError, resetErrorReporting, stripUrlQueries } from "./errorReporting"

const send = vi.mocked(sendClientError)

function errorWith(message: string, stack: string) {
  const error = new Error(message)
  error.stack = stack
  return error
}

beforeEach(() => {
  resetErrorReporting()
  send.mockReset()
  send.mockResolvedValue(undefined)
  vi.stubGlobal("window", { location: { pathname: "/reset-password", search: "?token=secret", hash: "#x" } })
  vi.stubGlobal("navigator", { userAgent: "UA" })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("stripUrlQueries", () => {
  it("drops query and hash but keeps the frame's line and column", () => {
    expect(stripUrlQueries("at f (https://app.test/assets/a.js?v=1#h:10:5)")).toBe("at f (https://app.test/assets/a.js:10:5)")
    expect(stripUrlQueries("GET https://app.test/reset-password?token=secret failed")).toBe(
      "GET https://app.test/reset-password failed",
    )
    expect(stripUrlQueries("at https://app.test:8080/a.js:1:2")).toBe("at https://app.test:8080/a.js:1:2")
    expect(stripUrlQueries("wss://app.test/ws/1/abc/websocket?t=1")).toBe("wss://app.test/ws/1/abc/websocket")
  })
})

describe("buildReport", () => {
  it("sends the pathname only and strips URLs inside the stack", () => {
    const report = buildReport(
      errorWith("boom", "Error: boom\n    at https://app.test/auth?returnTo=%2Fhome:3:4"),
      "BOUNDARY",
    )

    expect(report).toEqual({
      message: "boom",
      stack: "Error: boom\n    at https://app.test/auth:3:4",
      path: "/reset-password",
      release: "test",
      userAgent: "UA",
      source: "BOUNDARY",
    })
  })

  it("truncates to the API's limits", () => {
    const report = buildReport(errorWith("m".repeat(600), "s".repeat(5000)), "WINDOW")

    expect(report?.message).toHaveLength(500)
    expect(report?.stack).toHaveLength(4000)
  })

  it("describes values that aren't errors", () => {
    expect(buildReport("texto", "PROMISE")?.message).toBe("texto")
    expect(buildReport({ code: 1 }, "PROMISE")?.message).toBe('{"code":1}')
    expect(buildReport(undefined, "PROMISE")?.message).toBe("undefined")
  })

  it("ignores browser noise", () => {
    expect(buildReport("ResizeObserver loop completed with undelivered notifications.", "WINDOW")).toBeNull()
    expect(buildReport("Script error.", "WINDOW")).toBeNull()
    expect(buildReport("  ", "WINDOW")).toBeNull()
  })
})

describe("reportError", () => {
  it("sends each message + stack once", () => {
    reportError(errorWith("boom", "stack 1"), "WINDOW")
    reportError(errorWith("boom", "stack 1"), "PROMISE")
    reportError(errorWith("boom", "stack 2"), "WINDOW")

    expect(send).toHaveBeenCalledTimes(2)
  })

  it("sends at most 10 per page load", () => {
    for (let i = 0; i < 15; i++) reportError(new Error(`erro ${i}`), "WINDOW")

    expect(send).toHaveBeenCalledTimes(10)
  })

  it("ignores a failed send", async () => {
    send.mockRejectedValue(new Error("Network Error"))

    expect(() => reportError(new Error("boom"), "WINDOW")).not.toThrow()
    await Promise.resolve()
  })
})

describe("buildViolationReport", () => {
  const violation = {
    disposition: "report" as const,
    effectiveDirective: "script-src-elem",
    blockedURI: "inline",
    sourceFile: "https://app.test/home?x=1",
    lineNumber: 12,
    columnNumber: 3,
  }

  it("names the directive and the blocked source, with where it happened", () => {
    expect(buildViolationReport(violation)).toMatchObject({
      message: "CSP report script-src-elem: inline",
      stack: "at https://app.test/home:12:3",
      path: "/reset-password",
      source: "CSP",
    })
  })

  it("strips the query of a blocked URL", () => {
    const report = buildViolationReport({ ...violation, blockedURI: "https://cdn.test/a.js?token=secret", sourceFile: "" })
    expect(report?.message).toBe("CSP report script-src-elem: https://cdn.test/a.js")
    expect(report?.stack).toBeNull()
  })

  it("ignores browser extensions", () => {
    expect(buildViolationReport({ ...violation, blockedURI: "chrome-extension://abc/x.js" })).toBeNull()
    expect(buildViolationReport({ ...violation, sourceFile: "moz-extension://abc/content.js" })).toBeNull()
  })

  it("ignores Google Fonts, which only extensions load (the app self-hosts its fonts)", () => {
    const font = { ...violation, effectiveDirective: "font-src", sourceFile: "https://app.test/home" }
    expect(buildViolationReport({ ...font, blockedURI: "https://fonts.gstatic.com/s/inter/v20/a.woff2" })).toBeNull()
    expect(buildViolationReport({ ...font, effectiveDirective: "style-src-elem", blockedURI: "https://fonts.googleapis.com/css2?family=Inter" })).toBeNull()
    // any other host is still reported
    expect(buildViolationReport({ ...font, blockedURI: "https://cdn.example.com/a.woff2" })?.source).toBe("CSP")
  })
})

describe("credential redaction", () => {
  it("redacts invite paths, bearer values, JSON tokens and URL queries", () => {
    for (const value of ["/join/secret-value", "/api/v1/invite-links/secret-value/join", "Bearer secret-value", '{"token":"secret-value"}', "https://app/verify-email?token=secret-value"]) {
      expect(redactErrorText(value)).not.toContain("secret-value")
    }
  })
})
