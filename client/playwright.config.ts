import { defineConfig, devices } from "@playwright/test"

// Visual regression of the built app with the API mocked (e2e/mockApi.ts).
// Snapshots are only comparable when rendered by the same browser build and fonts, so they are
// generated and checked inside the official Playwright image (bun run test:visual:docker).
const PORT = 4318

const viewports = {
  mobile: { width: 375, height: 812 },
  desktop: { width: 1280, height: 800 },
}

export default defineConfig({
  testDir: "e2e",
  snapshotPathTemplate: "e2e/__screenshots__/{projectName}/{arg}{ext}",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  expect: {
    toHaveScreenshot: { animations: "disabled", caret: "hide", maxDiffPixelRatio: 0.01 },
  },
  use: {
    baseURL: `http://localhost:${PORT}`,
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    reducedMotion: "reduce",
  },
  // the theme comes from the OS preference (nothing saved in localStorage.theme)
  projects: (["light", "dark"] as const).flatMap((colorScheme) =>
    (Object.keys(viewports) as (keyof typeof viewports)[]).map((size) => ({
      name: `${size}-${colorScheme}`,
      use: { ...devices["Desktop Chrome"], viewport: viewports[size], colorScheme },
    })),
  ),
  webServer: {
    command: `bun run build && bunx vite preview --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
})
