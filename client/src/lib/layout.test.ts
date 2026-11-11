import { readdirSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"

// The folder rule of FRONTEND.md ("Where new code goes"): a feature folder holds at most 12 files directly
const MAX_FILES = 12
const COMPONENTS = join(__dirname, "..", "components")

describe("components layout", () => {
  it("keeps every feature folder at 12 files or fewer (hooks/ and ui/ aside)", () => {
    const tooBig = readdirSync(COMPONENTS, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && entry.name !== "ui")
      .flatMap((feature) => {
        const dir = join(COMPONENTS, feature.name)
        const walk = (path: string): string[] => [
          path,
          ...readdirSync(path, { withFileTypes: true })
            .filter((entry) => entry.isDirectory() && entry.name !== "hooks")
            .flatMap((entry) => walk(join(path, entry.name))),
        ]
        return walk(dir)
      })
      .map((dir) => ({
        dir,
        files: readdirSync(dir, { withFileTypes: true }).filter((entry) => entry.isFile()).length,
      }))
      .filter(({ files }) => files > MAX_FILES)
      .map(({ dir, files }) => `${dir.slice(COMPONENTS.length + 1)} (${files})`)

    expect(tooBig).toEqual([])
  })
})
