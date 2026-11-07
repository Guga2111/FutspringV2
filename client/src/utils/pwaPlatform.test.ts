import { describe, expect, it } from "vitest"
import { detectInstallContext, installCardVisible, installPlatform, type InstallEnvironment } from "@/utils/pwaPlatform"

const UA = {
  iphoneSafari:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
  ipadOs: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
  iphoneChrome:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.6778.73 Mobile/15E148 Safari/604.1",
  iphoneFirefox:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/132.0 Mobile/15E148 Safari/605.1.15",
  iphoneEdge:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 EdgiOS/131.0.2903.68 Mobile/15E148 Safari/605.1.15",
  iphoneInstagram:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 357.0.0.25.101 (iPhone15,2; iOS 18_1; pt_BR)",
  iphoneFacebook:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/489.0.0.40.108;FBBV/650000000]",
  androidWhatsApp:
    "Mozilla/5.0 (Linux; Android 14; SM-S918B Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.81 Mobile Safari/537.36",
  androidChrome: "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
  samsungInternet:
    "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36",
  macSafari: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
  windowsChrome: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
}

function env(userAgent: string, overrides: Partial<InstallEnvironment> = {}): InstallEnvironment {
  return { userAgent, maxTouchPoints: 0, standalone: false, hasInstallPrompt: false, ...overrides }
}

describe("detectInstallContext", () => {
  it("is installed when the app runs standalone, whatever the browser", () => {
    expect(detectInstallContext(env(UA.iphoneSafari, { maxTouchPoints: 5, standalone: true }))).toBe("installed")
    expect(detectInstallContext(env(UA.androidChrome, { standalone: true, hasInstallPrompt: true }))).toBe("installed")
  })

  it("detects Safari on iPhone and iPadOS (a Mac with a touch screen)", () => {
    expect(detectInstallContext(env(UA.iphoneSafari, { maxTouchPoints: 5 }))).toBe("ios-safari")
    expect(detectInstallContext(env(UA.ipadOs, { maxTouchPoints: 5 }))).toBe("ios-safari")
  })

  it("treats a Mac without touch as a desktop", () => {
    expect(detectInstallContext(env(UA.macSafari))).toBe("desktop")
  })

  it("detects the other iOS browsers", () => {
    for (const ua of [UA.iphoneChrome, UA.iphoneFirefox, UA.iphoneEdge]) {
      expect(detectInstallContext(env(ua, { maxTouchPoints: 5 }))).toBe("ios-other")
    }
  })

  it("detects in-app browsers before the platform", () => {
    for (const ua of [UA.iphoneInstagram, UA.iphoneFacebook, UA.androidWhatsApp]) {
      expect(detectInstallContext(env(ua, { maxTouchPoints: 5 }))).toBe("in-app")
    }
  })

  it("offers the one-tap install when Chrome sent the prompt, else the manual steps on Android", () => {
    expect(detectInstallContext(env(UA.androidChrome, { hasInstallPrompt: true }))).toBe("android-prompt")
    expect(detectInstallContext(env(UA.androidChrome))).toBe("android-manual")
    expect(detectInstallContext(env(UA.samsungInternet))).toBe("android-manual")
  })

  it("is a desktop otherwise, even with Chrome's install prompt on Windows", () => {
    expect(detectInstallContext(env(UA.windowsChrome))).toBe("desktop")
    expect(detectInstallContext(env(UA.windowsChrome, { hasInstallPrompt: true }))).toBe("desktop")
  })
})

describe("installPlatform", () => {
  it("maps each context to the steps it shows", () => {
    expect(installPlatform("ios-safari", UA.iphoneSafari)).toBe("ios")
    expect(installPlatform("ios-other", UA.iphoneChrome)).toBe("ios")
    expect(installPlatform("android-manual", UA.androidChrome)).toBe("android")
    expect(installPlatform("in-app", UA.androidWhatsApp)).toBe("android")
    expect(installPlatform("in-app", UA.iphoneInstagram)).toBe("ios")
    expect(installPlatform("desktop", UA.windowsChrome)).toBeNull()
    expect(installPlatform("installed", UA.iphoneSafari)).toBeNull()
  })
})

describe("installCardVisible", () => {
  const now = new Date("2026-11-06T12:00:00Z")

  it("shows the card when it was never dismissed or the value is unreadable", () => {
    expect(installCardVisible(null, now)).toBe(true)
    expect(installCardVisible("ontem", now)).toBe(true)
  })

  it("hides it for 30 days after the ✕", () => {
    expect(installCardVisible("2026-10-08T12:00:01Z", now)).toBe(false)
    expect(installCardVisible("2026-10-07T12:00:00Z", now)).toBe(true)
  })
})
