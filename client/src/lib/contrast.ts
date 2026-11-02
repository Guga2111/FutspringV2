// WCAG 2.x contrast of the design tokens in index.css (bare HSL triplets, see the spec in docs/plans)

export type Rgb = [number, number, number]
export type ThemeTokens = { light: Record<string, string>; dark: Record<string, string> }

/** "197 94% 43.1%" → [r, g, b] in 0–255 */
export function hslTripletToRgb(triplet: string): Rgb {
  const match = triplet.trim().match(/^(-?[\d.]+)\s+([\d.]+)%\s+([\d.]+)%$/)
  if (!match) throw new Error(`Not an HSL triplet: "${triplet}"`)
  const h = (((Number(match[1]) % 360) + 360) % 360) / 360
  const s = Number(match[2]) / 100
  const l = Number(match[3]) / 100
  if (s === 0) return [l * 255, l * 255, l * 255]
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  const channel = (t: number) => {
    const x = t < 0 ? t + 1 : t > 1 ? t - 1 : t
    if (x < 1 / 6) return p + (q - p) * 6 * x
    if (x < 1 / 2) return q
    if (x < 2 / 3) return p + (q - p) * (2 / 3 - x) * 6
    return p
  }
  return [channel(h + 1 / 3) * 255, channel(h) * 255, channel(h - 1 / 3) * 255]
}

export function relativeLuminance([r, g, b]: Rgb): number {
  const linear = (c: number) => {
    const v = c / 255
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

export function contrastRatio(a: Rgb, b: Rgb): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

function declarations(block: string): Record<string, string> {
  const tokens: Record<string, string> = {}
  for (const [, name, value] of block.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/--([\w-]+)\s*:\s*([^;]+);/g)) {
    tokens[name] = value.trim()
  }
  return tokens
}

function block(css: string, selector: string): string {
  const start = css.indexOf(`${selector} {`)
  if (start < 0) throw new Error(`No "${selector}" block`)
  return css.slice(start, css.indexOf("}", start))
}

/** Reads the first `:root` and `.dark` blocks; dark inherits what it doesn't redefine. */
export function parseThemeTokens(css: string): ThemeTokens {
  const light = declarations(block(css, ":root"))
  return { light, dark: { ...light, ...declarations(block(css, ".dark")) } }
}

/** A token's HSL triplet, following var(--x) references. */
export function resolveToken(tokens: Record<string, string>, name: string, seen: string[] = []): string {
  const value = tokens[name]
  if (value === undefined) throw new Error(`Unknown token --${name}`)
  const ref = value.match(/^var\(--([\w-]+)\)$/)
  if (!ref) return value
  if (seen.includes(name)) throw new Error(`Circular token --${name}`)
  return resolveToken(tokens, ref[1], [...seen, name])
}

export function tokenContrast(tokens: Record<string, string>, foreground: string, background: string): number {
  return contrastRatio(hslTripletToRgb(resolveToken(tokens, foreground)), hslTripletToRgb(resolveToken(tokens, background)))
}
